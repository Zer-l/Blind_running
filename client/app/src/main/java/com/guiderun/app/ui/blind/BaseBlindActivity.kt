package com.guiderun.app.ui.blind

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.guiderun.app.R
import com.guiderun.app.accessibility.BlindFeedback
import com.guiderun.app.accessibility.HapticFeedback
import com.guiderun.app.accessibility.SosCoordinator
import com.guiderun.app.accessibility.TtsManager
import com.guiderun.app.accessibility.voice.VoiceCommandHost
import com.guiderun.app.accessibility.voice.VoiceCommandManager
import com.guiderun.app.accessibility.voice.VolumeKeyDispatcher
import com.guiderun.app.data.local.UserPreferences
import com.guiderun.app.ui.theme.BlindThemeResolver
import com.guiderun.app.util.PhoneDialer
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
abstract class BaseBlindActivity : AppCompatActivity(), VoiceCommandHost {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BlindActivityEntryPoint {
        fun userPreferences(): UserPreferences
    }

    @Inject lateinit var sosCoordinator: SosCoordinator
    @Inject lateinit var ttsManager: TtsManager
    @Inject lateinit var hapticFeedback: HapticFeedback
    @Inject lateinit var voiceCommandManager: VoiceCommandManager
    @Inject lateinit var blindFeedback: BlindFeedback
    @Inject lateinit var userPreferences: UserPreferences

    /** 当前生效的字号缩放，由 Fragment 通过 BlindFontScaler.apply 应用。 */
    var currentBlindFontScale: Float = UserPreferences.DEFAULT_BLIND_FONT_SCALE
        private set

    /** Active fragment sets this to the current requestId so SOS can call the API. */
    override var activeRequestId: String? = null

    /**
     * 当前页面对应志愿者的手机号；非 null 时启用音量+键连按 3 次拨号。
     * MatchedFragment / BlindRunningFragment / BlindReviewFragment 在 onResume 注入，onPause 清空。
     */
    var activeCallPeerPhone: String? = null

    /** Active fragment registers a touch forwarder for long-press detection. */
    var touchEventForwarder: ((MotionEvent) -> Unit)? = null

    private val audioManager: AudioManager by lazy {
        getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    private val volumeKeyDispatcher: VolumeKeyDispatcher by lazy {
        VolumeKeyDispatcher(
            audioManager = audioManager,
            onVoiceTrigger = { voiceCommandManager.startListening() },
            onVolumeUpTriple = { triggerCallPeer() },
            onVolumeDownTriple = { sosCoordinator.trigger(activeRequestId) },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 用户偏好通过 Hilt EntryPoint 在 Hilt @Inject 注入之前拿到，
        // 因为 setTheme 必须在 super.onCreate(setContentView) 之前调用。
        val prefs = EntryPointAccessors.fromApplication(
            applicationContext,
            BlindActivityEntryPoint::class.java,
        ).userPreferences()
        val themeId = runBlocking { prefs.getBlindContrastThemeOnce() }
        currentBlindFontScale = runBlocking { prefs.getBlindFontScaleOnce() }
        setTheme(BlindThemeResolver.resolve(themeId))

        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
    }

    override fun onDestroy() {
        volumeKeyDispatcher.release()
        super.onDestroy()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        touchEventForwarder?.invoke(event)
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (volumeKeyDispatcher.dispatch(event)) return true
        return super.dispatchKeyEvent(event)
    }

    // ===== VoiceCommandHost 部分实现：电话由 Base 统一处理；导航相关由子类 (BlindActivity) 实现 =====
    final override fun voiceCallPeer() = triggerCallPeer()

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
                blindFeedback.error(R.string.call_peer_failed)
            }
            PhoneDialer.Result.InvalidPhone -> {
                ttsManager.speak(getString(R.string.blind_call_no_phone), TtsManager.Priority.HIGH)
                hapticFeedback.warning()
            }
        }
    }

}
