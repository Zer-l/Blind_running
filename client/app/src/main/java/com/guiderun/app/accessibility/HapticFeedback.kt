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
    fun tick() = vibrate(
        VibrationEffect.createOneShot(scaleDuration(20), amp(180)),
        Channel.TOUCH,
    )

    /** 确认反馈：操作成功 */
    fun confirm() = vibrate(
        VibrationEffect.createOneShot(scaleDuration(60), amp(200)),
        Channel.TOUCH,
    )

    /** 警告反馈：双脉冲，提示危险操作 */
    fun warning() = vibrate(
        VibrationEffect.createWaveform(
            scaleDurations(longArrayOf(0, 80, 60, 80)),
            scaleAmps(intArrayOf(0, 220, 0, 220)),
            -1,
        ),
        Channel.NOTIFICATION,
    )

    /** 错误反馈：三脉冲 */
    fun error() = vibrate(
        VibrationEffect.createWaveform(
            scaleDurations(longArrayOf(0, 50, 50, 50, 50, 50)),
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

    /**
     * 单值 amplitude 按 strength 缩放；0 输入保留为 0（间隔静默段）。
     *
     * 设计意图：NORMAL 降到 0.6x 而不是把 STRONG 抬到 1.6x —— 后者会被 [coerceIn]
     * 顶到 255 上限，让 NORMAL/STRONG 体感几乎一致。改为降低 NORMAL 后两档差异稳定可感。
     */
    private fun amp(value: Int): Int {
        if (value == 0) return 0
        val factor = when (currentStrength) {
            STRENGTH_STRONG -> 1.0f
            STRENGTH_NORMAL -> 0.6f
            else -> 0.6f  // OFF 时 vibrate() 已提前 return，此分支不会到达
        }
        return (value * factor).toInt().coerceIn(1, 255)
    }

    /** 数组 amplitude 按 strength 缩放，保留间隔静默段。 */
    private fun scaleAmps(original: IntArray): IntArray =
        IntArray(original.size) { amp(original[it]) }

    /**
     * 单值 duration 按 strength 缩放。
     *
     * 必要性：部分 ROM 不实现 [android.os.Vibrator.hasAmplitudeControl]，amplitude 参数被忽略
     * 全部按默认强度震动。此时只能靠 duration 让用户感知 NORMAL/STRONG 的差异。
     */
    private fun scaleDuration(ms: Long): Long {
        if (ms == 0L) return 0L
        val factor = when (currentStrength) {
            STRENGTH_STRONG -> 1.5f
            STRENGTH_NORMAL -> 1.0f
            else -> 1.0f
        }
        return (ms * factor).toLong().coerceAtLeast(1L)
    }

    private fun scaleDurations(original: LongArray): LongArray =
        LongArray(original.size) { scaleDuration(original[it]) }

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
