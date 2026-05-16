package com.guiderun.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guiderun.app.R
import com.guiderun.app.accessibility.TtsManager
import com.guiderun.app.data.local.UserPreferences
import com.guiderun.app.domain.model.RunRequest
import com.guiderun.app.domain.model.UserRole
import com.guiderun.app.domain.repository.AuthRepository
import com.guiderun.app.domain.repository.RunRequestRepository
import com.guiderun.app.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.content.Context
import javax.inject.Inject
import kotlinx.coroutines.flow.first

data class HomeUiState(
    val nickname: String = "",
    val activeRole: String = "",
    val activeRoleEnum: UserRole? = null,
    val isLoading: Boolean = false,
    val loggedOut: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val userPreferences: UserPreferences,
    private val ttsManager: TtsManager,
    runRequestRepository: RunRequestRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()

    /** 当前用户的进行中订单。null 时不显示横幅。 */
    val activeRequest: StateFlow<RunRequest?> = runRequestRepository.activeRequest
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        loadUser()
    }

    private var isInitialLoad = true

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
                    // ★ 仅首次加载时播报欢迎语
                    if (isInitialLoad) {
                        isInitialLoad = false
                        speakWelcome(user.nickname, roleDisplay)
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(isLoading = false) }
                }
        }
    }

    private fun speakWelcome(nickname: String, role: String) {
        viewModelScope.launch {
            ttsManager.state.first { it is TtsManager.TtsState.Ready }
            val greeting = if (role.isNotEmpty()) {
                context.getString(R.string.tts_welcome, nickname, role)
            } else {
                context.getString(R.string.tts_welcome_no_role, nickname)
            }
            ttsManager.speak(greeting, TtsManager.Priority.HIGH)
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
    }
}
