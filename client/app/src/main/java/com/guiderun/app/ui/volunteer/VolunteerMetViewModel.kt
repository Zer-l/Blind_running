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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 已汇合页 UI 状态。 */
data class VolunteerMetUiState(
    val request: RunRequest? = null,
    val isLoading: Boolean = false,
    val mapState: GuideRunMapState = GuideRunMapState(),
    val statusMessage: String = "",
    val showCancelledDialog: Boolean = false,
)

/** 已汇合页导航事件：回首页 或 进跑步页。 */
sealed interface VolunteerMetNavEvent {
    data object ToHome : VolunteerMetNavEvent
    data class ToRunning(val requestId: String) : VolunteerMetNavEvent
}

/**
 * 已汇合页 ViewModel（AndroidViewModel，持有 Application 以启停 LocationUpdateService）。
 *
 * 数据流：
 * - loadRequest 加载订单详情，首次下发 CameraTarget 聚焦集合点；
 * - observeVolunteerPosition 每 15s 刷新志愿者实时位置（更新地图蓝点）；
 * - observeWs 监听 WS：RUNNING → 跳跑步页；ABORTED → 弹通知弹窗后回首页。
 * 页面离开（onCleared）时停止前台位置上报服务。
 */
@HiltViewModel
class VolunteerMetViewModel @Inject constructor(
    application: Application,
    savedStateHandle: SavedStateHandle,
    private val runRequestRepository: RunRequestRepository,
    private val locationProvider: LocationProvider,
    private val wsManager: WebSocketManager,
) : AndroidViewModel(application) {

    private val requestId: String = checkNotNull(savedStateHandle["requestId"])

    private val _uiState = MutableStateFlow(VolunteerMetUiState())
    val uiState = _uiState.asStateFlow()

    private val _navEvent = MutableSharedFlow<VolunteerMetNavEvent>(replay = 0)
    val navEvent: SharedFlow<VolunteerMetNavEvent> = _navEvent.asSharedFlow()

    init {
        loadRequest()
        startLocationService(intervalSeconds = 15)
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
                            statusMessage = "等待视障用户确认汇合中…",
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
                }
                .onFailure { _uiState.update { it.copy(isLoading = false) } }
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
                    RunRequestStatus.RUNNING.name -> _navEvent.emit(VolunteerMetNavEvent.ToRunning(requestId))
                    RunRequestStatus.ABORTED.name -> {
                        stopLocationService()
                        _uiState.update { it.copy(showCancelledDialog = true) }
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
                locationProvider.locationUpdates(15_000L).collect { geoPoint ->
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

    fun onCancelledDialogDismiss() {
        _uiState.update { it.copy(showCancelledDialog = false) }
        viewModelScope.launch { _navEvent.emit(VolunteerMetNavEvent.ToHome) }
    }

    override fun onCleared() {
        super.onCleared()
        stopLocationService()
    }
}
