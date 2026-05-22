package com.guiderun.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guiderun.app.data.local.RequestPreferences
import com.guiderun.app.data.local.UserPreferences
import com.guiderun.app.domain.model.RunRequest
import com.guiderun.app.domain.model.UserRole
import com.guiderun.app.domain.repository.AuthRepository
import com.guiderun.app.domain.repository.RunRequestRepository
import com.guiderun.app.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val userPreferences: UserPreferences,
    private val requestPreferences: RequestPreferences,
    runRequestRepository: RunRequestRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()

    /** 当前用户的进行中订单。null 时不显示横幅。 */
    val activeRequest: StateFlow<RunRequest?> = runRequestRepository.activeRequest
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * 一键发起是否可用：依赖 RequestPreferences.loadLast() 是否非空。
     * 在 init 和 refreshUser 时刷新（视障端用户发起一次后回到 Home 即可使用）。
     */
    private val _quickStartEnabled = MutableStateFlow(false)
    val quickStartEnabled: StateFlow<Boolean> = _quickStartEnabled.asStateFlow()

    init {
        loadUser()
        refreshQuickStartEnabled()
    }

    private fun refreshQuickStartEnabled() {
        viewModelScope.launch {
            _quickStartEnabled.value = requestPreferences.loadLast() != null
        }
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
