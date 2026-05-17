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
 * 使用约定：
 * - [info]：进度/状态信息，NORMAL 优先级排队，避免打断；轻触震动。
 * - [success]：操作成功，HIGH 优先级立刻播报；确认震动。
 * - [warning]：危险或撤销提示，HIGH + 双脉冲震动。
 * - [error]：失败或异常，HIGH + 三脉冲震动。
 * - [permissionDenied]：权限拒绝场景，等同 warning，但语义更明确。
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
        tts.speak(appContext.getString(res, *formatArgs), TtsManager.Priority.HIGH)
    }

    fun success(message: CharSequence) {
        haptic.confirm()
        tts.speak(message.toString(), TtsManager.Priority.HIGH)
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
        tts.speak(appContext.getString(reasonRes, *formatArgs), TtsManager.Priority.HIGH)
    }
}
