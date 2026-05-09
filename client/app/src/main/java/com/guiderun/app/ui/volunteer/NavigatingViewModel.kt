package com.guiderun.app.ui.volunteer

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.guiderun.app.accessibility.TtsManager
import com.guiderun.app.data.remote.WebSocketManager
import com.guiderun.app.domain.model.RunRequest
import com.guiderun.app.domain.model.RunRequestStatus
import com.guiderun.app.domain.repository.LocationProvider
import com.guiderun.app.domain.repository.RunRequestRepository
import com.guiderun.app.service.LocationUpdateService
import com.guiderun.app.ui.shared.map.GuideRunMapState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class NavigatingUiState(
    val request: RunRequest? = null,
    val isLoading: Boolean = false,
    val mapState: GuideRunMapState = GuideRunMapState(),
    val isConfirmingMet: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface NavigatingNavEvent {
    data class ToMet(val requestId: String) : NavigatingNavEvent
    data object ToHome : NavigatingNavEvent
}

@HiltViewModel
class NavigatingViewModel @Inject constructor(
    application: Application,
    savedStateHandle: SavedStateHandle,
    private val ttsManager: TtsManager,
    private val runRequestRepository: RunRequestRepository,
    private val locationProvider: LocationProvider,
    private val wsManager: WebSocketManager,
) : AndroidViewModel(application) {

    private val requestId: String = checkNotNull(savedStateHandle["requestId"])

    private val _uiState = MutableStateFlow(NavigatingUiState())
    val uiState = _uiState.asStateFlow()

    private val _navEvent = MutableSharedFlow<NavigatingNavEvent>(replay = 0)
    val navEvent: SharedFlow<NavigatingNavEvent> = _navEvent.asSharedFlow()

    init {
        ttsManager.acquire()
        loadRequest()
        observeWs()
        observeVolunteerPosition()
    }

    private fun loadRequest() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            runRequestRepository.getRunRequest(requestId)
                .onSuccess { req ->
                    _uiState.update {
                        it.copy(
                            request = req,
                            isLoading = false,
                            mapState = it.mapState.copy(
                                centerLat = req.meetingLocation.lat,
                                centerLng = req.meetingLocation.lng,
                                blindLatLng = req.meetingLocation.lat to req.meetingLocation.lng,
                            ),
                        )
                    }
                    // Auto-depart if still in ACCEPTED state, otherwise start service directly
                    if (req.status == RunRequestStatus.ACCEPTED) {
                        doDepart()
                    } else {
                        startLocationService(intervalSeconds = 5)
                    }
                }
                .onFailure { e ->
                    Timber.e(e, "NavigatingVM: loadRequest failed")
                    _uiState.update { it.copy(isLoading = false) }
                }
        }
    }

    private fun doDepart() {
        viewModelScope.launch {
            runRequestRepository.depart(requestId)
                .onSuccess { req ->
                    _uiState.update { it.copy(request = req) }
                    startLocationService(intervalSeconds = 5)
                }
                .onFailure { e ->
                    Timber.e(e, "NavigatingVM: depart failed")
                    _uiState.update { it.copy(errorMessage = "出发失败：${e.message}") }
                }
        }
    }

    private fun startLocationService(intervalSeconds: Int) {
        val intent = Intent(getApplication(), LocationUpdateService::class.java).apply {
            putExtra(LocationUpdateService.EXTRA_REQUEST_ID, requestId)
            putExtra(LocationUpdateService.EXTRA_INTERVAL_SECONDS, intervalSeconds)
        }
        getApplication<Application>().startForegroundService(intent)
    }

    private fun stopLocationService() {
        getApplication<Application>().stopService(
            Intent(getApplication(), LocationUpdateService::class.java)
        )
    }

    private fun observeWs() {
        viewModelScope.launch {
            wsManager.messages.collect { msg ->
                if (msg.requestId != requestId) return@collect
                when (msg.toStatus) {
                    RunRequestStatus.MET.name -> _navEvent.emit(NavigatingNavEvent.ToMet(requestId))
                    RunRequestStatus.ABORTED.name -> {
                        stopLocationService()
                        val ttsText = if (msg.triggeredRole == "BLIND") "视障用户已取消请求，返回主页"
                        else "已放弃接单，返回主页"
                        ttsManager.speakAndWait(ttsText, TtsManager.Priority.HIGH)
                        _navEvent.emit(NavigatingNavEvent.ToHome)
                    }
                    else -> {
                        val currentStatus = _uiState.value.request?.status?.name
                        if (msg.toStatus != currentStatus) syncRequest()
                    }
                }
            }
        }
        viewModelScope.launch {
            wsManager.reconnected.collect { syncRequest() }
        }
    }

    private fun observeVolunteerPosition() {
        viewModelScope.launch {
            runCatching {
                locationProvider.locationUpdates(5_000L).collect { geoPoint ->
                    _uiState.update {
                        it.copy(mapState = it.mapState.copy(volunteerLatLng = geoPoint.lat to geoPoint.lng))
                    }
                }
            }
        }
    }

    private fun syncRequest() {
        viewModelScope.launch {
            runRequestRepository.getRunRequest(requestId)
                .onSuccess { req -> _uiState.update { it.copy(request = req) } }
        }
    }

    fun onArrivedClick() {
        viewModelScope.launch {
            _uiState.update { it.copy(isConfirmingMet = true) }
            runRequestRepository.confirmMet(requestId)
                .onSuccess {
                    _navEvent.emit(NavigatingNavEvent.ToMet(requestId))
                }
                .onFailure { e ->
                    Timber.e(e, "NavigatingVM: confirmMet failed")
                    _uiState.update {
                        it.copy(isConfirmingMet = false, errorMessage = "操作失败：${e.message}")
                    }
                }
        }
    }

    fun onErrorShown() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        stopLocationService()
        ttsManager.release()
    }
}
