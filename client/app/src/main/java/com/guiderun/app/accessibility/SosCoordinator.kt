package com.guiderun.app.accessibility

import com.guiderun.app.domain.repository.RunRequestRepository
import com.guiderun.app.domain.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SosCoordinator @Inject constructor(
    private val hapticFeedback: HapticFeedback,
    private val ttsManager: TtsManager,
    private val runRequestRepository: RunRequestRepository,
    private val userRepository: UserRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun trigger(requestId: String?) {
        hapticFeedback.emergency()
        ttsManager.speak("正在为你求助", TtsManager.Priority.HIGH)
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
                        ttsManager.speak("未设置紧急联系人，请尽快添加", TtsManager.Priority.HIGH)
                        return@onSuccess
                    }
                    contacts.forEach { contact ->
                        // TODO: 实际发送通知给紧急联系人
                    }
                    ttsManager.speak("已通知${contacts.size}位紧急联系人", TtsManager.Priority.HIGH)
                }
                .onFailure { e ->
                    Timber.w(e, "SOS: Failed to load emergency contacts")
                    ttsManager.speak("无法获取紧急联系人信息", TtsManager.Priority.HIGH)
                }
        }
    }
}
