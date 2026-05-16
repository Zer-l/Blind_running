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

data class MetUiState(
    val request: RunRequest? = null,
    val isLoading: Boolean = false,
    val mapState: GuideRunMapState = GuideRunMapState(),
    val statusMessage: String = "",
)

sealed interface MetNavEvent {
    data object ToHome : MetNavEvent
    data class ToRunning(val requestId: String) : MetNavEvent
}

@HiltViewModel
class MetViewModel @Inject constructor(
    application: Application,
    savedStateHandle: SavedStateHandle,
    private val ttsManager: TtsManager,
    private val runRequestRepository: RunRequestRepository,
    private val locationProvider: LocationProvider,
    private val wsManager: WebSocketManager,
) : AndroidViewModel(application) {

    private val requestId: String = checkNotNull(savedStateHandle["requestId"])

    private val _uiState = MutableStateFlow(MetUiState())
    val uiState = _uiState.asStateFlow()

    private val _navEvent = MutableSharedFlow<MetNavEvent>(replay = 0)
    val navEvent: SharedFlow<MetNavEvent> = _navEvent.asSharedFlow()

    init {
        ttsManager.acquire()
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
                    RunRequestStatus.RUNNING.name -> _navEvent.emit(MetNavEvent.ToRunning(requestId))
                    RunRequestStatus.ABORTED.name -> {
                        stopLocationService()
                        val ttsText = if (msg.triggeredRole == "BLIND") "视障用户已取消请求，返回主页"
                        else "已放弃接单，返回主页"
                        ttsManager.speakAndWait(ttsText, TtsManager.Priority.HIGH)
                        _navEvent.emit(MetNavEvent.ToHome)
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

    override fun onCleared() {
        super.onCleared()
        stopLocationService()
        ttsManager.release()
    }
}
