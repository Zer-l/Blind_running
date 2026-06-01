package com.guiderun.app.ui.volunteer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guiderun.app.data.local.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 设置页 UI 状态：当前选中的主题 ID。 */
data class SettingsUiState(
    val currentThemeId: String = "orange",
)

/**
 * 志愿者设置页 ViewModel（同时服务主题选择页）。
 *
 * 通过 DataStore 持久化主题 ID，使用 [stateIn] + [SharingStarted.WhileSubscribed]
 * 将 DataStore Flow 转为 StateFlow，UI 无订阅者时自动停止上游节省资源。
 */
@HiltViewModel
class VolunteerSettingsViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = userPreferences.observeThemeId()
        .map { SettingsUiState(currentThemeId = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setTheme(themeId: String) {
        viewModelScope.launch {
            userPreferences.saveThemeId(themeId)
        }
    }
}
