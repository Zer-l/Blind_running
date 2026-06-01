package com.guiderun.app.ui.blind

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.guiderun.app.R
import com.guiderun.app.accessibility.BlindFeedback
import com.guiderun.app.accessibility.HapticFeedback
import com.guiderun.app.accessibility.SosCoordinator
import com.guiderun.app.accessibility.TtsManager
import com.guiderun.app.accessibility.voice.VoiceCommandHost
import com.guiderun.app.accessibility.voice.VoiceCommandManager
import com.guiderun.app.accessibility.voice.VolumeKeyDispatcher
import com.guiderun.app.data.local.UserPreferences
import com.guiderun.app.ui.theme.BlindDesignTokens
import com.guiderun.app.ui.theme.BlindThemeResolver
import com.guiderun.app.util.PhoneDialer
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * 视障端 Activity 基类，抽取所有 Fragment 共享的无障碍基础设施：
 *
 * 1. 字号缩放：在 attachBaseContext 注入 Configuration.fontScale，优先于 super.onCreate
 * 2. 对比度主题：setTheme() 必须在 super.onCreate/setContentView 之前，用 Hilt EntryPoint 同步读 DataStore
 * 3. 音量键三路语义：[VolumeKeyDispatcher] 统一处理短按调音量 / 三连击拨号或 SOS / 长按语音指令
 * 4. 权限：onCreate 批量申请核心权限（定位/麦克风/通知），tryStartVoiceListening 动态续申请 RECORD_AUDIO
 * 5. VoiceCommandHost：声明导航/拨电话接口，由子类 BlindActivity 实现导航部分，Base 实现拨号部分
 * 6. touchEventForwarder：允许当前 Fragment 通过回调转发 Activity 层的触摸事件（长按手势检测用）
 */
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

    /** Active fragment sets this to the current requestId so SOS can call the API. */
    override var activeRequestId: String? = null

    /**
     * 当前页面对应志愿者的手机号；非 null 时启用音量+键连按 3 次拨号。
     * BlindMatchedFragment / BlindRunningFragment / BlindReviewFragment 在 onResume 注入，onPause 清空。
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
            onVoiceTrigger = { tryStartVoiceListening() },
            onVolumeUpTriple = { triggerCallPeer() },
            onVolumeDownTriple = { sosCoordinator.trigger(activeRequestId) },
        )
    }

    /**
     * 视障端基础权限包：
     * 进入 Activity 时一次性弹原生权限弹窗。视障用户操作 TalkBack 双击较慢，把麦克风/定位/通知
     * 一次性走完，避免后续音量键长按、定位、前台服务等关键场景被弹窗打断 TTS 朗读节奏。
     * 后台定位 (Android 11+) 必须等前台定位授予后才能单独申请，留给 BlindRunningFragment。
     */
    private val basePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        onBasePermissionResult(results)
    }

    /**
     * 音量键长按 / 备注框麦克风按钮触发语音输入前的 RECORD_AUDIO 申请。
     * 与基础包独立：用户拒绝基础包后仍能在按下音量键时再次申请。授予后立即续录避免用户重按。
     */
    private val recordAudioLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceCommandManager.startListening()
        } else {
            val msgRes = if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                R.string.voice_input_permission_denied
            } else {
                R.string.blind_permission_record_audio_blocked
            }
            ttsManager.speak(getString(msgRes), TtsManager.Priority.INTERACTION)
            hapticFeedback.warning()
        }
    }

    /**
     * 字号缩放通过 Configuration.fontScale 注入到 Activity 的 base context，
     * 让所有 TextView 通过系统的 sp→px 转换自动放大；
     * 避免手动 walk View 树修改 textSize 的脆弱方案（在 Fragment 切换 / Material 控件状态变化时会失效）。
     * 字号变化需 recreate() 才能生效（BlindAccessibilitySettingsViewModel 已发 RecreateRequired 事件）。
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        val prefs = EntryPointAccessors.fromApplication(
            newBase.applicationContext,
            BlindActivityEntryPoint::class.java,
        ).userPreferences()
        val scale = runBlocking { prefs.getBlindFontScaleOnce() }
        if (scale != 1.0f) {
            val overrideConfig = Configuration()
            overrideConfig.fontScale = scale
            applyOverrideConfiguration(overrideConfig)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 主题通过 Hilt EntryPoint 在 Hilt @Inject 注入之前拿到，
        // 因为 setTheme 必须在 super.onCreate(setContentView) 之前调用。
        val prefs = EntryPointAccessors.fromApplication(
            applicationContext,
            BlindActivityEntryPoint::class.java,
        ).userPreferences()
        val themeId = runBlocking { prefs.getBlindContrastThemeOnce() }
        setTheme(BlindThemeResolver.resolve(themeId))

        // 按对比度主题显式指定 system bar 风格，避免 DayNight 默认按系统暗黑模式决定
        // 导致黑底主题（深背景）下状态栏内容为黑色不可见。
        // dark style：内容浅色（白），适合深背景；light style：内容深色（黑），适合浅背景。
        val systemBarStyle = when (themeId) {
            BlindDesignTokens.ContrastTheme.White,
            BlindDesignTokens.ContrastTheme.Yellow -> SystemBarStyle.light(
                Color.TRANSPARENT,
                Color.TRANSPARENT,
            )
            else -> SystemBarStyle.dark(Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = systemBarStyle, navigationBarStyle = systemBarStyle)
        super.onCreate(savedInstanceState)
        requestBaseBlindPermissionsIfNeeded()
    }

    /**
     * 入口侧批量申请视障端核心权限。已全部授予则静默；存在未授予项则 TTS 解释意图 + 弹原生弹窗。
     * POST_NOTIFICATIONS 仅 Android 13+ 需要运行时申请；CALL_PHONE 不强制（PhoneDialer 已降级到拨号盘）。
     */
    private fun requestBaseBlindPermissionsIfNeeded() {
        val missing = buildList {
            if (!isGranted(Manifest.permission.ACCESS_FINE_LOCATION)) add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (!isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)) add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (!isGranted(Manifest.permission.RECORD_AUDIO)) add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !isGranted(Manifest.permission.POST_NOTIFICATIONS)
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (missing.isEmpty()) return
        ttsManager.speak(getString(R.string.blind_permission_rationale), TtsManager.Priority.INTERACTION)
        basePermissionLauncher.launch(missing.toTypedArray())
    }

    private fun onBasePermissionResult(results: Map<String, Boolean>) {
        val allGranted = results.values.all { it }
        val msgRes = if (allGranted) {
            R.string.blind_permission_all_granted
        } else {
            R.string.blind_permission_some_denied
        }
        ttsManager.speak(getString(msgRes), TtsManager.Priority.INTERACTION)
        if (allGranted) hapticFeedback.confirm() else hapticFeedback.warning()
    }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    /**
     * 音量键长按 / 麦克风按钮触发语音输入前的统一入口。
     * 已授权直接 startListening；未授权则 TTS 解释意图 + 弹原生 Permission Dialog；
     * 授权回调成功后自动续录，用户无需重按音量键。
     */
    fun tryStartVoiceListening() {
        if (isGranted(Manifest.permission.RECORD_AUDIO)) {
            timber.log.Timber.d("tryStartVoiceListening: granted, start ASR")
            voiceCommandManager.startListening()
            return
        }
        timber.log.Timber.w("tryStartVoiceListening: RECORD_AUDIO not granted, requesting…")
        ttsManager.speak(
            getString(R.string.blind_permission_record_audio_requesting),
            TtsManager.Priority.INTERACTION,
        )
        hapticFeedback.tick()
        recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
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
            ttsManager.speak(getString(R.string.blind_call_no_phone), TtsManager.Priority.INTERACTION)
            hapticFeedback.warning()
            return
        }
        when (PhoneDialer.call(this, phone)) {
            PhoneDialer.Result.Calling,
            PhoneDialer.Result.OpenedDialer -> {
                ttsManager.speak(getString(R.string.blind_call_calling), TtsManager.Priority.INTERACTION)
                hapticFeedback.confirm()
            }
            PhoneDialer.Result.Failed -> {
                blindFeedback.error(R.string.call_peer_failed)
            }
            PhoneDialer.Result.InvalidPhone -> {
                ttsManager.speak(getString(R.string.blind_call_no_phone), TtsManager.Priority.INTERACTION)
                hapticFeedback.warning()
            }
        }
    }

}
