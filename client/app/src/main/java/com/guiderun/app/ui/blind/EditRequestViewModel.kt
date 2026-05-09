package com.guiderun.app.ui.blind

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.guiderun.app.accessibility.TtsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class EditRequestUiState(
    val selectedDurationMinutes: Int = 60,
    val locationDescription: String = "当前位置",
    val notes: String = "",
)

@HiltViewModel
class EditRequestViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val ttsManager: TtsManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        EditRequestUiState(
            selectedDurationMinutes = savedStateHandle.get<Int>("durationMinutes") ?: 60,
            locationDescription = savedStateHandle.get<String>("locationDescription") ?: "当前位置",
            notes = savedStateHandle.get<String>("notes") ?: "",
        )
    )
    val uiState: StateFlow<EditRequestUiState> = _uiState.asStateFlow()

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
}
