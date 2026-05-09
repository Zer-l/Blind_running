package com.guiderun.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guiderun.app.domain.model.UserRole
import com.guiderun.app.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RoleSelectUiState(
    val selectedRole: UserRole? = null,
    val isLoading: Boolean = false,
    val success: Boolean = false,
    val error: String? = null,
    val showBackWarning: Boolean = false,
)

@HiltViewModel
class RoleSelectViewModel @Inject constructor(
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RoleSelectUiState())
    val uiState = _uiState.asStateFlow()

    fun onRoleSelected(role: UserRole) {
        _uiState.update { it.copy(selectedRole = role, error = null) }
    }

    fun confirm() {
        val role = _uiState.value.selectedRole ?: run {
            _uiState.update { it.copy(error = "请选择角色") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            userRepository.updateRoles(listOf(role))
                .onSuccess { _uiState.update { it.copy(isLoading = false, success = true) } }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun onNavigated() {
        _uiState.update { it.copy(success = false) }
    }

    fun onBackAttempted() {
        _uiState.update { it.copy(showBackWarning = true) }
    }

    fun onBackWarningDismissed() {
        _uiState.update { it.copy(showBackWarning = false) }
    }
}
