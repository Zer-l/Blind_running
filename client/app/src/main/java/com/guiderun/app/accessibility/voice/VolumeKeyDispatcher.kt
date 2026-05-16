package com.guiderun.app.accessibility.voice

import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent

/**
 * 音量键统一调度器。负责：
 * - 音量+长按 [LONG_PRESS_MS] 触发 [onVoiceTrigger]（语音指令）
 * - 音量+短按：松手后延迟 [ADJUST_DEBOUNCE_MS] 调一次音量；累计 [PRESS_COUNT] 次在 [TRIPLE_WINDOW_MS] 内触发 [onVolumeUpTriple]（拨号）
 * - 音量-短按：同样的延迟调音量 + 三连击触发 [onVolumeDownTriple]（SOS）
 *
 * 关键设计：所有音量键事件被本地消费（return true），不再走系统默认音量调节。短按场景下手动延迟调用
 * [AudioManager.adjustVolume]，新按下会取消未到期的延迟任务，因此三连击 / 长按期间音量不会被乱调。
 *
 * 使用：Activity.dispatchKeyEvent 调用 [dispatch]，销毁时调用 [release]。
 */
class VolumeKeyDispatcher(
    private val audioManager: AudioManager,
    private val onVoiceTrigger: () -> Unit,
    private val onVolumeUpTriple: () -> Unit,
    private val onVolumeDownTriple: () -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val volumeDownPressTimes = ArrayDeque<Long>()
    private val volumeUpPressTimes = ArrayDeque<Long>()

    private var volumeUpPressStart: Long = 0L
    private var volumeUpLongPressFired: Boolean = false

    private var pendingVolumeUpAdjust: Runnable? = null
    private var pendingVolumeDownAdjust: Runnable? = null

    private val longPressVoiceRunnable = Runnable {
        volumeUpLongPressFired = true
        onVoiceTrigger()
    }

    /**
     * @return true 表示已消费事件，调用方不应再调 super。
     */
    fun dispatch(event: KeyEvent): Boolean {
        return when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                handleVolumeUpEvent(event)
                true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                handleVolumeDownEvent(event)
                true
            }
            else -> false
        }
    }

    fun release() {
        mainHandler.removeCallbacks(longPressVoiceRunnable)
        pendingVolumeUpAdjust?.let { mainHandler.removeCallbacks(it) }
        pendingVolumeDownAdjust?.let { mainHandler.removeCallbacks(it) }
        pendingVolumeUpAdjust = null
        pendingVolumeDownAdjust = null
    }

    private fun handleVolumeUpEvent(event: KeyEvent) {
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                // 新按下即取消未到期的音量调节，避免快速连按累积调音量
                cancelPendingAdjust(true)
                if (event.repeatCount == 0) {
                    volumeUpPressStart = System.currentTimeMillis()
                    volumeUpLongPressFired = false
                    mainHandler.removeCallbacks(longPressVoiceRunnable)
                    mainHandler.postDelayed(longPressVoiceRunnable, LONG_PRESS_MS)
                }
            }
            KeyEvent.ACTION_UP -> {
                mainHandler.removeCallbacks(longPressVoiceRunnable)
                if (volumeUpLongPressFired) {
                    volumeUpLongPressFired = false
                    return
                }
                val duration = System.currentTimeMillis() - volumeUpPressStart
                if (duration >= LONG_PRESS_MS) return
                val triggered = trackTriple(volumeUpPressTimes, onVolumeUpTriple)
                if (!triggered) {
                    scheduleAdjust(true, AudioManager.ADJUST_RAISE)
                }
            }
        }
    }

    private fun handleVolumeDownEvent(event: KeyEvent) {
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                cancelPendingAdjust(false)
            }
            KeyEvent.ACTION_UP -> {
                val triggered = trackTriple(volumeDownPressTimes, onVolumeDownTriple)
                if (!triggered) {
                    scheduleAdjust(false, AudioManager.ADJUST_LOWER)
                }
            }
        }
    }

    private fun cancelPendingAdjust(isVolumeUp: Boolean) {
        if (isVolumeUp) {
            pendingVolumeUpAdjust?.let { mainHandler.removeCallbacks(it) }
            pendingVolumeUpAdjust = null
        } else {
            pendingVolumeDownAdjust?.let { mainHandler.removeCallbacks(it) }
            pendingVolumeDownAdjust = null
        }
    }

    private fun scheduleAdjust(isVolumeUp: Boolean, direction: Int) {
        val runnable = Runnable {
            audioManager.adjustVolume(
                direction,
                AudioManager.FLAG_SHOW_UI or AudioManager.FLAG_PLAY_SOUND,
            )
            if (isVolumeUp) pendingVolumeUpAdjust = null else pendingVolumeDownAdjust = null
        }
        if (isVolumeUp) pendingVolumeUpAdjust = runnable else pendingVolumeDownAdjust = runnable
        mainHandler.postDelayed(runnable, ADJUST_DEBOUNCE_MS)
    }

    /** @return true 表示触发了三连击。 */
    private inline fun trackTriple(buffer: ArrayDeque<Long>, action: () -> Unit): Boolean {
        val now = System.currentTimeMillis()
        buffer.addLast(now)
        while (buffer.isNotEmpty() && now - buffer.first() > TRIPLE_WINDOW_MS) {
            buffer.removeFirst()
        }
        if (buffer.size >= PRESS_COUNT) {
            buffer.clear()
            action()
            return true
        }
        return false
    }

    companion object {
        private const val PRESS_COUNT = 3
        /** 三连击窗口：3 次按下必须在此时长内完成 */
        private const val TRIPLE_WINDOW_MS = 1_200L
        /** 短按松手到实际调音量的延迟：期间若有新按下则取消，避免三连击误调音量 */
        private const val ADJUST_DEBOUNCE_MS = 450L
        /** 音量+长按触发语音指令的阈值 */
        private const val LONG_PRESS_MS = 1_500L
    }
}
