package com.guiderun.app.ui.volunteer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guiderun.app.data.local.UserPreferences
import com.guiderun.app.data.remote.WebSocketManager
import com.guiderun.app.domain.model.AvailableRunRequest
import com.guiderun.app.domain.model.RunRequest
import com.guiderun.app.domain.repository.LocationProvider
import com.guiderun.app.domain.repository.RunRequestRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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

data class VolunteerOrderListUiState(
    val isOnline: Boolean = true,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val availableRequests: List<AvailableRunRequest> = emptyList(),
    val errorMessage: String? = null,
    val selectedRadiusMeters: Double = 3000.0,
)

@HiltViewModel
class VolunteerOrderListViewModel @Inject constructor(
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
            val location = runCatching { locationProvider.getLastLocation() }.getOrNull()
            if (location == null) {
                // 保持在线，启动位置监听，获取到位置后自动加载
                _uiState.update { it.copy(isLoading = false, errorMessage = "正在获取位置…") }
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
            val location = runCatching { locationProvider.getLastLocation() }.getOrNull()
            if (location == null) {
                _uiState.update { it.copy(isRefreshing = false, errorMessage = "正在获取位置…") }
                startLocationListening()
                return@launch
            }
            fetchAvailable(location.lat, location.lng, isRefresh = true)
        }
    }

    private fun startLocationListening() {
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            val geoPoint = runCatching {
                locationProvider.locationUpdates(5_000L).first()
            }.getOrNull()
            if (geoPoint != null) {
                fetchAvailable(geoPoint.lat, geoPoint.lng)
            } else {
                _uiState.update { it.copy(isOnline = false, errorMessage = "无法获取位置，请检查GPS开关") }
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
            .onFailure { e ->
                _uiState.update {
                    if (isRefresh) it.copy(isRefreshing = false, errorMessage = "刷新失败：${e.message}")
                    else it.copy(isLoading = false, errorMessage = "加载失败：${e.message}")
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
                _uiState.update { it.copy(errorMessage = "你有进行中的陪跑订单，上线后可继续处理") }
            }
        }
    }

    fun onLocationPermissionDenied() {
        _uiState.update { it.copy(isLoading = false, isOnline = false, errorMessage = "需要位置权限才能查看附近的跑步请求") }
    }

    fun onErrorShown() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
