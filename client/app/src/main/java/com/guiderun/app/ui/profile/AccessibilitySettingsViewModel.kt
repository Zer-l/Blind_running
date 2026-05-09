package com.guiderun.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guiderun.app.data.local.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccessibilitySettingsUiState(
    val ttsSpeed: Float = 1.5f,
    val isLoading: Boolean = false,
)

@HiltViewModel
class AccessibilitySettingsViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccessibilitySettingsUiState())
    val uiState: StateFlow<AccessibilitySettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val speed = userPreferences.getTtsSpeechRate().first()
            _uiState.value = _uiState.value.copy(ttsSpeed = speed)
        }
    }

    fun updateTtsSpeed(speed: Float) {
        _uiState.value = _uiState.value.copy(ttsSpeed = speed)
        viewModelScope.launch {
            userPreferences.saveTtsSpeechRate(speed)
        }
    }
}
