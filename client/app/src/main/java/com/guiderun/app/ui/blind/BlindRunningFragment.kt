package com.guiderun.app.ui.blind

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
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
import com.guiderun.app.ui.common.showInterruptDialog
import com.guiderun.app.util.EdgeToEdgeHelper
import com.guiderun.app.util.PaceCalculator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BlindRunningFragment : Fragment() {

    private val viewModel: BlindRunningViewModel by viewModels()
    private var _binding: FragmentBlindRunningBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var ttsManager: TtsManager
    @Inject lateinit var hapticFeedback: HapticFeedback

    private var savedBrightness: Float = -1f

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
     * 返回键：跑步中不允许直接退出（服务端状态机限制 RUNNING 不接受 cancel）。
     * 弹「最小化/留在此页」对话框 + TTS 提示长按结束。
     */
    private fun setupBackPressInterception() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    ttsManager.speak(
                        getString(R.string.interrupt_message_leave_running),
                        TtsManager.Priority.HIGH,
                    )
                    showInterruptDialog(
                        activity = requireActivity(),
                        title = getString(R.string.interrupt_title_leave_running),
                        message = getString(R.string.interrupt_message_leave_running)
                            + "\n" + getString(R.string.interrupt_hint_resume),
                        stayLabel = getString(R.string.interrupt_btn_stay),
                        homeLabel = getString(R.string.interrupt_btn_back_home),
                        onHome = { (activity as? BlindActivity)?.navigateToHome() },
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
                BlindRunningNavEvent.ToHome ->
                    (activity as? BlindActivity)?.navigateToHome()
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
