package com.guiderun.app.ui.volunteer

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.guiderun.app.data.local.UserPreferences
import com.guiderun.app.data.local.dao.RunSessionStatsDao
import com.guiderun.app.data.local.entity.RunSessionStatsEntity
import com.guiderun.app.data.remote.WebSocketManager
import com.guiderun.app.domain.model.RunRequest
import com.guiderun.app.domain.model.RunRequestStatus
import com.guiderun.app.domain.repository.LocationProvider
import com.guiderun.app.domain.repository.RunRequestRepository
import com.guiderun.app.service.RunTrackingService
import com.guiderun.app.service.VolunteerRunTrackingService
import com.guiderun.app.util.Ema
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
    /** 原始瞬时配速（来自 PaceWindow，用于上传/统计）。 */
    val currentPaceSeconds: Int? = null,
    /** 显示用瞬时配速：基于 currentPaceSeconds 做 EMA 平滑，仅用于 UI。 */
    val displayPaceSeconds: Int? = null,
    val avgPaceSeconds: Int? = null,
    /** 本机采集端是否处于自动暂停（距离/配速冻结时为 true）。 */
    val isPaused: Boolean = false,
    /** 已发送结束申请，等待视障端确认中。 */
    val endRequestPending: Boolean = false,
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
) : AndroidViewModel(application) {

    private val requestId: String = checkNotNull(savedStateHandle["requestId"])

    private val _uiState = MutableStateFlow(VolunteerRunningUiState())
    val uiState: StateFlow<VolunteerRunningUiState> = _uiState.asStateFlow()

    private val _navEvent = MutableSharedFlow<VolunteerRunningNavEvent>(replay = 0)
    val navEvent: SharedFlow<VolunteerRunningNavEvent> = _navEvent.asSharedFlow()

    private var userId: String = ""
    private val paceEma = Ema(alpha = 0.3)

    init {
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
                    val display = smoothPaceForDisplay(stats.currentPaceSeconds, stats.isPaused)
                    _uiState.update {
                        it.copy(
                            totalDistanceMeters = stats.totalDistanceMeters,
                            totalDurationSeconds = stats.totalDurationSeconds,
                            currentPaceSeconds = stats.currentPaceSeconds,
                            displayPaceSeconds = display,
                            avgPaceSeconds = stats.avgPaceSeconds,
                            isPaused = stats.isPaused,
                        )
                    }
                }
            }
        }
    }

    /**
     * UI 配速做指数移动平均，避免瞬时跳变。
     * 暂停时保留上一次有效配速（由 UI 用 isPaused 切换淡色样式），不再返回 null。
     */
    private fun smoothPaceForDisplay(rawPace: Int?, paused: Boolean): Int? {
        if (paused) {
            return _uiState.value.displayPaceSeconds
        }
        if (rawPace == null) {
            paceEma.reset()
            return _uiState.value.displayPaceSeconds
        }
        return paceEma.update(rawPace.toDouble()).toInt()
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

    /**
     * 申请结束跑步：服务端不立即改状态，仅推送视障端等待其确认。
     * 视障端确认后 → 服务端 RUNNING→FINISHED → WS 推送 → 双方进评价页。
     * UI 上仅依靠 endRequestPending 触发横幅+按钮 disabled，不再额外弹 snackbar。
     */
    fun requestEndRun() {
        if (_uiState.value.endRequestPending) return
        viewModelScope.launch {
            _uiState.update { it.copy(endRequestPending = true) }
            runRequestRepository.requestEndRun(requestId)
                .onFailure { e ->
                    _uiState.update {
                        it.copy(endRequestPending = false, errorMessage = e.message ?: "申请结束失败")
                    }
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
