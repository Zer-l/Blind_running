package com.guiderun.app.ui.blind

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guiderun.app.data.local.UserPreferences
import com.guiderun.app.ui.theme.BlindDesignTokens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BlindAccessibilitySettingsUiState(
    val ttsSpeed: Float = UserPreferences.DEFAULT_TTS_SPEECH_RATE,
    val ttsVolume: Float = UserPreferences.DEFAULT_BLIND_TTS_VOLUME,
    val hapticStrength: Int = UserPreferences.DEFAULT_BLIND_HAPTIC_STRENGTH,
    val fontScale: Float = UserPreferences.DEFAULT_BLIND_FONT_SCALE,
    val contrastTheme: String = UserPreferences.DEFAULT_BLIND_CONTRAST_THEME,
    val isLoading: Boolean = false,
)

/**
 * 触发 Activity.recreate() 的事件。
 * 字号和对比度主题变化需要重建 Activity 才能生效（Configuration / themeOverlay 都在 onCreate 注入）。
 */
sealed interface BlindAccessibilitySettingsEvent {
    data object RecreateRequired : BlindAccessibilitySettingsEvent
}

@HiltViewModel
class BlindAccessibilitySettingsViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BlindAccessibilitySettingsUiState())
    val uiState: StateFlow<BlindAccessibilitySettingsUiState> = _uiState.asStateFlow()

    private val _events = MutableStateFlow<BlindAccessibilitySettingsEvent?>(null)
    val events: StateFlow<BlindAccessibilitySettingsEvent?> = _events.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _uiState.value = BlindAccessibilitySettingsUiState(
                ttsSpeed = userPreferences.getTtsSpeechRate().first(),
                ttsVolume = userPreferences.getBlindTtsVolume().first(),
                hapticStrength = userPreferences.getBlindHapticStrength().first(),
                fontScale = userPreferences.getBlindFontScale().first(),
                contrastTheme = userPreferences.getBlindContrastTheme().first(),
            )
        }
    }

    fun updateTtsSpeed(speed: Float) {
        _uiState.value = _uiState.value.copy(ttsSpeed = speed)
        viewModelScope.launch { userPreferences.saveTtsSpeechRate(speed) }
    }

    fun updateTtsVolume(volume: Float) {
        _uiState.value = _uiState.value.copy(ttsVolume = volume)
        viewModelScope.launch { userPreferences.saveBlindTtsVolume(volume) }
    }

    fun updateHapticStrength(strength: Int) {
        _uiState.value = _uiState.value.copy(hapticStrength = strength)
        viewModelScope.launch { userPreferences.saveBlindHapticStrength(strength) }
    }

    fun updateFontScale(scale: Float) {
        if (scale == _uiState.value.fontScale) return
        _uiState.value = _uiState.value.copy(fontScale = scale)
        viewModelScope.launch {
            userPreferences.saveBlindFontScale(scale)
            _events.value = BlindAccessibilitySettingsEvent.RecreateRequired
        }
    }

    fun updateContrastTheme(theme: String) {
        if (theme == _uiState.value.contrastTheme) return
        if (theme !in BlindDesignTokens.ContrastTheme.Options) return
        _uiState.value = _uiState.value.copy(contrastTheme = theme)
        viewModelScope.launch {
            userPreferences.saveBlindContrastTheme(theme)
            _events.value = BlindAccessibilitySettingsEvent.RecreateRequired
        }
    }

    fun onEventHandled() {
        _events.value = null
    }
}
