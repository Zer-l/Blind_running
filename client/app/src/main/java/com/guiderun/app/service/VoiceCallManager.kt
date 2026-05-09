package com.guiderun.app.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.guiderun.app.BuildConfig
import com.guiderun.app.data.remote.WebSocketManager
import com.guiderun.app.data.remote.dto.WsVoiceCallEndedMessage
import com.guiderun.app.data.remote.dto.WsVoiceCallInvitedMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

enum class VoiceCallState {
    IDLE,       // 无通话
    INCOMING,   // 收到来电邀请
    CONNECTING, // 正在连接
    CONNECTED,  // 通话中
}

data class VoiceCallInfo(
    val requestId: String,
    val fromNickname: String,
    val otherPartyPhone: String? = null,
)

@Singleton
class VoiceCallManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val wsManager: WebSocketManager,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _callState = MutableStateFlow(VoiceCallState.IDLE)
    val callState: StateFlow<VoiceCallState> = _callState.asStateFlow()

    private val _currentCall = MutableStateFlow<VoiceCallInfo?>(null)
    val currentCall: StateFlow<VoiceCallInfo?> = _currentCall.asStateFlow()

    private val isEnabled: Boolean get() = BuildConfig.VOICE_CALL_ENABLED

    fun init() {
        if (!isEnabled) {
            return
        }
        observeWsMessages()
    }

    private fun observeWsMessages() {
        scope.launch {
            wsManager.voiceCallInvited.collect { msg ->
                _currentCall.value = VoiceCallInfo(
                    requestId = msg.requestId,
                    fromNickname = msg.fromNickname,
                )
                _callState.value = VoiceCallState.INCOMING
            }
        }
        scope.launch {
            wsManager.voiceCallEnded.collect { msg ->
                hangUp()
            }
        }
    }

    fun initiateCall(requestId: String, otherPartyPhone: String) {
        if (!isEnabled) {
            return
        }
        _callState.value = VoiceCallState.CONNECTING
        _currentCall.value = VoiceCallInfo(
            requestId = requestId,
            fromNickname = "",
            otherPartyPhone = otherPartyPhone,
        )
        dialPhone(otherPartyPhone)
        _callState.value = VoiceCallState.CONNECTED
    }

    fun acceptCall() {
        val call = _currentCall.value ?: return
        if (!isEnabled) return

        call.otherPartyPhone?.let { dialPhone(it) }
        _callState.value = VoiceCallState.CONNECTED
    }

    fun hangUp() {
        _callState.value = VoiceCallState.IDLE
        _currentCall.value = null
    }

    fun rejectCall() {
        hangUp()
    }

    private fun dialPhone(phone: String) {
        try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phone")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Timber.e(e, "Failed to dial phone: $phone")
        }
    }
}
