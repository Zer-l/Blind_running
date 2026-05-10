package com.guiderun.app.ui.blind

import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.guiderun.app.R
import com.guiderun.app.accessibility.HapticFeedback
import com.guiderun.app.accessibility.SosCoordinator
import com.guiderun.app.accessibility.TtsManager
import com.guiderun.app.util.PhoneDialer
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
abstract class BaseBlindActivity : AppCompatActivity() {

    @Inject lateinit var sosCoordinator: SosCoordinator
    @Inject lateinit var ttsManager: TtsManager
    @Inject lateinit var hapticFeedback: HapticFeedback

    /** Active fragment sets this to the current requestId so SOS can call the API. */
    var activeRequestId: String? = null

    /**
     * 当前页面对应志愿者的手机号；非 null 时启用音量+键连按 3 次拨号。
     * MatchedFragment / BlindRunningFragment / BlindReviewFragment 在 onResume 注入，onPause 清空。
     */
    var activeCallPeerPhone: String? = null

    /** Active fragment registers a touch forwarder for long-press detection. */
    var touchEventForwarder: ((MotionEvent) -> Unit)? = null

    private val volumeDownPressTimes = ArrayDeque<Long>()
    private val volumeUpPressTimes = ArrayDeque<Long>()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        touchEventForwarder?.invoke(event)
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // 音量↓ / 音量↑ 在 PRESS_WINDOW_MS 内连按 PRESS_COUNT 次分别触发 SOS / 拨打志愿者电话。
        // 真实事件：每次 ACTION_DOWN，repeatCount 仅在长按时递增；这里用 repeatCount==0 过滤短按。
        // 不消费事件 → 系统继续调音量，避免静音视障用户。
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN -> handleVolumeKeyTriple(volumeDownPressTimes) {
                    sosCoordinator.trigger(activeRequestId)
                }
                KeyEvent.KEYCODE_VOLUME_UP -> handleVolumeKeyTriple(volumeUpPressTimes) {
                    triggerCallPeer()
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private inline fun handleVolumeKeyTriple(buffer: ArrayDeque<Long>, action: () -> Unit) {
        val now = System.currentTimeMillis()
        buffer.addLast(now)
        while (buffer.isNotEmpty() && now - buffer.first() > PRESS_WINDOW_MS) {
            buffer.removeFirst()
        }
        if (buffer.size >= PRESS_COUNT) {
            buffer.clear()
            action()
        }
    }

    private fun triggerCallPeer() {
        val phone = activeCallPeerPhone
        if (phone.isNullOrBlank()) {
            ttsManager.speak(getString(R.string.blind_call_no_phone), TtsManager.Priority.HIGH)
            hapticFeedback.warning()
            return
        }
        when (PhoneDialer.call(this, phone)) {
            PhoneDialer.Result.Calling,
            PhoneDialer.Result.OpenedDialer -> {
                ttsManager.speak(getString(R.string.blind_call_calling), TtsManager.Priority.HIGH)
                hapticFeedback.confirm()
            }
            PhoneDialer.Result.Failed -> {
                Toast.makeText(this, R.string.call_peer_failed, Toast.LENGTH_SHORT).show()
                hapticFeedback.error()
            }
            PhoneDialer.Result.InvalidPhone -> {
                ttsManager.speak(getString(R.string.blind_call_no_phone), TtsManager.Priority.HIGH)
                hapticFeedback.warning()
            }
        }
    }

    companion object {
        private const val PRESS_WINDOW_MS = 2_000L
        private const val PRESS_COUNT = 3
    }
}
