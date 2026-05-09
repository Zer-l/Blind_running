package com.guiderun.app.ui.volunteer

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.guiderun.app.BuildConfig
import com.guiderun.app.data.local.UserPreferences
import com.guiderun.app.data.local.dao.RunSessionStatsDao
import com.guiderun.app.data.local.entity.RunSessionStatsEntity
import com.guiderun.app.data.remote.WebSocketManager
import com.guiderun.app.domain.model.RunRequest
import com.guiderun.app.domain.model.RunRequestStatus
import com.guiderun.app.domain.repository.LocationProvider
import com.guiderun.app.domain.repository.RunRequestRepository
import com.guiderun.app.service.RunTrackingService
import com.guiderun.app.service.VoiceCallManager
import com.guiderun.app.service.VolunteerRunTrackingService
import com.guiderun.app.util.PaceCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class VolunteerRunningUiState(
    val request: RunRequest? = null,
    val isLoading: Boolean = true,
    val totalDistanceMeters: Int = 0,
    val totalDurationSeconds: Int = 0,
    val currentPaceSeconds: Int? = null,
    val avgPaceSeconds: Int? = null,
    val callEnabled: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface VolunteerRunningNavEvent {
    data class ToReview(val requestId: String) : VolunteerRunningNavEvent
    data object ToHome : VolunteerRunningNavEvent
}

@HiltViewModel
class VolunteerRunningViewModel @Inject constructor(
    application: Application,
    savedStateHandle: SavedStateHandle,
    private val runRequestRepository: RunRequestRepository,
    private val sessionStatsDao: RunSessionStatsDao,
    private val userPreferences: UserPreferences,
    private val wsManager: WebSocketManager,
    private val locationProvider: LocationProvider,
    private val voiceCallManager: VoiceCallManager,
) : AndroidViewModel(application) {

    private val requestId: String = checkNotNull(savedStateHandle["requestId"])

    private val _uiState = MutableStateFlow(VolunteerRunningUiState())
    val uiState: StateFlow<VolunteerRunningUiState> = _uiState.asStateFlow()

    private val _navEvent = MutableSharedFlow<VolunteerRunningNavEvent>(replay = 0)
    val navEvent: SharedFlow<VolunteerRunningNavEvent> = _navEvent.asSharedFlow()

    private var userId: String = ""

    init {
        _uiState.update { it.copy(callEnabled = BuildConfig.VOICE_CALL_ENABLED) }
        viewModelScope.launch {
            userId = userPreferences.getCurrentUserId() ?: ""
            loadRequest()
            try {
                startTrackingService()
            } catch (e: Exception) {
                Timber.e(e, "Failed to start tracking service")
            }
            observeSessionStats()
            observeWs()
            startDurationTicker()
            pushPeerMetricsPeriodically()
        }
    }

    private suspend fun loadRequest() {
        _uiState.update { it.copy(isLoading = true) }
        runRequestRepository.getRunRequest(requestId)
            .onSuccess { req ->
                _uiState.update { it.copy(request = req, isLoading = false) }
            }
            .onFailure { e ->
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
    }

    private fun startTrackingService() {
        val intent = Intent(getApplication(), VolunteerRunTrackingService::class.java).apply {
            putExtra(RunTrackingService.EXTRA_REQUEST_ID, requestId)
        }
        getApplication<Application>().startForegroundService(intent)
    }

    private fun stopTrackingService() {
        getApplication<Application>().stopService(
            Intent(getApplication(), VolunteerRunTrackingService::class.java)
        )
    }

    private fun observeSessionStats() {
        viewModelScope.launch {
            if (userId.isEmpty()) return@launch
            sessionStatsDao.observe(requestId, userId).collect { stats ->
                if (stats != null) {
                    _uiState.update {
                        it.copy(
                            totalDistanceMeters = stats.totalDistanceMeters,
                            totalDurationSeconds = stats.totalDurationSeconds,
                            currentPaceSeconds = stats.currentPaceSeconds,
                            avgPaceSeconds = stats.avgPaceSeconds,
                        )
                    }
                }
            }
        }
    }

    private fun startDurationTicker() {
        viewModelScope.launch {
            val req = _uiState.value.request ?: return@launch
            val startTimeSec = (req.runStartedAt ?: return@launch) / 1000
            while (true) {
                delay(1_000)
                val elapsed = ((System.currentTimeMillis() / 1000) - startTimeSec).toInt()
                _uiState.update { it.copy(totalDurationSeconds = elapsed) }
            }
        }
    }

    private fun pushPeerMetricsPeriodically() {
        viewModelScope.launch {
            while (true) {
                delay(5_000)
                val state = _uiState.value
                runRequestRepository.pushPeerMetrics(
                    requestId = requestId,
                    totalDistanceMeters = state.totalDistanceMeters,
                    totalDurationSeconds = state.totalDurationSeconds,
                    currentPaceSeconds = state.currentPaceSeconds,
                    avgPaceSeconds = state.avgPaceSeconds,
                )
            }
        }
    }

    private fun observeWs() {
        viewModelScope.launch {
            wsManager.messages.collect { msg ->
                if (msg.requestId != requestId) return@collect
                when (msg.toStatus) {
                    RunRequestStatus.FINISHED.name -> {
                        stopTrackingService()
                        _navEvent.emit(VolunteerRunningNavEvent.ToReview(requestId))
                    }
                    RunRequestStatus.ABORTED.name -> {
                        stopTrackingService()
                        _navEvent.emit(VolunteerRunningNavEvent.ToHome)
                    }
                    else -> {}
                }
            }
        }
        viewModelScope.launch {
            wsManager.reconnected.collect { loadRequest() }
        }
    }

    fun initiateCall() {
        if (!BuildConfig.VOICE_CALL_ENABLED) {
            _uiState.update { it.copy(errorMessage = "语音通话功能即将上线") }
            return
        }
        viewModelScope.launch {
            runRequestRepository.initiateVoiceCall(requestId)
                .onSuccess { callInfo ->
                    callInfo.otherPartyPhone?.let { phone ->
                        voiceCallManager.initiateCall(requestId, phone)
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(errorMessage = e.message ?: "发起通话失败") }
                }
        }
    }

    fun endRun() {
        viewModelScope.launch {
            val state = _uiState.value
            runRequestRepository.endRun(
                id = requestId,
                actualDistanceMeters = state.totalDistanceMeters,
                actualDurationSeconds = state.totalDurationSeconds,
                avgPaceSeconds = state.avgPaceSeconds,
            ).onSuccess {
                stopTrackingService()
                _navEvent.emit(VolunteerRunningNavEvent.ToReview(requestId))
            }.onFailure { e ->
                _uiState.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    fun onErrorShown() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        stopTrackingService()
    }
}
