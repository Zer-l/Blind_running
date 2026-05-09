package com.guiderun.app.ui.volunteer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guiderun.app.data.local.UserPreferences
import com.guiderun.app.data.remote.WebSocketManager
import com.guiderun.app.domain.model.AvailableRunRequest
import com.guiderun.app.domain.repository.LocationProvider
import com.guiderun.app.domain.repository.RunRequestRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VolunteerHomeUiState(
    val isOnline: Boolean = true,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val availableRequests: List<AvailableRunRequest> = emptyList(),
    val errorMessage: String? = null,
    val hasActiveOrder: Boolean = false,
)

@HiltViewModel
class VolunteerHomeViewModel @Inject constructor(
    private val runRequestRepository: RunRequestRepository,
    private val locationProvider: LocationProvider,
    private val userPreferences: UserPreferences,
    private val wsManager: WebSocketManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VolunteerHomeUiState())
    val uiState = _uiState.asStateFlow()

    private var locationJob: Job? = null

    init {
        viewModelScope.launch {
            val token = userPreferences.getAccessToken() ?: return@launch
            wsManager.connect(token)
        }
        viewModelScope.launch {
            wsManager.reconnected.collect { loadAvailableRequests() }
        }
        checkActiveOrder()
    }

    private fun checkActiveOrder() {
        viewModelScope.launch {
            runRequestRepository.getMyRequests("VOLUNTEER")
                .onSuccess { requests ->
                    _uiState.update { it.copy(hasActiveOrder = requests.any { r -> r.status.isActive() }) }
                }
                .onFailure {
                    // 查询失败不阻塞流程，保持当前状态
                }
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
        runRequestRepository.getAvailableRequests(lat = lat, lng = lng)
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

    fun onToggleOnline(wantOnline: Boolean) {
        if (wantOnline) {
            _uiState.update { it.copy(isOnline = true) }
            checkActiveOrder()
            loadAvailableRequests()
        } else {
            locationJob?.cancel()
            _uiState.update { it.copy(isOnline = false, availableRequests = emptyList()) }
            // 异步检查是否有活跃订单，有则提醒但不阻止下线
            viewModelScope.launch {
                runRequestRepository.getMyRequests("VOLUNTEER")
                    .onSuccess { requests ->
                        if (requests.any { it.status.isActive() }) {
                            _uiState.update { it.copy(hasActiveOrder = true, errorMessage = "你有进行中的陪跑订单，上线后可继续处理") }
                        }
                    }
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
