package com.guiderun.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.guiderun.app.R
import com.guiderun.app.data.local.LastRequestPrefs
import com.guiderun.app.data.local.UserPreferences
import com.guiderun.app.domain.model.RunRequest
import com.guiderun.app.domain.model.UserRole
import com.guiderun.app.domain.repository.AuthRepository
import com.guiderun.app.domain.repository.RunRequestRepository
import com.guiderun.app.domain.repository.UserRepository
import com.guiderun.app.domain.usecase.LoadLastRequestUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val nickname: String = "",
    val activeRole: String = "",
    val activeRoleEnum: UserRole? = null,
    val isLoading: Boolean = false,
    val loggedOut: Boolean = false,
    /** 上次订单摘要（用于一键发起 TTS 播报），null 表示无历史 */
    val lastRequestSummary: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val userPreferences: UserPreferences,
    private val loadLastRequest: LoadLastRequestUseCase,
    runRequestRepository: RunRequestRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()

    /** 当前用户的进行中订单。null 时不显示横幅。 */
    val activeRequest: StateFlow<RunRequest?> = runRequestRepository.activeRequest
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * 一键发起是否可用：依赖 [LoadLastRequestUseCase] 是否能取到上次订单（本地偏好 → 服务端最近订单兜底）。
     * 在 init / refreshUser / 回首页 onResume（[refreshQuickStart]）时刷新，
     * 避免完成一次跑步后需重启才显示按钮。
     */
    private val _quickStartEnabled = MutableStateFlow(false)
    val quickStartEnabled: StateFlow<Boolean> = _quickStartEnabled.asStateFlow()

    init {
        loadUser()
        refreshQuickStartEnabled()
    }

    /** 回首页 onResume 调用：重算一键发起按钮可见性与摘要。 */
    fun refreshQuickStart() = refreshQuickStartEnabled()

    private fun refreshQuickStartEnabled() {
        viewModelScope.launch {
            val last = loadLastRequest()
            _quickStartEnabled.value = last != null
            _uiState.update {
                it.copy(lastRequestSummary = last?.toSummary())
            }
        }
    }

    /** 将上次订单参数转为 TTS 可播报的摘要 */
    private fun LastRequestPrefs.toSummary(): String {
        val parts = mutableListOf<String>()
        parts.add(context.getString(R.string.quick_start_summary_location, locationDesc))
        parts.add(context.getString(R.string.quick_start_summary_duration, durationMinutes))
        if (notes.isNotBlank()) parts.add(context.getString(R.string.quick_start_summary_notes, notes))
        return parts.joinToString("，")
    }

    /**
     * 加载用户资料。TTS 入场播报由 UI 层（BlindHomeFragment.onResume）显式控制，
     * 此处仅刷新数据：避免 ViewModel 在 init / refreshUser / 旋屏重建时被动播报"首页+昵称+角色"，
     * 与 Fragment 自身的 onResume 播报叠加。
     */
    private fun loadUser() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val roleEnum = userPreferences.getActiveRole()
                ?.let { runCatching { UserRole.valueOf(it) }.getOrNull() }
            val roleDisplay = roleEnum?.displayName() ?: userPreferences.getActiveRole() ?: ""
            userRepository.getMe()
                .onSuccess { user ->
                    _uiState.update {
                        it.copy(
                            nickname = user.nickname,
                            activeRole = roleDisplay,
                            activeRoleEnum = roleEnum,
                            isLoading = false,
                        )
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(isLoading = false) }
                }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _uiState.update { it.copy(loggedOut = true) }
        }
    }

    fun onNavigated() {
        _uiState.update { it.copy(loggedOut = false) }
    }

    fun refreshUser() {
        loadUser()
        refreshQuickStartEnabled()
    }
}
