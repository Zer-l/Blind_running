package com.guiderun.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guiderun.app.domain.model.ProvisioningStatus
import com.guiderun.app.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val phone: String = "",
    val code: String = "",
    val step: LoginStep = LoginStep.PHONE,
    val isLoading: Boolean = false,
    val loginSuccess: Boolean = false,
    val needsRoleSelect: Boolean = false,
    val countdown: Int = 0,
    val error: String? = null,
)

enum class LoginStep {
    PHONE,  // 输入手机号
    CODE,   // 输入验证码
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState = _uiState.asStateFlow()

    fun onPhoneChanged(phone: String) {
        _uiState.update { it.copy(phone = phone, error = null) }
    }

    fun onCodeChanged(code: String) {
        if (code.length <= 6) {
            _uiState.update { it.copy(code = code, error = null) }
        }
    }

    fun sendSms() {
        val phone = _uiState.value.phone.trim()
        if (phone.length != 11) {
            _uiState.update { it.copy(error = "请输入正确的手机号") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            authRepository.sendSms(phone)
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false, step = LoginStep.CODE, countdown = 60) }
                    startCountdown()
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun resendSms() {
        if (_uiState.value.countdown > 0) return
        sendSms()
    }

    fun backToPhone() {
        _uiState.update { it.copy(step = LoginStep.PHONE, code = "", error = null) }
    }

    fun login() {
        val state = _uiState.value
        val phone = state.phone.trim()
        val code = state.code.trim()
        if (code.length != 6) {
            _uiState.update { it.copy(error = "请输入6位验证码") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            authRepository.login(phone, code)
                .onSuccess { result ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loginSuccess = true,
                            needsRoleSelect = result.provisioningStatus == ProvisioningStatus.PENDING_ROLE,
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun onNavigated() {
        _uiState.update { it.copy(loginSuccess = false) }
    }

    private fun startCountdown() {
        viewModelScope.launch {
            while (_uiState.value.countdown > 0) {
                delay(1000)
                _uiState.update { it.copy(countdown = it.countdown - 1) }
            }
        }
    }
}
