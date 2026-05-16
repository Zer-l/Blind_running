package com.guiderun.app.ui.blind

import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.MotionEvent
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
import com.guiderun.app.accessibility.voice.VoiceCommand
import com.guiderun.app.accessibility.voice.bindVoiceCommands
import com.guiderun.app.databinding.FragmentWaitingMatchBinding
import com.guiderun.app.ui.common.showInterruptDialog
import com.guiderun.app.util.EdgeToEdgeHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class WaitingMatchFragment : Fragment() {

    private val viewModel: WaitingMatchViewModel by viewModels()
    private var _binding: FragmentWaitingMatchBinding? = null
    private val binding get() = _binding!!

    private var pressStartTime = 0L
    private var pressThresholdJob: Job? = null

    /** 当前手势是否已在 ACTION_DOWN 时被消费（倒计时进行中按下=立即撤销）。 */
    private var gestureConsumedByCountdownDismiss = false

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
        setupBackPressInterception()
        setupVoiceCommands()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { collectUiState() }
                launch { collectNavEvents() }
            }
        }
    }

    /** 等待匹配页只暴露"取消订单"语义；CONFIRM 在此页无意义不接管。 */
    private fun setupVoiceCommands() = bindVoiceCommands { cmd ->
        when (cmd) {
            VoiceCommand.CANCEL -> { viewModel.onCancelPressed(); true }
            else -> false
        }
    }

    /**
     * 返回键：弹「取消订单/留在此页/最小化」对话框。
     * 「取消订单」复用现有长按取消逻辑（5 秒倒数 + TTS），保持一致性。
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
                        onCancel = { viewModel.onCancelPressed() },
                        stayLabel = getString(R.string.interrupt_btn_stay),
                        homeLabel = getString(R.string.interrupt_btn_back_home),
                        onHome = { (activity as? BlindActivity)?.navigateToHome() },
                    )
                }
            },
        )
    }

    private fun onGestureEvent(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // 倒计时进行中：任何按下都解读为"撤销"，不再启动阈值 Job、不播报等待时长
                if (viewModel.uiState.value.cancelCountdown != null) {
                    gestureConsumedByCountdownDismiss = true
                    viewModel.onCancelPressed() // toggle → 撤销倒计时
                    return
                }
                gestureConsumedByCountdownDismiss = false
                pressStartTime = SystemClock.elapsedRealtime()
                pressThresholdJob?.cancel()
                pressThresholdJob = viewLifecycleOwner.lifecycleScope.launch {
                    delay(2_000)
                    viewModel.onLongPressThreshold2s()
                }
            }
            MotionEvent.ACTION_UP -> {
                if (gestureConsumedByCountdownDismiss) {
                    gestureConsumedByCountdownDismiss = false
                    return
                }
                pressThresholdJob?.cancel()
                pressThresholdJob = null
                val elapsed = SystemClock.elapsedRealtime() - pressStartTime
                when {
                    elapsed >= 2_000 -> viewModel.onLongPressCancel()
                    else -> viewModel.onShortPressHint()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                gestureConsumedByCountdownDismiss = false
                pressThresholdJob?.cancel()
                pressThresholdJob = null
            }
        }
    }

    private suspend fun collectUiState() {
        viewModel.uiState.collect { state ->
            val minutes = state.elapsedSeconds / 60
            val seconds = state.elapsedSeconds % 60
            binding.tvElapsed.text = getString(R.string.waiting_match_elapsed, minutes, seconds)

            binding.tvWaitingMessage.text = state.waitingMessage

            binding.tvCancelStatus.text = when {
                state.isCancelling -> getString(R.string.loading)
                state.cancelCountdown != null ->
                    getString(R.string.waiting_match_btn_cancel_countdown, state.cancelCountdown)
                else -> ""
            }

            // ★ contentDescription 设为等待时长，防止 TalkBack 播报"长按取消请求"
            binding.tvCancelStatus.contentDescription = when {
                state.isCancelling -> getString(R.string.loading)
                state.cancelCountdown != null ->
                    getString(R.string.waiting_match_btn_cancel_countdown, state.cancelCountdown)
                else -> {
                    if (minutes > 0)
                        "已等待${minutes}分${seconds}秒，正在等待志愿者接单"
                    else
                        "已等待${seconds}秒，正在等待志愿者接单"
                }
            }
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
        (activity as? BaseBlindActivity)?.apply {
            activeRequestId = viewModel.requestId
            touchEventForwarder = ::onGestureEvent
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.onScreenPaused()
        pressThresholdJob?.cancel()
        (activity as? BaseBlindActivity)?.apply {
            activeRequestId = null
            touchEventForwarder = null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
