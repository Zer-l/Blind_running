package com.guiderun.app.accessibility

import com.guiderun.app.R
import com.guiderun.app.domain.repository.RunRequestRepository
import com.guiderun.app.domain.repository.UserRepository
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

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
