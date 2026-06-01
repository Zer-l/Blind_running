package com.guiderun.app.ui.volunteer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guiderun.app.R
import com.guiderun.app.data.local.UserPreferences
import com.guiderun.app.data.remote.WebSocketManager
import com.guiderun.app.domain.model.AvailableRunRequest
import com.guiderun.app.domain.model.RunRequest
import com.guiderun.app.domain.repository.LocationProvider
import com.guiderun.app.domain.repository.RunRequestRepository
import com.guiderun.app.util.runCatchingCancellable
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 接单列表页 UI 状态。 */
data class VolunteerOrderListUiState(
    val isOnline: Boolean = true,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val availableRequests: List<AvailableRunRequest> = emptyList(),
    val errorMessage: String? = null,
    val selectedRadiusMeters: Double = 3000.0,
)

/**
 * 志愿者订单列表页 ViewModel。
 *
 * 核心逻辑：
 * - 上线状态（isOnline）控制是否轮询附近订单；下线时列表清空，不消耗定位资源
 * - 定位优先取缓存（getLastLocation，快），缓存失效则启动 Flow 等第一个新鲜定位（最多等 5s 超时）
 * - 搜索半径（selectedRadiusMeters）变化时立即触发重新拉取，避免用户切换筛选后列表不刷新
 * - WS 重连成功时重拉列表，保证断网恢复后附近订单状态实时更新
 */
@HiltViewModel
class VolunteerOrderListViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val runRequestRepository: RunRequestRepository,
    private val locationProvider: LocationProvider,
    private val userPreferences: UserPreferences,
    private val wsManager: WebSocketManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VolunteerOrderListUiState())
    val uiState = _uiState.asStateFlow()

    /** 进行中的订单（订阅 Repository 单一数据源）。null 时不显示横幅。 */
    val activeRequest: StateFlow<RunRequest?> = runRequestRepository.activeRequest
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private var locationJob: Job? = null

    init {
        viewModelScope.launch {
            val token = userPreferences.getAccessToken() ?: return@launch
            wsManager.connect(token)
        }
        viewModelScope.launch {
            wsManager.reconnected.collect { loadAvailableRequests() }
        }
        // 冷启动 / 重新进入时主动刷新一次活跃订单（Repository 内部维护 StateFlow）
        viewModelScope.launch {
            runRequestRepository.refreshActiveRequest("VOLUNTEER")
        }
    }

    fun loadAvailableRequests() {
        if (!_uiState.value.isOnline) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val location = runCatchingCancellable { locationProvider.getLastLocation() }.getOrNull()
            if (location == null) {
                // 保持在线，启动位置监听，获取到位置后自动加载
                _uiState.update { it.copy(isLoading = false, errorMessage = context.getString(R.string.volunteer_locating)) }
                startLocationListening()
                return@launch
            }
            fetchAvailable(location.lat, location.lng)
        }
    }

    fun onRefresh() {
        if (!_uiState.value.isOnline) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
            val location = runCatchingCancellable { locationProvider.getLastLocation() }.getOrNull()
            if (location == null) {
                _uiState.update { it.copy(isRefreshing = false, errorMessage = context.getString(R.string.volunteer_locating)) }
                startLocationListening()
                return@launch
            }
            fetchAvailable(location.lat, location.lng, isRefresh = true)
        }
    }

    private fun startLocationListening() {
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            val geoPoint = runCatchingCancellable {
                locationProvider.locationUpdates(5_000L).first()
            }.getOrNull()
            if (geoPoint != null) {
                fetchAvailable(geoPoint.lat, geoPoint.lng)
            } else {
                _uiState.update { it.copy(isOnline = false, errorMessage = context.getString(R.string.volunteer_location_failed)) }
            }
        }
    }

    private suspend fun fetchAvailable(lat: Double, lng: Double, isRefresh: Boolean = false) {
        val radius = _uiState.value.selectedRadiusMeters
        runRequestRepository.getAvailableRequests(lat = lat, lng = lng, radiusMeters = radius)
            .onSuccess { list ->
                _uiState.update {
                    if (isRefresh) it.copy(availableRequests = list, isRefreshing = false)
                    else it.copy(availableRequests = list, isLoading = false)
                }
            }
            .onFailure {
                _uiState.update {
                    if (isRefresh) it.copy(isRefreshing = false, errorMessage = context.getString(R.string.volunteer_refresh_failed))
                    else it.copy(isLoading = false, errorMessage = context.getString(R.string.volunteer_load_failed))
                }
            }
    }

    fun onRadiusSelected(meters: Double) {
        if (_uiState.value.selectedRadiusMeters == meters) return
        _uiState.update { it.copy(selectedRadiusMeters = meters) }
        // 在线时立即重拉；离线时只更新 state，下次上线生效
        if (_uiState.value.isOnline) {
            loadAvailableRequests()
        }
    }

    fun onToggleOnline(wantOnline: Boolean) {
        if (wantOnline) {
            _uiState.update { it.copy(isOnline = true) }
            viewModelScope.launch { runRequestRepository.refreshActiveRequest("VOLUNTEER") }
            loadAvailableRequests()
        } else {
            locationJob?.cancel()
            _uiState.update { it.copy(isOnline = false, availableRequests = emptyList()) }
            // 下线时若有活跃订单，提示用户但不阻止
            if (activeRequest.value != null) {
                _uiState.update { it.copy(errorMessage = context.getString(R.string.volunteer_active_order_offline_hint)) }
            }
        }
    }

    fun onLocationPermissionDenied() {
        _uiState.update { it.copy(isLoading = false, isOnline = false, errorMessage = context.getString(R.string.volunteer_location_permission_required)) }
    }

    fun onErrorShown() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
