package com.guiderun.app.ui.blind

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
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
import com.guiderun.app.databinding.FragmentMatchedBinding
import com.guiderun.app.domain.model.RunRequestStatus
import com.guiderun.app.ui.blind.widget.BlindConfirmDialogFragment
import com.guiderun.app.util.EdgeToEdgeHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MatchedFragment : Fragment() {

    private val viewModel: MatchedViewModel by viewModels()
    private var _binding: FragmentMatchedBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var ttsManager: TtsManager
    @Inject lateinit var hapticFeedback: HapticFeedback

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentMatchedBinding.inflate(inflater, container, false)
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
            thresholdLabelRes = R.string.blind_tts_matched_confirm_threshold,
            countdownLabelRes = R.string.blind_tts_long_press_cancelled,
            // 主按钮随状态切换语义：MET → 确认汇合开始跑步；非 MET → 取消订单。
            // 两种状态都是可执行动作，故 canStartGesture 恒 true。
            onCountdownCommitted = {
                if (viewModel.uiState.value.currentStatus == RunRequestStatus.MET) {
                    viewModel.executeConfirmMet()
                } else {
                    viewModel.cancelByUser()
                }
            },
            onGestureStart = { viewModel.onLongPressStarted() },
        )
    }

    /**
     * 已匹配页：CONFIRM 在 ViewModel 内部根据状态分支决定是否真 startRun；
     * CANCEL 仅在非 MET 状态时允许（MET 后服务端不允许 cancel）。
     */
    private fun setupVoiceCommands() = bindVoiceCommands { cmd ->
        when (cmd) {
            VoiceCommand.CONFIRM -> { viewModel.executeConfirmMet(); true }
            VoiceCommand.CANCEL -> {
                if (viewModel.uiState.value.currentStatus != RunRequestStatus.MET) {
                    viewModel.cancelByUser()
                }
                true
            }
            VoiceCommand.STATUS -> { viewModel.announceCurrentStatus(); true }
            else -> false
        }
    }

    /**
     * 返回键分支：
     * - MET 状态（已汇合）：服务端禁止 cancel，直接返回首页 + TTS 提示后台继续；
     * - 非 MET 状态：弹全屏长按确认页取消订单；短按"继续等待"留在此页。
     * 最小化/返回首页可通过语音指令"返回首页"实现。
     */
    private fun setupBackPressInterception() {
        setFragmentResultListener(REQ_KEY_CANCEL) { _, bundle ->
            if (bundle.getBoolean(BlindConfirmDialogFragment.KEY_CONFIRMED)) {
                viewModel.cancelByUser()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val isMet = viewModel.uiState.value.currentStatus == RunRequestStatus.MET
                    if (isMet) {
                        // TTS 接力：避免本页 onPause→ttsManager.release()→engine.stop() 吞掉提示
                        (activity as? BlindActivity)?.navigateToHomeWithTts(
                            R.string.blind_tts_minimized_to_home,
                        )
                    } else {
                        BlindConfirmDialogFragment.newInstance(
                            requestKey = REQ_KEY_CANCEL,
                            titleRes = R.string.interrupt_title_leave_matched,
                            messageRes = R.string.interrupt_message_leave_matched,
                            primaryLabelRes = R.string.interrupt_btn_cancel_order,
                            primaryHintRes = R.string.blind_hint_cancel_order_long_press,
                            thresholdLabelRes = R.string.blind_tts_cancel_order_threshold,
                            cancelledLabelRes = R.string.blind_tts_long_press_cancelled,
                            secondaryLabelRes = R.string.interrupt_btn_stay,
                            hostPageTitleRes = R.string.matched_title,
                        ).show(parentFragmentManager, REQ_KEY_CANCEL)
                    }
                }
            },
        )
    }

    private suspend fun collectUiState() {
        viewModel.uiState.collect { state ->
            (activity as? BaseBlindActivity)?.activeCallPeerPhone = state.peerPhone

            binding.tvVolunteerName.text = if (state.volunteerName.isNotEmpty())
                getString(R.string.matched_volunteer_name, state.volunteerName)
            else ""

            binding.tvVolunteerRating.text = if (state.volunteerRating != null)
                getString(R.string.matched_volunteer_rating, state.volunteerRating, state.volunteerTotalRuns)
            else
                getString(R.string.matched_volunteer_rating_none, state.volunteerTotalRuns)

            // tv_status 仅在有内容时显示（assertive 关键事件），空文本时 GONE 避免占用空间
            if (state.statusText.isNotBlank()) {
                binding.tvStatus.text = state.statusText
                binding.tvStatus.visibility = View.VISIBLE
            } else {
                binding.tvStatus.visibility = View.GONE
            }

            // ★ 两个页面：未到达="志愿者已接单"+取消订单按钮；已到达(MET)="志愿者已到达"+确认汇合按钮
            val isMet = state.currentStatus == RunRequestStatus.MET
            binding.header.title = getString(
                if (isMet) R.string.matched_title_arrived else R.string.matched_title
            )
            if (isMet) {
                binding.footer.primaryGesture.text = getString(R.string.blind_ui_matched_btn_confirm)
                binding.tvActionHint.setText(R.string.blind_hint_matched_confirm)
                binding.footer.primaryGesture.contentDescription =
                    getString(R.string.blind_hint_matched_confirm)
                binding.footer.primaryGesture.setThresholdLabel(R.string.blind_tts_matched_confirm_threshold)
            } else {
                binding.footer.primaryGesture.text = getString(R.string.blind_ui_matched_btn_cancel_order)
                binding.tvActionHint.setText(R.string.blind_hint_cancel_order_long_press)
                binding.footer.primaryGesture.contentDescription =
                    getString(R.string.blind_hint_cancel_order_long_press)
                binding.footer.primaryGesture.setThresholdLabel(R.string.blind_tts_cancel_order_threshold)
            }
        }
    }

    private suspend fun collectNavEvents() {
        viewModel.navEvent.collect { event ->
            when (event) {
                is MatchedNavEvent.ToHome -> {
                    val act = activity as? BlindActivity
                    if (event.reasonRes != null) {
                        act?.navigateToHomeWithTts(event.reasonRes)
                    } else {
                        act?.navigateToHome()
                    }
                }
                is MatchedNavEvent.ToWaitingMatch -> {
                    // 接力 TTS：让 WaitingMatchFragment.onResume 内消费并 speak HIGH
                    event.reasonRes?.let {
                        (activity as? BlindActivity)?.setPendingWaitingTts(it)
                    }
                    val nc = findNavController()
                    // 优先 pop 回栈中已有的 WaitingMatchFragment，复用其 elapsed 计时；
                    // 若不在栈中（如订单恢复直接进 Matched），fallback 走 navigate 新建实例。
                    val popped = nc.popBackStack(R.id.blindWaitingMatchFragment, /* inclusive = */ false)
                    if (!popped) {
                        val args = Bundle().apply { putString("requestId", event.requestId) }
                        nc.navigate(R.id.action_matched_to_waitingMatch, args)
                    }
                }
                is MatchedNavEvent.ToRunning -> {
                    val args = Bundle().apply { putString("requestId", event.requestId) }
                    findNavController().navigate(R.id.action_matched_to_running, args)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onScreenResumed()
        (activity as? BaseBlindActivity)?.apply {
            activeRequestId = viewModel.requestId
            activeCallPeerPhone = viewModel.uiState.value.peerPhone
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.onScreenPaused()
        binding.footer.primaryGesture.reset()
        (activity as? BaseBlindActivity)?.apply {
            activeRequestId = null
            activeCallPeerPhone = null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val REQ_KEY_CANCEL = "matched_cancel_confirm"
    }
}
