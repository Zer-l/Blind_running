package com.guiderun.app.accessibility

import android.content.Context
import androidx.annotation.StringRes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 视障端统一反馈工具——替代 Toast。
 *
 * 视障用户看不到 Toast；本类用 TTS 朗读 + 震动两个维度反馈，根据严重程度选择不同优先级与震动模式。
 *
 * 使用约定（与 TtsManager.Priority 锁机制对齐）：
 * - [info]：被动状态进度信息（如刷新中、加载中），NORMAL 排队，可被操作反馈推迟。
 * - [success]：用户操作刚完成的反馈，INTERACTION 持锁防止被低优消息打断。
 * - [warning]/[error]：业务错误或异常，HIGH（INTERACTION 锁内自动入队等待）。
 * - [permissionDenied]：用户拒绝权限申请的反馈，INTERACTION（属于操作链路的延续）。
 */
@Singleton
class BlindFeedback @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val tts: TtsManager,
    private val haptic: HapticFeedback,
) {

    fun info(@StringRes res: Int, vararg formatArgs: Any) {
        haptic.tick()
        tts.speak(appContext.getString(res, *formatArgs), TtsManager.Priority.NORMAL)
    }

    fun info(message: CharSequence) {
        haptic.tick()
        tts.speak(message.toString(), TtsManager.Priority.NORMAL)
    }

    fun success(@StringRes res: Int, vararg formatArgs: Any) {
        haptic.confirm()
        tts.speak(appContext.getString(res, *formatArgs), TtsManager.Priority.INTERACTION)
    }

    fun success(message: CharSequence) {
        haptic.confirm()
        tts.speak(message.toString(), TtsManager.Priority.INTERACTION)
    }

    fun warning(@StringRes res: Int, vararg formatArgs: Any) {
        haptic.warning()
        tts.speak(appContext.getString(res, *formatArgs), TtsManager.Priority.HIGH)
    }

    fun warning(message: CharSequence) {
        haptic.warning()
        tts.speak(message.toString(), TtsManager.Priority.HIGH)
    }

    fun error(@StringRes res: Int, vararg formatArgs: Any) {
        haptic.error()
        tts.speak(appContext.getString(res, *formatArgs), TtsManager.Priority.HIGH)
    }

    fun error(message: CharSequence) {
        haptic.error()
        tts.speak(message.toString(), TtsManager.Priority.HIGH)
    }

    fun permissionDenied(@StringRes reasonRes: Int, vararg formatArgs: Any) {
        haptic.warning()
        tts.speak(appContext.getString(reasonRes, *formatArgs), TtsManager.Priority.INTERACTION)
    }
}
