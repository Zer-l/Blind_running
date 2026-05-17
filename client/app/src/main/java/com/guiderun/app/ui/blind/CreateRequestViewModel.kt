package com.guiderun.app.ui.blind

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guiderun.app.R
import com.guiderun.app.accessibility.HapticFeedback
import com.guiderun.app.accessibility.TtsManager
import com.guiderun.app.data.local.RequestPreferences
import com.guiderun.app.data.location.ReverseGeocoder
import com.guiderun.app.domain.model.GeoPoint
import com.guiderun.app.domain.repository.LocationProvider
import com.guiderun.app.domain.usecase.CreateRunRequestUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val requestPreferences: RequestPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateRequestUiState())
    val uiState: StateFlow<CreateRequestUiState> = _uiState.asStateFlow()

    private val _navEvent = MutableSharedFlow<CreateRequestNavEvent>(replay = 0)
    val navEvent: SharedFlow<CreateRequestNavEvent> = _navEvent.asSharedFlow()

    private var isPageAnnounced = false
    private var hasAnnouncedPage = false

    init {
        // 启动时预填上次成功提交的偏好（如果有）。
        viewModelScope.launch {
            requestPreferences.loadLast()?.let { prefs ->
                _uiState.update {
                    it.copy(
                        selectedDurationMinutes = prefs.durationMinutes,
                        locationDescription = prefs.locationDesc,
                        notes = prefs.notes,
                        locationStatus = if (prefs.lat != null && prefs.lng != null) {
                            LocationStatus.Located(prefs.lat, prefs.lng)
                        } else {
                            LocationStatus.Loading
                        },
                    )
                }
            }
        }
    }

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

            if (!isPageAnnounced) {
                ttsManager.speakAndWait(
                    context.getString(R.string.tts_location_loading),
                    TtsManager.Priority.NORMAL,
                )
            }

            val cached = try {
                withTimeoutOrNull(3_000) { locationProvider.getLastLocation() }
            } catch (_: Exception) {
                null
            }

            if (cached != null) {
                onLocationObtained(cached)
                return@launch
            }

            val fresh = withTimeoutOrNull(15_000) {
                locationProvider.locationUpdates(3_000).first()
            }

            if (fresh != null) {
                onLocationObtained(fresh)
            } else {
                _uiState.update { it.copy(locationStatus = LocationStatus.Failed) }
                ttsManager.speak(
                    context.getString(R.string.tts_location_timeout),
                    TtsManager.Priority.HIGH,
                )
            }
        }
    }

    private suspend fun onLocationObtained(point: GeoPoint) {
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

        if (address.isNotBlank()) {
            ttsManager.speak(
                context.getString(R.string.tts_location_success, address),
                TtsManager.Priority.HIGH,
            )
        } else {
            ttsManager.speak(
                context.getString(R.string.tts_location_success_no_address),
                TtsManager.Priority.HIGH,
            )
        }
    }

    fun onScreenResumed() {
        ttsManager.acquire()
        if (!hasAnnouncedPage) {
            hasAnnouncedPage = true
            viewModelScope.launch {
                ttsManager.stop()
                ttsManager.speakAndWait(
                    context.getString(R.string.tts_page_create_request),
                    TtsManager.Priority.HIGH,
                )
                isPageAnnounced = true
                ttsManager.speak(
                    context.getString(R.string.tts_hint_create_request),
                    TtsManager.Priority.HIGH,
                )
            }
        }
    }

    fun onScreenPaused() {
        ttsManager.release()
    }

    fun onDurationSelected(minutes: Int) {
        _uiState.update { it.copy(selectedDurationMinutes = minutes) }
    }

    fun onNotesChanged(text: String) {
        _uiState.update { it.copy(notes = text) }
    }

    fun onLocationPermissionDenied() {
        _uiState.update { it.copy(locationStatus = LocationStatus.Failed) }
        ttsManager.speak(
            context.getString(R.string.tts_location_permission_denied),
            TtsManager.Priority.HIGH,
        )
    }

    fun onEditRequestResult(
        durationMinutes: Int,
        locationDescription: String,
        notes: String,
        newLat: Double?,
        newLng: Double?,
    ) {
        _uiState.update {
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

    /** 由 LongPressGestureView 倒计时结束、或语音指令 CONFIRM 触发的最终提交。 */
    fun submit() {
        viewModelScope.launch { submitRequest() }
    }

    /** HomeScreen "一键发起"路径：用上次偏好 + 当前定位（如已获取）。 */
    fun submitWithLastPrefs() {
        viewModelScope.launch {
            val prefs = requestPreferences.loadLast() ?: return@launch
            _uiState.update {
                it.copy(
                    selectedDurationMinutes = prefs.durationMinutes,
                    locationDescription = prefs.locationDesc,
                    notes = prefs.notes,
                    locationStatus = if (prefs.lat != null && prefs.lng != null) {
                        LocationStatus.Located(prefs.lat, prefs.lng)
                    } else {
                        it.locationStatus
                    },
                )
            }
            submitRequest()
        }
    }

    /** 返回键退出：Fragment 已在 OnBackPressedCallback 弹对话框确认。 */
    fun onBackRequested() {
        viewModelScope.launch { _navEvent.emit(CreateRequestNavEvent.Back) }
    }

    private suspend fun submitRequest() {
        val current = uiState.value
        val (lat, lng) = when (val s = current.locationStatus) {
            is LocationStatus.Located -> s.lat to s.lng
            is LocationStatus.Failed -> 0.0 to 0.0
            is LocationStatus.Loading -> {
                ttsManager.speak(
                    context.getString(R.string.tts_location_loading),
                    TtsManager.Priority.HIGH,
                )
                return
            }
        }
        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
        ttsManager.speak(
            context.getString(R.string.tts_submitting),
            TtsManager.Priority.HIGH,
        )

        createRunRequest(
            durationMinutes = current.selectedDurationMinutes,
            location = GeoPoint(lat, lng, current.locationDescription),
            notes = current.notes.ifBlank { null },
        ).onSuccess { request ->
            _uiState.update { it.copy(isSubmitting = false) }
            ttsManager.speak(
                context.getString(R.string.tts_submit_success),
                TtsManager.Priority.HIGH,
            )
            hapticFeedback.confirm()
            // 保存偏好供下次 quick start 使用
            requestPreferences.saveLast(
                durationMinutes = current.selectedDurationMinutes,
                locationDesc = current.locationDescription,
                notes = current.notes,
                lat = if (lat != 0.0) lat else null,
                lng = if (lng != 0.0) lng else null,
            )
            _navEvent.emit(CreateRequestNavEvent.ToWaitingMatch(request.id))
        }.onFailure { e ->
            _uiState.update {
                it.copy(
                    isSubmitting = false,
                    errorMessage = e.message ?: "提交失败，请重试",
                )
            }
            ttsManager.speak(
                context.getString(R.string.tts_submit_failed, e.message ?: "请重试"),
                TtsManager.Priority.HIGH,
            )
            hapticFeedback.error()
        }
    }
}
