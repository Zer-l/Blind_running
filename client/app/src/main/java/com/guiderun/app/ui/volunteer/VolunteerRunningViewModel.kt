package com.guiderun.app.ui.volunteer

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.guiderun.app.R
import com.guiderun.app.data.local.UserPreferences
import com.guiderun.app.data.local.dao.RunSessionStatsDao
import com.guiderun.app.data.local.dao.RunTrackBufferDao
import com.guiderun.app.data.remote.WebSocketManager
import com.guiderun.app.domain.model.RunRequest
import com.guiderun.app.domain.model.RunRequestStatus
import com.guiderun.app.domain.repository.LocationProvider
import com.guiderun.app.domain.repository.RunRequestRepository
import com.guiderun.app.service.RunTrackingService
import com.guiderun.app.service.VolunteerRunTrackingService
import dagger.hilt.android.lifecycle.HiltViewModel
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

/** 志愿者跑步中页 UI 状态。 */
data class VolunteerRunningUiState(
    val request: RunRequest? = null,
    val isLoading: Boolean = true,
    val totalDistanceMeters: Int = 0,
    val totalDurationSeconds: Int = 0,
    /** 原始瞬时配速（来自 service SpeedSmoother，用于统计）。 */
    val currentPaceSeconds: Int? = null,
    /** 显示用瞬时配速：基于 currentPaceSeconds 做 EMA 平滑，仅用于 UI。 */
    val displayPaceSeconds: Int? = null,
    /** 本机采集端是否处于自动暂停（距离/配速冻结时为 true）。 */
    val isPaused: Boolean = false,
    /** 已发送结束申请，等待视障端确认中。 */
    val endRequestPending: Boolean = false,
    val showCancelledDialog: Boolean = false,
    val errorMessage: String? = null,
    /** 轨迹点列表（WGS-84 坐标） */
    val trackPoints: List<Pair<Double, Double>> = emptyList(),
    /** 初始相机位置（当前定位） */
    val initialLocation: Pair<Double, Double>? = null,
)

/** 跑步中页导航事件。 */
sealed interface VolunteerRunningNavEvent {
    data class ToReview(val requestId: String) : VolunteerRunningNavEvent
    data object ToHome : VolunteerRunningNavEvent
}

/**
 * 志愿者端跑步中页 ViewModel（AndroidViewModel，持有 Application 以启停前台 Service）。
 *
 * 数据流与视障端对称，但各自独立：
 * - 跑步指标由 [VolunteerRunTrackingService] 写入 Room，ViewModel 订阅 observe Flow 驱动 UI
 * - 轨迹折线从 [RunTrackBufferDao] 实时订阅（每新写入一个点就更新地图折线）
 * - WS FINISHED → 停止 Service → ToReview；WS ABORTED → showCancelledDialog
 *
 * 结束流程（协商式）：
 * 1. 志愿者调 requestEndRun → 服务端推送 WS 给视障端（不改状态）
 * 2. 视障端长按确认 → 服务端 RUNNING→FINISHED → WS 推送双方 → 各自 ToReview
 */
@HiltViewModel
class VolunteerRunningViewModel @Inject constructor(
    application: Application,
    savedStateHandle: SavedStateHandle,
    private val runRequestRepository: RunRequestRepository,
    private val sessionStatsDao: RunSessionStatsDao,
    private val trackBufferDao: RunTrackBufferDao,
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

    init {
        viewModelScope.launch {
            userId = userPreferences.getCurrentUserId() ?: ""
            loadRequest()
            fetchInitialLocation()
            try {
                startTrackingService()
            } catch (e: Exception) {
                Timber.e(e, "Failed to start tracking service")
            }
            observeSessionStats()
            observeTrackPoints()
            observeWs()
            // 双端解耦：不再向对端广播自己的 metrics，视障端从本地 service 独立获取数据
        }
    }

    /** 获取当前定位作为地图初始相机位置 */
    private suspend fun fetchInitialLocation() {
        try {
            val location = locationProvider.getLastLocation()
            if (location != null) {
                _uiState.update {
                    it.copy(initialLocation = location.lat to location.lng)
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to fetch initial location")
        }
    }

    private suspend fun loadRequest() {
        _uiState.update { it.copy(isLoading = true) }
        runRequestRepository.getRunRequest(requestId)
            .onSuccess { req ->
                _uiState.update { it.copy(request = req, isLoading = false) }
            }
            .onFailure { e ->
                // 不向 UI 透传原始异常，统一用友好文案；细节记日志
                Timber.e(e, "VolunteerRunningVM: loadRequest failed")
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = getApplication<Application>().getString(R.string.volunteer_load_failed))
                }
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
                        // 配速平滑已在 service SpeedSmoother（5s 滑动平均）完成，UI 不再叠 EMA。
                        // 暂停 / 无瞬时配速时保留上一次有效值（UI 用 isPaused 切淡色），避免数字闪没。
                        val display = if (stats.isPaused || stats.currentPaceSeconds == null) {
                            it.displayPaceSeconds
                        } else {
                            stats.currentPaceSeconds
                        }
                        it.copy(
                            totalDistanceMeters = stats.totalDistanceMeters,
                            totalDurationSeconds = stats.totalDurationSeconds,
                            currentPaceSeconds = stats.currentPaceSeconds,
                            displayPaceSeconds = display,
                            isPaused = stats.isPaused,
                        )
                    }
                }
            }
        }
    }

    private fun observeTrackPoints() {
        viewModelScope.launch {
            trackBufferDao.observeByRequest(requestId).collect { points ->
                _uiState.update {
                    it.copy(trackPoints = points.map { p -> p.lat to p.lng })
                }
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
                        _uiState.update { it.copy(showCancelledDialog = true) }
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
                    Timber.e(e, "VolunteerRunningVM: requestEndRun failed")
                    _uiState.update {
                        it.copy(endRequestPending = false, errorMessage = getApplication<Application>().getString(R.string.volunteer_end_request_failed))
                    }
                }
        }
    }

    fun onCancelledDialogDismiss() {
        _uiState.update { it.copy(showCancelledDialog = false) }
        viewModelScope.launch { _navEvent.emit(VolunteerRunningNavEvent.ToHome) }
    }

    fun onErrorShown() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // 故意不在 onCleared 内 stopTrackingService。
    // 返回首页 / 跳到其他页时 ViewModel.onCleared 会触发，但跑步应在后台继续，
    // 终态停止由显式分支负责：FINISHED WS / ABORTED WS 都已调 stopTrackingService。
}
