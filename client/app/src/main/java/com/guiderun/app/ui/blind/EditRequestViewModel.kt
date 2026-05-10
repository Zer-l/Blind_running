package com.guiderun.app.ui.blind

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guiderun.app.accessibility.TtsManager
import com.guiderun.app.data.location.ForwardGeocoder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditRequestUiState(
    val selectedDurationMinutes: Int = 60,
    val locationDescription: String = "当前位置",
    val notes: String = "",
    val isSaving: Boolean = false,
)

/**
 * 修改参数页保存事件。lat/lng 为 null 表示"地址未变"或"地址解析失败"，
 * 此时上一页应保留原 GPS 坐标作为兜底。
 */
sealed interface EditRequestNavEvent {
    data class SaveAndReturn(
        val durationMinutes: Int,
        val locationDescription: String,
        val notes: String,
        val lat: Double?,
        val lng: Double?,
    ) : EditRequestNavEvent
}

@HiltViewModel
class EditRequestViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val ttsManager: TtsManager,
    private val forwardGeocoder: ForwardGeocoder,
) : ViewModel() {

    private val initialLocationDescription: String =
        savedStateHandle.get<String>("locationDescription") ?: "当前位置"

    private val _uiState = MutableStateFlow(
        EditRequestUiState(
            selectedDurationMinutes = savedStateHandle.get<Int>("durationMinutes") ?: 60,
            locationDescription = initialLocationDescription,
            notes = savedStateHandle.get<String>("notes") ?: "",
        )
    )
    val uiState: StateFlow<EditRequestUiState> = _uiState.asStateFlow()

    private val _navEvent = MutableSharedFlow<EditRequestNavEvent>(replay = 0)
    val navEvent: SharedFlow<EditRequestNavEvent> = _navEvent.asSharedFlow()

    fun onScreenResumed() {
        ttsManager.acquire()
        val state = _uiState.value
        val durationText = "${state.selectedDurationMinutes}分钟"
        ttsManager.speak(
            "修改请求参数。当前时长${durationText}，集合地点${state.locationDescription}。",
            TtsManager.Priority.HIGH,
        )
    }

    fun onScreenPaused() {
        ttsManager.release()
    }

    fun onDurationSelected(minutes: Int) {
        _uiState.update { it.copy(selectedDurationMinutes = minutes) }
    }

    fun onLocationDescriptionChanged(text: String) {
        _uiState.update { it.copy(locationDescription = text) }
    }

    fun onNotesChanged(text: String) {
        _uiState.update { it.copy(notes = text) }
    }

    fun onSavePressed() {
        if (_uiState.value.isSaving) return

        val state = _uiState.value
        val descriptionChanged = state.locationDescription != initialLocationDescription

        // 地址未改：直接回传，不跑地理编码
        if (!descriptionChanged) {
            viewModelScope.launch {
                _navEvent.emit(
                    EditRequestNavEvent.SaveAndReturn(
                        durationMinutes = state.selectedDurationMinutes,
                        locationDescription = state.locationDescription,
                        notes = state.notes,
                        lat = null,
                        lng = null,
                    )
                )
            }
            return
        }

        // 地址改了：尝试正向地理编码，失败时兜底回退到旧坐标
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            ttsManager.speak("正在解析地址", TtsManager.Priority.HIGH)

            val point = forwardGeocoder.geocode(state.locationDescription)

            if (point != null) {
                ttsManager.speak("地址解析成功", TtsManager.Priority.HIGH)
            } else {
                ttsManager.speak(
                    "地址解析失败，将使用当前定位作为集合点参考",
                    TtsManager.Priority.HIGH,
                )
            }

            _uiState.update { it.copy(isSaving = false) }
            _navEvent.emit(
                EditRequestNavEvent.SaveAndReturn(
                    durationMinutes = state.selectedDurationMinutes,
                    locationDescription = state.locationDescription,
                    notes = state.notes,
                    lat = point?.lat,
                    lng = point?.lng,
                )
            )
        }
    }
}
