package com.guiderun.app.ui.blind

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guiderun.app.R
import com.guiderun.app.accessibility.HapticFeedback
import com.guiderun.app.accessibility.TtsManager
import com.guiderun.app.data.location.ReverseGeocoder
import com.guiderun.app.domain.model.GeoPoint
import com.guiderun.app.domain.repository.LocationProvider
import com.guiderun.app.domain.usecase.CreateRunRequestUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
import android.content.Context
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
    @ApplicationContext private val context: Context,
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

    private var isPageAnnounced = false

    private fun loadLocation() {
        viewModelScope.launch {
            _uiState.update { it.copy(locationStatus = LocationStatus.Loading) }

            // 等待页面播报完成后再播报定位状态
            if (!isPageAnnounced) {
                // 页面播报还在进行中，等播完再播定位相关
                ttsManager.speakAndWait(context.getString(R.string.tts_location_loading), TtsManager.Priority.NORMAL)
            }

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
                ttsManager.speak(context.getString(R.string.tts_location_timeout), TtsManager.Priority.HIGH)
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
            ttsManager.speak(context.getString(R.string.tts_location_success, address), TtsManager.Priority.HIGH)
        } else {
            ttsManager.speak(context.getString(R.string.tts_location_success_no_address), TtsManager.Priority.HIGH)
        }
    }

    fun onScreenResumed() {
        ttsManager.acquire()
        if (!hasAnnouncedPage) {
            hasAnnouncedPage = true
            viewModelScope.launch {
                // 先停掉可能残留的播报，确保页面名称优先
                ttsManager.stop()
                ttsManager.speakAndWait(context.getString(R.string.tts_page_create_request), TtsManager.Priority.HIGH)
                isPageAnnounced = true
                ttsManager.speak(context.getString(R.string.tts_hint_create_request), TtsManager.Priority.HIGH)
            }
        }
    }

    private var hasAnnouncedPage = false

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
        ttsManager.speak(context.getString(R.string.tts_location_permission_denied), TtsManager.Priority.HIGH)
    }

    fun onConfirmPressed() {
        if (uiState.value.locationStatus is LocationStatus.Loading) {
            ttsManager.speak(context.getString(R.string.tts_location_loading))
            return
        }
        val state = uiState.value
        if (state.locationStatus is LocationStatus.Failed && state.locationDescription == "当前位置") {
            ttsManager.speak(context.getString(R.string.tts_location_failed_hint))
            return
        }

        if (countdownJob?.isActive == true) {
            countdownJob?.cancel()
            countdownJob = null
            _uiState.update { it.copy(confirmCountdown = null) }
            hapticFeedback.confirm()
            ttsManager.speak(context.getString(R.string.tts_cancelled), TtsManager.Priority.HIGH)
            return
        }

        hapticFeedback.warning()
        countdownJob = viewModelScope.launch {
            ttsManager.speakAndWait(context.getString(R.string.tts_submit_countdown, 5), TtsManager.Priority.HIGH)

            for (i in 5 downTo 1) {
                ensureActive()
                _uiState.update { it.copy(confirmCountdown = i) }
                ttsManager.speakAndWait("$i", TtsManager.Priority.HIGH)
            }

            ensureActive()
            _uiState.update { it.copy(confirmCountdown = null) }
            submitRequest()
        }
    }

    /** 长按达到 2 秒阈值：震动 + "松开发起请求"提示。 */
    fun onLongPressThresholdConfirm() {
        hapticFeedback.warning()
        ttsManager.speak(context.getString(R.string.tts_submit_release), TtsManager.Priority.HIGH)
    }

    /** 短按确认按钮：朗读当前选定（时长 + 地点 + 备注摘要）。 */
    fun onShortPressConfirm() {
        val state = uiState.value
        val location = when (state.locationStatus) {
            is LocationStatus.Located -> state.locationDescription.ifBlank { "当前位置" }
            is LocationStatus.Loading -> "正在定位"
            is LocationStatus.Failed -> "定位失败"
        }
        val notes = state.notes.trim().ifEmpty { "无" }
        val msg = "时长${state.selectedDurationMinutes}分钟，地点${location}，备注${notes}。按住2秒发起请求，5秒倒计时内可撤销"
        ttsManager.speak(msg, TtsManager.Priority.HIGH)
    }

    fun onCancelPressed() {
        if (countdownJob?.isActive == true) {
            countdownJob?.cancel()
            countdownJob = null
            _uiState.update { it.copy(confirmCountdown = null) }
            hapticFeedback.confirm()
            ttsManager.speak(context.getString(R.string.tts_cancelled), TtsManager.Priority.HIGH)
        } else {
            viewModelScope.launch { _navEvent.emit(CreateRequestNavEvent.Back) }
        }
    }

    fun onEditRequestResult(
        durationMinutes: Int,
        locationDescription: String,
        notes: String,
        newLat: Double?,
        newLng: Double?,
    ) {
        _uiState.update {
            // 地理编码成功 → 用新坐标覆盖（即使原状态是 Failed 也能修复"GPS 失败 + 手动输入地址"路径）；
            // 失败/未变 → locationStatus 不动，保留旧 GPS 坐标兜底
            val newStatus = if (newLat != null && newLng != null) {
                LocationStatus.Located(newLat, newLng)
            } else {
                it.locationStatus
            }
            it.copy(
                selectedDurationMinutes = durationMinutes,
                locationDescription = locationDescription,
                notes = notes,
                locationStatus = newStatus,
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
        ttsManager.speak(context.getString(R.string.tts_submitting), TtsManager.Priority.HIGH)

        createRunRequest(
            durationMinutes = current.selectedDurationMinutes,
            location = GeoPoint(lat, lng, current.locationDescription),
            notes = current.notes.ifBlank { null },
        ).onSuccess { request ->
            _uiState.update { it.copy(isSubmitting = false) }
            ttsManager.speak(context.getString(R.string.tts_submit_success), TtsManager.Priority.HIGH)
            hapticFeedback.confirm()
            _navEvent.emit(CreateRequestNavEvent.ToWaitingMatch(request.id))
        }.onFailure { e ->
            _uiState.update { it.copy(isSubmitting = false, errorMessage = e.message ?: "提交失败，请重试") }
            ttsManager.speak(context.getString(R.string.tts_submit_failed, e.message ?: "请重试"))
            hapticFeedback.error()
        }
    }
}
