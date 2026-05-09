package com.guiderun.app.accessibility

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HapticFeedback @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    /** 轻触反馈：按钮点击、列表选中 */
    fun tick() = vibrate(VibrationEffect.createOneShot(20, 80))

    /** 确认反馈：操作成功 */
    fun confirm() = vibrate(VibrationEffect.createOneShot(60, 180))

    /** 警告反馈：双脉冲，提示危险操作 */
    fun warning() = vibrate(
        VibrationEffect.createWaveform(longArrayOf(0, 80, 60, 80), intArrayOf(0, 200, 0, 200), -1)
    )

    /** 错误/紧急反馈：三脉冲 */
    fun error() = vibrate(
        VibrationEffect.createWaveform(
            longArrayOf(0, 50, 50, 50, 50, 50),
            intArrayOf(0, 255, 0, 255, 0, 255),
            -1,
        )
    )

    /** SOS 紧急求助：三段递进震动，比 error() 更强烈 */
    fun emergency() = vibrate(
        VibrationEffect.createWaveform(
            longArrayOf(0, 100, 100, 200, 100, 400),
            intArrayOf(0, 255, 0, 255, 0, 255),
            -1,
        )
    )

    private fun vibrate(effect: VibrationEffect) {
        if (vibrator.hasVibrator()) vibrator.vibrate(effect)
    }
}
