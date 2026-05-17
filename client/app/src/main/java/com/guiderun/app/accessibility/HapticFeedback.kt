package com.guiderun.app.accessibility

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.guiderun.app.data.local.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HapticFeedback @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferences: UserPreferences,
) {
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    /** 0=关 / 1=标准 / 2=强；由 UserPreferences.getBlindHapticStrength 自动更新。 */
    @Volatile private var currentStrength: Int = STRENGTH_NORMAL

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        scope.launch {
            userPreferences.getBlindHapticStrength().collect { strength ->
                currentStrength = strength.coerceIn(STRENGTH_OFF, STRENGTH_STRONG)
            }
        }
    }

    /** 轻触反馈：按钮点击、列表选中 */
    fun tick() = vibrate(VibrationEffect.createOneShot(20, amp(80)), Channel.TOUCH)

    /** 确认反馈：操作成功 */
    fun confirm() = vibrate(VibrationEffect.createOneShot(60, amp(180)), Channel.TOUCH)

    /** 警告反馈：双脉冲，提示危险操作 */
    fun warning() = vibrate(
        VibrationEffect.createWaveform(
            longArrayOf(0, 80, 60, 80),
            scaleAmps(intArrayOf(0, 200, 0, 200)),
            -1,
        ),
        Channel.NOTIFICATION,
    )

    /** 错误反馈：三脉冲 */
    fun error() = vibrate(
        VibrationEffect.createWaveform(
            longArrayOf(0, 50, 50, 50, 50, 50),
            scaleAmps(intArrayOf(0, 255, 0, 255, 0, 255)),
            -1,
        ),
        Channel.ALARM,
    )

    /**
     * SOS 紧急求助：三段递进震动。
     * 注意：emergency 是安全关键功能，**不受 currentStrength 影响**，无条件最强震动。
     */
    fun emergency() = vibrateRaw(
        VibrationEffect.createWaveform(
            longArrayOf(0, 100, 100, 200, 100, 400),
            intArrayOf(0, 255, 0, 255, 0, 255),
            -1,
        ),
        Channel.ALARM,
    )

    /** 受 currentStrength 控制的震动入口。strength=0 时跳过。 */
    private fun vibrate(effect: VibrationEffect, channel: Channel) {
        if (currentStrength == STRENGTH_OFF) return
        vibrateRaw(effect, channel)
    }

    /** 不受 currentStrength 控制的震动入口，仅供 SOS 等安全功能使用。 */
    private fun vibrateRaw(effect: VibrationEffect, channel: Channel) {
        if (!vibrator.hasVibrator()) return
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                val attrs = VibrationAttributes.Builder().setUsage(channel.vibrationUsage).build()
                vibrator.vibrate(effect, attrs)
            }
            else -> {
                @Suppress("DEPRECATION")
                vibrator.vibrate(effect, channel.audioAttributes)
            }
        }
    }

    /** 单值 amplitude 按 strength 放大；0 输入保留为 0（间隔静默段）。 */
    private fun amp(value: Int): Int {
        if (value == 0) return 0
        val factor = when (currentStrength) {
            STRENGTH_STRONG -> 1.6f
            else -> 1.0f
        }
        return (value * factor).toInt().coerceIn(1, 255)
    }

    /** 数组 amplitude 按 strength 放大，保留间隔静默段。 */
    private fun scaleAmps(original: IntArray): IntArray =
        IntArray(original.size) { amp(original[it]) }

    /** 按业务用途分桶。各 ROM 会按 usage 决定是否受"触感反馈/勿扰/省电"开关影响。 */
    private enum class Channel(
        val vibrationUsage: Int,
        val audioAttributes: AudioAttributes,
    ) {
        TOUCH(
            vibrationUsage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                VibrationAttributes.USAGE_TOUCH else 0,
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        ),
        NOTIFICATION(
            vibrationUsage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                VibrationAttributes.USAGE_NOTIFICATION else 0,
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        ),
        ALARM(
            vibrationUsage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                VibrationAttributes.USAGE_ALARM else 0,
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        ),
    }

    companion object {
        const val STRENGTH_OFF = 0
        const val STRENGTH_NORMAL = 1
        const val STRENGTH_STRONG = 2
    }
}
