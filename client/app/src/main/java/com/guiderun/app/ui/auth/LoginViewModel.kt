package com.guiderun.app.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guiderun.app.R
import com.guiderun.app.domain.model.ProvisioningStatus
import com.guiderun.app.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
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
    @ApplicationContext private val context: Context,
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
            _uiState.update { it.copy(error = context.getString(R.string.login_phone_invalid)) }
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
                    _uiState.update { it.copy(isLoading = false, error = loginErrorMessage(e)) }
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
            _uiState.update { it.copy(error = context.getString(R.string.login_code_invalid)) }
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
                    _uiState.update { it.copy(isLoading = false, error = loginErrorMessage(e)) }
                }
        }
    }

    fun onNavigated() {
        _uiState.update { it.copy(loginSuccess = false) }
    }

    /**
     * 网络类异常（[IOException]：断网/超时/连接失败）统一显示友好文案，避免把内网 IP/端口等技术细节
     * 暴露给用户；业务类异常（服务端 code≠0 抛出的 message，如"验证码错误"）是有意展示的提示，原样保留。
     */
    private fun loginErrorMessage(e: Throwable): String =
        if (e is IOException) context.getString(R.string.error_network)
        else e.message ?: context.getString(R.string.error_network)

    private fun startCountdown() {
        viewModelScope.launch {
            while (_uiState.value.countdown > 0) {
                delay(1000)
                _uiState.update { it.copy(countdown = it.countdown - 1) }
            }
        }
    }
}
