package com.guiderun.app.ui.volunteer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guiderun.app.domain.exception.RequestConflictException
import com.guiderun.app.domain.model.RunRequest
import com.guiderun.app.domain.repository.LocationProvider
import com.guiderun.app.domain.repository.RunRequestRepository
import com.guiderun.app.ui.shared.map.CameraTarget
import com.guiderun.app.ui.shared.map.GuideRunMapState
import com.guiderun.app.ui.shared.map.PolylineConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class VolunteerRequestDetailUiState(
    val request: RunRequest? = null,
    val isLoading: Boolean = false,
    val isAccepting: Boolean = false,
    val mapState: GuideRunMapState = GuideRunMapState(),
    val errorMessage: String? = null,
)

sealed interface VolunteerRequestDetailNavEvent {
    data class ToNavigating(val requestId: String) : VolunteerRequestDetailNavEvent
}

@HiltViewModel
class VolunteerRequestDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val runRequestRepository: RunRequestRepository,
    private val locationProvider: LocationProvider,
) : ViewModel() {

    private val requestId: String = checkNotNull(savedStateHandle["requestId"])

    private val _uiState = MutableStateFlow(VolunteerRequestDetailUiState())
    val uiState = _uiState.asStateFlow()

    private val _navEvent = MutableSharedFlow<VolunteerRequestDetailNavEvent>(replay = 0)
    val navEvent: SharedFlow<VolunteerRequestDetailNavEvent> = _navEvent.asSharedFlow()

    /** 当前是否已有进行中订单：用于禁用接单按钮。 */
    val activeRequest: StateFlow<RunRequest?> = runRequestRepository.activeRequest
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        loadRequest()
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
                            // 首次加载下发 CameraTarget 让地图聚焦集合点；后续 WS / 定位刷新只改 latLng，
                            // 不重建 CameraTarget，避免用户缩放后被反复拉回。
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

    /**
     * 订阅本机定位更新志愿者位置 + 蓝色直线指引：让志愿者接单前直观看到自己与集合点的相对位置。
     * 与 VolunteerNavigatingViewModel 一致；接单详情页不启动前台 Service，仅用前台 collect。
     */
    private fun observeVolunteerPosition() {
        viewModelScope.launch {
            runCatching {
                locationProvider.locationUpdates(5_000L).collect { geoPoint ->
                    _uiState.update { state ->
                        val volunteer = geoPoint.lat to geoPoint.lng
                        val meeting = state.mapState.blindLatLng
                        val polylines = if (meeting != null) {
                            listOf(
                                PolylineConfig(
                                    points = listOf(volunteer, meeting),
                                    colorHex = ROUTE_HINT_COLOR,
                                    width = 12f,
                                ),
                            )
                        } else {
                            emptyList()
                        }
                        state.copy(
                            mapState = state.mapState.copy(
                                volunteerLatLng = volunteer,
                                polylines = polylines,
                            ),
                        )
                    }
                }
            }.onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                Timber.w(it, "RequestDetailVM: volunteer position updates failed")
            }
        }
    }

    fun onAccept() {
        // 前端兜底：有进行中订单时拒绝；服务端 HAS_ACTIVE_ORDER 也是兜底保护
        if (activeRequest.value != null) {
            _uiState.update { it.copy(errorMessage = "您已有进行中的订单，请先处理后再接单") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isAccepting = true) }
            runRequestRepository.accept(requestId)
                .onSuccess { _navEvent.emit(VolunteerRequestDetailNavEvent.ToNavigating(requestId)) }
                .onFailure { e ->
                    val serverMsg = e.message
                    val msg = when {
                        e is RequestConflictException -> "手慢了，该订单已被接"
                        serverMsg?.contains("进行中") == true -> serverMsg
                        else -> "接单失败，请重试"
                    }
                    _uiState.update { it.copy(isAccepting = false, errorMessage = msg) }
                }
        }
    }

    fun onErrorShown() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private companion object {
        // 与 VolunteerNavigatingViewModel 一致：Material Blue 600，避免与跑步速度色冲突
        const val ROUTE_HINT_COLOR = "#1E88E5"
    }
}
