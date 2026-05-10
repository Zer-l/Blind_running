package com.guiderun.app.accessibility

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
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
    fun tick() = vibrate(VibrationEffect.createOneShot(20, 80), Channel.TOUCH)

    /** 确认反馈：操作成功 */
    fun confirm() = vibrate(VibrationEffect.createOneShot(60, 180), Channel.TOUCH)

    /** 警告反馈：双脉冲，提示危险操作 */
    fun warning() = vibrate(
        VibrationEffect.createWaveform(longArrayOf(0, 80, 60, 80), intArrayOf(0, 200, 0, 200), -1),
        Channel.NOTIFICATION,
    )

    /** 错误/紧急反馈：三脉冲 */
    fun error() = vibrate(
        VibrationEffect.createWaveform(
            longArrayOf(0, 50, 50, 50, 50, 50),
            intArrayOf(0, 255, 0, 255, 0, 255),
            -1,
        ),
        Channel.ALARM,
    )

    /** SOS 紧急求助：三段递进震动，比 error() 更强烈 */
    fun emergency() = vibrate(
        VibrationEffect.createWaveform(
            longArrayOf(0, 100, 100, 200, 100, 400),
            intArrayOf(0, 255, 0, 255, 0, 255),
            -1,
        ),
        Channel.ALARM,
    )

    private fun vibrate(effect: VibrationEffect, channel: Channel) {
        if (!vibrator.hasVibrator()) return
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                // API 33+: 用 VibrationAttributes 显式声明用途，避免被国产 ROM 的"触感反馈"开关静默 mute
                val attrs = VibrationAttributes.Builder().setUsage(channel.vibrationUsage).build()
                vibrator.vibrate(effect, attrs)
            }
            else -> {
                // API 26~32: 用 AudioAttributes，国产 ROM 多数仍会按 usage 路由
                @Suppress("DEPRECATION")
                vibrator.vibrate(effect, channel.audioAttributes)
            }
        }
    }

    /** 按业务用途分桶。各 ROM 会按 usage 决定是否受"触感反馈/勿扰/省电"开关影响。 */
    private enum class Channel(
        val vibrationUsage: Int,
        val audioAttributes: AudioAttributes,
    ) {
        /** 普通点击/确认：受系统"触感反馈"开关控制 */
        TOUCH(
            vibrationUsage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                VibrationAttributes.USAGE_TOUCH else 0,
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        ),
        /** 警告/状态提示：通知通道，勿扰会拦截但触感反馈关不影响 */
        NOTIFICATION(
            vibrationUsage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                VibrationAttributes.USAGE_NOTIFICATION else 0,
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        ),
        /** SOS/紧急：闹钟通道，省电模式 + 勿扰均不静音 */
        ALARM(
            vibrationUsage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                VibrationAttributes.USAGE_ALARM else 0,
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        ),
    }
}
