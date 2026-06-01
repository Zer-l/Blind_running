package com.guiderun.app.accessibility

import android.content.Context
import com.guiderun.app.R
import com.guiderun.app.domain.repository.RunRequestRepository
import com.guiderun.app.domain.repository.UserRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SOS 紧急求助协调器（Singleton）。
 *
 * trigger() 的两条处理路径：
 * 1. 跑步中（requestId != null）→ 调用后端 emergency API，服务端通知志愿者和紧急联系人
 * 2. 非跑步中（requestId == null）→ 仅本地通知已配置的紧急联系人（TODO: 接入短信/推送）
 *
 * 触发来源：音量-键三连击（VolumeKeyDispatcher）或语音指令 SOS（CommandExecutor）。
 */
@Singleton
class SosCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val hapticFeedback: HapticFeedback,
    private val ttsManager: TtsManager,
    private val runRequestRepository: RunRequestRepository,
    private val userRepository: UserRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun trigger(requestId: String?) {
        hapticFeedback.emergency()
        ttsManager.speak(context.getString(R.string.tts_sos_triggering), TtsManager.Priority.CRITICAL)
        if (requestId != null) {
            scope.launch {
                runRequestRepository.emergency(requestId)
                    .onFailure { e -> Timber.w(e, "SOS emergency call failed for requestId=$requestId") }
            }
        } else {
            notifyEmergencyContacts()
        }
    }

    private fun notifyEmergencyContacts() {
        scope.launch {
            userRepository.getEmergencyContacts()
                .onSuccess { contacts ->
                    if (contacts.isEmpty()) {
                        ttsManager.speak(context.getString(R.string.tts_sos_no_contacts), TtsManager.Priority.CRITICAL)
                        return@onSuccess
                    }
                    contacts.forEach { _ ->
                        // TODO: 接入推送/短信后在此发送通知；实装前不播报成功
                    }
                    ttsManager.speak(context.getString(R.string.tts_sos_contacts_pending), TtsManager.Priority.CRITICAL)
                }
                .onFailure { e ->
                    Timber.w(e, "SOS: Failed to load emergency contacts")
                    ttsManager.speak(context.getString(R.string.tts_sos_load_failed), TtsManager.Priority.CRITICAL)
                }
        }
    }
}
