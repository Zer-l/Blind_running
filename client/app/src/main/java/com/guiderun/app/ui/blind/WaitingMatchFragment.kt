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
import androidx.fragment.app.setFragmentResultListener
import com.guiderun.app.databinding.FragmentWaitingMatchBinding
import com.guiderun.app.ui.blind.widget.BlindConfirmDialogFragment
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
            onGestureStart = { viewModel.onLongPressStarted() },
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
     * 返回键：弹全屏长按确认页（长按 2 秒取消订单）。
     * 短按"继续等待"则关闭对话框留在此页。最小化/返回首页改走语音指令"返回首页"。
     */
    private fun setupBackPressInterception() {
        setFragmentResultListener(REQ_KEY_CANCEL) { _, bundle ->
            if (bundle.getBoolean(BlindConfirmDialogFragment.KEY_CONFIRMED)) {
                viewModel.executeCancel()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    BlindConfirmDialogFragment.newInstance(
                        requestKey = REQ_KEY_CANCEL,
                        titleRes = R.string.interrupt_title_leave_waiting,
                        messageRes = R.string.interrupt_message_leave_waiting,
                        primaryLabelRes = R.string.interrupt_btn_cancel_order,
                        primaryHintRes = R.string.blind_hint_cancel_order_long_press,
                        thresholdLabelRes = R.string.blind_tts_cancel_order_threshold,
                        cancelledLabelRes = R.string.blind_tts_long_press_cancelled,
                        secondaryLabelRes = R.string.interrupt_btn_stay,
                        hostPageTitleRes = R.string.waiting_match_title,
                    ).show(parentFragmentManager, REQ_KEY_CANCEL)
                }
            },
        )
    }

    private suspend fun collectUiState() {
        viewModel.uiState.collect { state ->
            val minutes = state.elapsedSeconds / 60
            val seconds = state.elapsedSeconds % 60
            binding.tvElapsed.text = getString(R.string.waiting_match_elapsed, minutes, seconds)

            // isCancelling 状态不显示"加载中..."，取消操作很快完成无需中间状态

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
                is WaitingMatchNavEvent.ToHome -> {
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

    override fun onResume() {
        super.onResume()
        // 接力 TTS：上游（MatchedViewModel MATCHING 分支）通过 setPendingWaitingTts 暂存原因。
        // 把 string 透传给 VM，由它串行调度 pending → page title → hint，
        // 避免后入队 HIGH speakAndWait 用 QUEUE_FLUSH 打断 pending。
        val pending = (activity as? BlindActivity)?.consumePendingWaitingTts()?.let { getString(it) }
        viewModel.onScreenResumed(pendingTts = pending)
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

    private companion object {
        const val REQ_KEY_CANCEL = "waiting_cancel_confirm"
    }
}
