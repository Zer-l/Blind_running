package com.guiderun.app.ui.blind

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.guiderun.app.databinding.FragmentWaitingMatchBinding
import com.guiderun.app.ui.common.showInterruptDialog
import com.guiderun.app.util.EdgeToEdgeHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class WaitingMatchFragment : Fragment() {

    private val viewModel: WaitingMatchViewModel by viewModels()
    private var _binding: FragmentWaitingMatchBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var ttsManager: TtsManager
    @Inject lateinit var hapticFeedback: HapticFeedback

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentWaitingMatchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        EdgeToEdgeHelper.applyInsets(view)
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
            thresholdLabelRes = R.string.blind_tts_waiting_match_cancel_threshold,
            countdownLabelRes = R.string.blind_tts_long_press_cancelled,
            onCountdownCommitted = { viewModel.executeCancel() },
        )
    }

    /**
     * 等待匹配页：CANCEL 语音指令 = 直接取消（语音是明确意图，跳过 2s+5s）；
     * STATUS = 朗读当前等待时长。
     */
    private fun setupVoiceCommands() = bindVoiceCommands { cmd ->
        when (cmd) {
            VoiceCommand.CANCEL -> { viewModel.executeCancel(); true }
            VoiceCommand.STATUS -> { viewModel.announceWaitTime(); true }
            else -> false
        }
    }

    /**
     * 返回键：弹「取消订单/留在此页/最小化」对话框。
     * 「取消订单」直接调用 executeCancel（明确意图，跳过手势 2s+5s）。
     */
    private fun setupBackPressInterception() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    showInterruptDialog(
                        activity = requireActivity(),
                        title = getString(R.string.interrupt_title_leave_waiting),
                        message = getString(R.string.interrupt_message_leave_waiting)
                            + "\n" + getString(R.string.interrupt_hint_resume),
                        cancelLabel = getString(R.string.interrupt_btn_cancel_order),
                        onCancel = { viewModel.executeCancel() },
                        stayLabel = getString(R.string.interrupt_btn_stay),
                        homeLabel = getString(R.string.interrupt_btn_back_home),
                        onHome = { (activity as? BlindActivity)?.navigateToHome() },
                    )
                }
            },
        )
    }

    private suspend fun collectUiState() {
        viewModel.uiState.collect { state ->
            val minutes = state.elapsedSeconds / 60
            val seconds = state.elapsedSeconds % 60
            binding.tvElapsed.text = getString(R.string.waiting_match_elapsed, minutes, seconds)

            binding.tvCancelStatus.text = if (state.isCancelling) getString(R.string.loading) else ""

            // ★ 关键状态在 contentDescription 上一并暴露给 TalkBack
            binding.tvElapsed.contentDescription = if (minutes > 0)
                "已等待${minutes}分${seconds}秒"
            else
                "已等待${seconds}秒"
        }
    }

    private suspend fun collectNavEvents() {
        viewModel.navEvent.collect { event ->
            when (event) {
                is WaitingMatchNavEvent.ToMatched -> {
                    val args = Bundle().apply { putString("requestId", event.requestId) }
                    findNavController().navigate(R.id.action_waitingMatch_to_matched, args)
                }
                WaitingMatchNavEvent.ToHome ->
                    (activity as? BlindActivity)?.navigateToHome()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onScreenResumed()
        (activity as? BaseBlindActivity)?.activeRequestId = viewModel.requestId
    }

    override fun onPause() {
        super.onPause()
        viewModel.onScreenPaused()
        binding.footer.primaryGesture.reset()
        (activity as? BaseBlindActivity)?.activeRequestId = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
