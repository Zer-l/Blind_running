package com.guiderun.app.ui.volunteer

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.guiderun.app.R
import com.guiderun.app.data.remote.WebSocketManager
import com.guiderun.app.domain.model.RunRequest
import com.guiderun.app.domain.model.RunRequestStatus
import com.guiderun.app.domain.repository.LocationProvider
import com.guiderun.app.domain.repository.RunRequestRepository
import com.guiderun.app.service.LocationUpdateService
import com.guiderun.app.ui.shared.map.CameraTarget
import com.guiderun.app.ui.shared.map.GuideRunMapState
import com.guiderun.app.ui.shared.map.PolylineConfig
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

/** 前往集合点页 UI 状态。 */
data class VolunteerNavigatingUiState(
    val request: RunRequest? = null,
    val isLoading: Boolean = false,
    val mapState: GuideRunMapState = GuideRunMapState(),
    val isConfirmingMet: Boolean = false,
    val showCancelledDialog: Boolean = false,
    val errorMessage: String? = null,
)

/** 前往集合点页导航事件。 */
sealed interface VolunteerNavigatingNavEvent {
    data class ToMet(val requestId: String) : VolunteerNavigatingNavEvent
    data object ToHome : VolunteerNavigatingNavEvent
}

/**
 * 志愿者前往集合点页 ViewModel（AndroidViewModel，持有 Application 以启停 LocationUpdateService）。
 *
 * 状态机联动：
 * - loadRequest 检测 ACCEPTED → 自动调 depart()（状态 ACCEPTED→EN_ROUTE），避免志愿者手动按"出发"
 * - 本机 GPS 每 5s 上报一次位置 + 渲染地图上的蓝色直线方向指引（非真实路线）
 * - WS ABORTED：selfAborting=true 时本人取消直接回首页，false 时弹"订单已被取消"通知弹窗
 *
 * 取消路径区分：
 * - ACCEPTED → abandon（订单回 MATCHING，不直接 ABORTED）
 * - EN_ROUTE → cancel（订单 ABORTED）
 */
@HiltViewModel
class VolunteerNavigatingViewModel @Inject constructor(
    application: Application,
    savedStateHandle: SavedStateHandle,
    private val runRequestRepository: RunRequestRepository,
    private val locationProvider: LocationProvider,
    private val wsManager: WebSocketManager,
) : AndroidViewModel(application) {

    private val requestId: String = checkNotNull(savedStateHandle["requestId"])

    private val _uiState = MutableStateFlow(VolunteerNavigatingUiState())
    val uiState = _uiState.asStateFlow()

    private val _navEvent = MutableSharedFlow<VolunteerNavigatingNavEvent>(replay = 0)
    val navEvent: SharedFlow<VolunteerNavigatingNavEvent> = _navEvent.asSharedFlow()

    /**
     * 志愿者本人发起取消的标志。
     * 志愿者自己 cancel/abandon 也会收到服务端广播的 ABORTED WS，若不区分会误弹"订单已取消"通知。
     * 该弹窗仅用于"对方（视障端）/系统取消"时通知志愿者，本人取消应直接导航回首页。
     */
    private var selfAborting = false

    init {
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
                            // 仅在首次加载时下发新的 CameraTarget，让地图定位到集合点；
                            // 后续 WS 位置推送只更新 volunteerLatLng，保持引用不变，避免相机被反复拉回。
                            mapState = it.mapState.copy(
                                cameraTarget = CameraTarget(
                                    lat = req.meetingLocation.lat,
                                    lng = req.meetingLocation.lng,
                                ),
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
                    _uiState.update { it.copy(errorMessage = getApplication<Application>().getString(R.string.navigating_error_depart_failed)) }
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
                    RunRequestStatus.MET.name -> _navEvent.emit(VolunteerNavigatingNavEvent.ToMet(requestId))
                    RunRequestStatus.ABORTED.name -> {
                        stopLocationService()
                        if (selfAborting) {
                            // 志愿者本人取消：interruptByUser 已负责导航回首页，不弹通知弹窗
                            _navEvent.emit(VolunteerNavigatingNavEvent.ToHome)
                        } else {
                            _uiState.update { it.copy(showCancelledDialog = true) }
                        }
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
                    _uiState.update { state ->
                        val volunteer = geoPoint.lat to geoPoint.lng
                        val meeting = state.mapState.blindLatLng
                        // 有集合点时连一条直线指引方向；不是真实步行路线，仅做视觉方向感
                        val polylines = if (meeting != null) {
                            listOf(
                                PolylineConfig(
                                    points = listOf(volunteer, meeting),
                                    colorHex = ROUTE_HINT_COLOR,
                                    width = 12f,
                                )
                            )
                        } else {
                            emptyList()
                        }
                        state.copy(
                            mapState = state.mapState.copy(
                                volunteerLatLng = volunteer,
                                polylines = polylines,
                            )
                        )
                    }
                }
            }.onFailure {
                // 协程取消需重新抛出（结构化并发）；其余定位异常记日志而非静默吞没
                if (it is kotlinx.coroutines.CancellationException) throw it
                Timber.w(it, "NavigatingVM: volunteer position updates failed")
            }
        }
    }

    private companion object {
        // Material Blue 600，避免与跑步轨迹的速度色（红/绿）冲突
        const val ROUTE_HINT_COLOR = "#1E88E5"
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
                    _navEvent.emit(VolunteerNavigatingNavEvent.ToMet(requestId))
                }
                .onFailure { e ->
                    Timber.e(e, "NavigatingVM: confirmMet failed")
                    _uiState.update {
                        it.copy(
                            isConfirmingMet = false,
                            errorMessage = getApplication<Application>().getString(R.string.navigating_error_operation_failed),
                        )
                    }
                }
        }
    }

    fun onCancelledDialogDismiss() {
        _uiState.update { it.copy(showCancelledDialog = false) }
        viewModelScope.launch { _navEvent.emit(VolunteerNavigatingNavEvent.ToHome) }
    }

    fun onErrorShown() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * 返回键触发的中断：服务端状态机限制不同。
     * - ACCEPTED：志愿者只能 abandon（放弃接单，订单回 MATCHING 或第3次直接 ABORTED）
     * - EN_ROUTE：志愿者可以 cancel（取消订单 → ABORTED）
     * - 其他状态：不应到达此分支（UI 层应已拦截）
     */
    fun interruptByUser() {
        val status = _uiState.value.request?.status ?: return
        // 先置标志：服务端转 ABORTED 后会广播 WS 回本端，避免误弹"订单已取消"通知
        selfAborting = true
        viewModelScope.launch {
            val result = when (status) {
                RunRequestStatus.ACCEPTED -> runRequestRepository.abandon(requestId)
                RunRequestStatus.EN_ROUTE -> runRequestRepository.cancel(requestId, reason = "志愿者主动取消")
                else                      -> {
                    selfAborting = false
                    return@launch
                }
            }
            result
                .onSuccess {
                    stopLocationService()
                    _navEvent.emit(VolunteerNavigatingNavEvent.ToHome)
                }
                .onFailure { e ->
                    // 取消失败：复位标志，否则后续真·对方取消会被误判为本人取消而不弹通知
                    selfAborting = false
                    Timber.e(e, "NavigatingVM: interruptByUser failed")
                    _uiState.update { it.copy(errorMessage = getApplication<Application>().getString(R.string.navigating_error_cancel_failed)) }
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopLocationService()
    }
}
