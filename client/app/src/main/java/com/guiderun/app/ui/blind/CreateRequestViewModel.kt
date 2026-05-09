package com.guiderun.app.ui.blind

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guiderun.app.accessibility.HapticFeedback
import com.guiderun.app.accessibility.TtsManager
import com.guiderun.app.data.location.ReverseGeocoder
import com.guiderun.app.domain.model.GeoPoint
import com.guiderun.app.domain.repository.LocationProvider
import com.guiderun.app.domain.usecase.CreateRunRequestUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

data class CreateRequestUiState(
    val locationStatus: LocationStatus = LocationStatus.Loading,
    val locationDescription: String = "当前位置",
    val selectedDurationMinutes: Int = 30,
    val notes: String = "",
    val confirmCountdown: Int? = null,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface LocationStatus {
    data object Loading : LocationStatus
    data class Located(val lat: Double, val lng: Double) : LocationStatus
    data object Failed : LocationStatus
}

sealed interface CreateRequestNavEvent {
    data class ToWaitingMatch(val requestId: String) : CreateRequestNavEvent
    data object Back : CreateRequestNavEvent
}

@HiltViewModel
class CreateRequestViewModel @Inject constructor(
    private val ttsManager: TtsManager,
    private val hapticFeedback: HapticFeedback,
    private val createRunRequest: CreateRunRequestUseCase,
    private val locationProvider: LocationProvider,
    private val reverseGeocoder: ReverseGeocoder,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateRequestUiState())
    val uiState: StateFlow<CreateRequestUiState> = _uiState.asStateFlow()

    private val _navEvent = MutableSharedFlow<CreateRequestNavEvent>(replay = 0)
    val navEvent: SharedFlow<CreateRequestNavEvent> = _navEvent.asSharedFlow()

    private var countdownJob: Job? = null

    // ★ 不在 init 中调用 loadLocation()，由 Fragment 在权限授予后调用

    /**
     * Fragment 确认权限已授予后调用，开始获取定位。
     * 重复调用安全——已定位成功则跳过。
     */
    fun startLocationUpdates() {
        if (uiState.value.locationStatus is LocationStatus.Located) return
        loadLocation()
    }

    private fun loadLocation() {
        viewModelScope.launch {
            _uiState.update { it.copy(locationStatus = LocationStatus.Loading) }

            // Step 1: 尝试缓存位置（通常瞬间返回）
            val cached = try {
                withTimeoutOrNull(3_000) { locationProvider.getLastLocation() }
            } catch (_: Exception) { null }

            if (cached != null) {
                onLocationObtained(cached)
                return@launch
            }

            // Step 2: 请求实时定位，15 秒超时（GPS 冷启动需要更长时间）
            val fresh = withTimeoutOrNull(15_000) {
                locationProvider.locationUpdates(3_000).first()
            }

            if (fresh != null) {
                onLocationObtained(fresh)
            } else {
                _uiState.update { it.copy(locationStatus = LocationStatus.Failed) }
                ttsManager.speak("定位超时，请检查定位权限设置，或点击修改按钮手动输入集合地点")
            }
        }
    }

    private suspend fun onLocationObtained(point: GeoPoint) {
        // 逆地理编码：经纬度 → 可读地址
        val address = point.description.ifBlank {
            reverseGeocoder.getAddress(point.lat, point.lng)
        }
        val displayAddress = address.ifBlank { "当前位置" }

        _uiState.update {
            it.copy(
                locationStatus = LocationStatus.Located(point.lat, point.lng),
                locationDescription = displayAddress,
            )
        }

        // ★ 定位成功播报
        if (address.isNotBlank()) {
            ttsManager.speak("定位成功，$address")
        } else {
            ttsManager.speak("定位成功")
        }
    }

    fun onScreenResumed() {
        ttsManager.acquire()
    }

    fun onScreenPaused() {
        ttsManager.release()
        countdownJob?.cancel()
        countdownJob = null
        _uiState.update { it.copy(confirmCountdown = null) }
    }

    fun onDurationSelected(minutes: Int) {
        _uiState.update { it.copy(selectedDurationMinutes = minutes) }
    }

    fun onNotesChanged(text: String) {
        _uiState.update { it.copy(notes = text) }
    }

    fun onLocationPermissionDenied() {
        _uiState.update { it.copy(locationStatus = LocationStatus.Failed) }
        ttsManager.speak("无法获取位置权限，您可以点击修改按钮手动输入集合地点", TtsManager.Priority.HIGH)
    }

    fun onConfirmPressed() {
        if (uiState.value.locationStatus is LocationStatus.Loading) {
            ttsManager.speak("位置尚未获取，请稍候")
            return
        }
        val state = uiState.value
        if (state.locationStatus is LocationStatus.Failed && state.locationDescription == "当前位置") {
            ttsManager.speak("位置获取失败，请点击修改按钮手动输入集合地点")
            return
        }

        if (countdownJob?.isActive == true) {
            countdownJob?.cancel()
            countdownJob = null
            _uiState.update { it.copy(confirmCountdown = null) }
            ttsManager.speak("已取消", TtsManager.Priority.HIGH)
            return
        }

        hapticFeedback.warning()
        countdownJob = viewModelScope.launch {
            // ★ 改 speak → speakAndWait，播完再开始倒数
            ttsManager.speakAndWait("3秒后提交请求，再次点击可取消", TtsManager.Priority.HIGH)

            for (i in 3 downTo 1) {
                ensureActive()
                _uiState.update { it.copy(confirmCountdown = i) }
                ttsManager.speakAndWait("$i", TtsManager.Priority.HIGH)
                delay(1000)
            }

            ensureActive()
            _uiState.update { it.copy(confirmCountdown = null) }
            submitRequest()
        }
    }

    fun onCancelPressed() {
        if (countdownJob?.isActive == true) {
            countdownJob?.cancel()
            countdownJob = null
            _uiState.update { it.copy(confirmCountdown = null) }
            ttsManager.speak("已取消", TtsManager.Priority.HIGH)
        } else {
            viewModelScope.launch { _navEvent.emit(CreateRequestNavEvent.Back) }
        }
    }

    fun tryHandleShakeCancel(): Boolean {
        if (countdownJob?.isActive == true) {
            countdownJob?.cancel()
            countdownJob = null
            _uiState.update { it.copy(confirmCountdown = null) }
            ttsManager.speak("已取消", TtsManager.Priority.HIGH)
            return true
        }
        return true
    }

    fun onEditRequestResult(durationMinutes: Int, locationDescription: String, notes: String) {
        _uiState.update {
            it.copy(
                selectedDurationMinutes = durationMinutes,
                locationDescription = locationDescription,
                notes = notes,
            )
        }
    }

    fun onRetryLocation() {
        _uiState.update { it.copy(locationStatus = LocationStatus.Loading) }
        loadLocation()
    }

    private suspend fun submitRequest() {
        val current = uiState.value
        val (lat, lng) = when (val s = current.locationStatus) {
            is LocationStatus.Located -> s.lat to s.lng
            is LocationStatus.Failed -> 0.0 to 0.0
            is LocationStatus.Loading -> return
        }
        _uiState.update { it.copy(isSubmitting = true, confirmCountdown = null, errorMessage = null) }
        ttsManager.speak("正在提交请求…", TtsManager.Priority.HIGH)

        createRunRequest(
            durationMinutes = current.selectedDurationMinutes,
            location = GeoPoint(lat, lng, current.locationDescription),
            notes = current.notes.ifBlank { null },
        ).onSuccess { request ->
            _uiState.update { it.copy(isSubmitting = false) }
            ttsManager.speak("请求已发出，正在等待志愿者", TtsManager.Priority.HIGH)
            hapticFeedback.confirm()
            _navEvent.emit(CreateRequestNavEvent.ToWaitingMatch(request.id))
        }.onFailure { e ->
            _uiState.update { it.copy(isSubmitting = false, errorMessage = e.message ?: "提交失败，请重试") }
            ttsManager.speak("提交失败：${e.message ?: "请重试"}")
            hapticFeedback.error()
        }
    }
}
