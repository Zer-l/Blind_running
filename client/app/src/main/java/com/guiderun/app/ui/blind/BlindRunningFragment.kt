package com.guiderun.app.ui.blind

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.guiderun.app.R
import com.guiderun.app.accessibility.HapticFeedback
import com.guiderun.app.accessibility.TtsManager
import com.guiderun.app.accessibility.voice.VoiceCommand
import com.guiderun.app.accessibility.voice.bindVoiceCommands
import com.guiderun.app.databinding.FragmentBlindRunningBinding
import com.guiderun.app.util.EdgeToEdgeHelper
import com.guiderun.app.util.PaceCalculator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 视障端跑步中页 Fragment。
 *
 * 主要职责：
 * - onResume 调暗屏幕（防止视障用户误触亮屏）+申请后台定位权限（Android 11+）
 * - 展示距离/时长/配速三个 BlindMetricCard，数据由 BlindRunningViewModel 从 Room 读取（服务写入）
 * - 主按钮长按 2s+5s 发起结束跑步申请（协商式终止）；志愿者申请结束时 tv_status 显示提示
 * - 返回键 = 最小化到首页（后台跑步），TTS 接力避免被 onPause→release 吞掉提示语
 *
 * 屏幕调暗策略：视障用户跑步中无需看屏幕，亮屏既耗电也可能意外解锁；
 * 最低亮度（0.01f）而非完全关闭，避免某些机型 0f 时触摸事件被系统丢弃。
 */
@AndroidEntryPoint
class BlindRunningFragment : Fragment() {

    private val viewModel: BlindRunningViewModel by viewModels()
    private var _binding: FragmentBlindRunningBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var ttsManager: TtsManager
    @Inject lateinit var hapticFeedback: HapticFeedback

    private var savedBrightness: Float = -1f

    private var backgroundLocationRequested = false

    /**
     * Android 11+ 必须等前台定位授予后再单独申请后台定位。
     * 拒绝不致命（前台跑步页可用），只 TTS 提醒。授予后无需重启服务，FusedLocationProvider 自动接管后台事件。
     */
    private val backgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val msgRes = if (granted) {
            R.string.blind_permission_background_location_granted
        } else {
            R.string.blind_permission_background_location_denied
        }
        ttsManager.speak(getString(msgRes), TtsManager.Priority.INTERACTION)
        if (granted) hapticFeedback.confirm() else hapticFeedback.warning()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentBlindRunningBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        EdgeToEdgeHelper.applyInsets(view)
        view.keepScreenOn = true
        setupGestureFooter()
        setupBackPressInterception()
        setupVoiceCommands()


        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { collectUiState() }
                launch { collectNavEvents() }
            }
        }
    }

    private fun setupGestureFooter() {
        binding.footer.primaryGesture.bind(
            scope = viewLifecycleOwner.lifecycleScope,
            ttsManager = ttsManager,
            hapticFeedback = hapticFeedback,
            thresholdLabelRes = R.string.blind_tts_running_end_threshold,
            countdownLabelRes = R.string.blind_tts_long_press_cancelled,
            onCountdownCommitted = { viewModel.executeEndRun() },
            onGestureStart = { viewModel.onLongPressStarted() },
            onGestureEnd = { viewModel.onLongPressEnded() },
        )
    }

    private fun setupVoiceCommands() = bindVoiceCommands { cmd ->
        when (cmd) {
            VoiceCommand.END_RUN -> {
                viewModel.executeEndRun()
                true
            }
            VoiceCommand.STATUS -> {
                viewModel.announceCurrentStatus()
                true
            }
            else -> false
        }
    }

    /**
     * 返回键：服务端状态机禁止 RUNNING 状态 cancel，非破坏性最小化即可。
     * 直接返回首页，跑步在后台继续；TTS 由 BlindHomeFragment.onResume 接力播报，
     * 避免本页 onPause→ttsManager.release()→engine.stop() 把 INTERACTION 入队的提示吞掉。
     */
    private fun setupBackPressInterception() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    (activity as? BlindActivity)?.navigateToHomeWithTts(
                        R.string.blind_tts_running_minimized_to_home,
                    )
                }
            },
        )
    }

    override fun onResume() {
        super.onResume()
        setScreenDim(true)
        viewModel.onScreenResumed()
        (activity as? BaseBlindActivity)?.apply {
            activeRequestId = viewModel.requestId
            activeCallPeerPhone = viewModel.uiState.value.peerPhone
        }
        ensureBackgroundLocationIfNeeded()
    }

    private fun ensureBackgroundLocationIfNeeded() {
        if (backgroundLocationRequested) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val ctx = context ?: return
        val foregroundGranted = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val backgroundGranted = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!foregroundGranted || backgroundGranted) return
        backgroundLocationRequested = true
        ttsManager.speak(
            getString(R.string.blind_permission_background_location_rationale),
            TtsManager.Priority.INTERACTION,
        )
        backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }

    override fun onPause() {
        super.onPause()
        setScreenDim(false)
        viewModel.onScreenPaused()
        binding.footer.primaryGesture.reset()
        (activity as? BaseBlindActivity)?.apply {
            activeRequestId = null
            activeCallPeerPhone = null
        }
    }

    private fun setScreenDim(dim: Boolean) {
        val window = activity?.window ?: return
        val lp = window.attributes
        if (dim) {
            savedBrightness = lp.screenBrightness
            lp.screenBrightness = 0.01f
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            if (savedBrightness >= 0f) {
                lp.screenBrightness = savedBrightness
            } else {
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        window.attributes = lp
    }

    private suspend fun collectUiState() {
        viewModel.uiState.collect { state ->
            (activity as? BaseBlindActivity)?.activeCallPeerPhone = state.peerPhone

            val distanceKm = "%.2f".format(state.totalDistanceMeters / 1000.0)
            binding.cardDistance.setValue(distanceKm)

            val durationStr = formatDuration(state.totalDurationSeconds)
            val minutes = state.totalDurationSeconds / 60
            val seconds = state.totalDurationSeconds % 60
            binding.cardDuration.setValue(durationStr, ttsRead = "${minutes}分${seconds}秒")

            val paceStr = state.displayPaceSeconds?.let { PaceCalculator.formatPace(it) }
                ?: "--'--\""
            val paceTts = state.displayPaceSeconds?.let { p ->
                "${p / 60}分${p % 60}秒每公里"
            } ?: "暂无配速"
            binding.cardPace.setValue(paceStr, ttsRead = paceTts)

            binding.tvPausedBadge.visibility = if (state.isPaused) View.VISIBLE else View.GONE

            // tv_status 仅在志愿者申请结束等关键 assertive 事件显示，常态 GONE 避免与页面标题"跑步进行中"重复
            if (state.endRequestedByVolunteer) {
                binding.tvStatus.text = getString(R.string.blind_running_end_requested_by_volunteer)
                binding.tvStatus.visibility = View.VISIBLE
            } else {
                binding.tvStatus.visibility = View.GONE
            }
        }
    }

    private suspend fun collectNavEvents() {
        viewModel.navEvent.collect { event ->
            when (event) {
                is BlindRunningNavEvent.ToReview -> {
                    val args = Bundle().apply { putString("requestId", event.requestId) }
                    findNavController().navigate(R.id.action_blindRunning_to_review, args)
                }
                is BlindRunningNavEvent.ToHome -> {
                    val act = activity as? BlindActivity
                    if (event.reasonRes != null) {
                        act?.navigateToHomeWithTts(event.reasonRes)
                    } else {
                        act?.navigateToHome()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun formatDuration(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }
}
