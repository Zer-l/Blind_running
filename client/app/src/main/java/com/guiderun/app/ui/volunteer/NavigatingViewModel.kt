package com.guiderun.app.ui.volunteer

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
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

    /**
     * 返回键触发的中断：服务端状态机限制不同。
     * - ACCEPTED：志愿者只能 abandon（放弃接单，订单回 MATCHING 或第3次直接 ABORTED）
     * - EN_ROUTE：志愿者可以 cancel（取消订单 → ABORTED）
     * - 其他状态：不应到达此分支（UI 层应已拦截）
     */
    fun interruptByUser() {
        val status = _uiState.value.request?.status ?: return
        viewModelScope.launch {
            val result = when (status) {
                RunRequestStatus.ACCEPTED -> runRequestRepository.abandon(requestId)
                RunRequestStatus.EN_ROUTE -> runRequestRepository.cancel(requestId, reason = "志愿者主动取消")
                else                      -> return@launch
            }
            result
                .onSuccess {
                    stopLocationService()
                    _navEvent.emit(NavigatingNavEvent.ToHome)
                }
                .onFailure { e ->
                    Timber.e(e, "NavigatingVM: interruptByUser failed")
                    _uiState.update { it.copy(errorMessage = "取消失败：${e.message ?: "请重试"}") }
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopLocationService()
    }
}
