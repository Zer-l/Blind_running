package com.guiderun.app.ui.blind

import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.card.MaterialCardView
import com.guiderun.app.R
import com.guiderun.app.accessibility.voice.VoiceCommand
import com.guiderun.app.accessibility.voice.bindVoiceCommands
import com.guiderun.app.databinding.FragmentBlindReviewBinding
import com.guiderun.app.ui.common.showInterruptDialog
import com.guiderun.app.util.EdgeToEdgeHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BlindReviewFragment : Fragment() {

    private val viewModel: BlindReviewViewModel by viewModels()
    private var _binding: FragmentBlindReviewBinding? = null
    private val binding get() = _binding!!

    private var ratingCards: List<MaterialCardView> = emptyList()

    private var pressStartTime = 0L
    private var pressThresholdJob: Job? = null
    private var gestureConsumedByCountdownDismiss = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentBlindReviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        EdgeToEdgeHelper.applyInsets(view)
        setupBackPressInterception()

        ratingCards = listOf(
            binding.cardRating1,
            binding.cardRating2,
            binding.cardRating3,
            binding.cardRating4,
            binding.cardRating5,
        )
        ratingCards.forEachIndexed { index, card ->
            // 卡片单击仅选择评分，不提交；提交由 dispatchTouchEvent 双击触发
            card.setOnClickListener { viewModel.selectRating(index + 1) }
        }

        setupVoiceCommands()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { collectUiState() }
                launch { collectNavEvents() }
            }
        }
    }

    /** 评价页：RATE_x 选择 + CONFIRM 提交 + SKIP 跳过 */
    private fun setupVoiceCommands() = bindVoiceCommands { cmd ->
        when (cmd) {
            VoiceCommand.RATE_1 -> { viewModel.selectRating(1); true }
            VoiceCommand.RATE_2 -> { viewModel.selectRating(2); true }
            VoiceCommand.RATE_3 -> { viewModel.selectRating(3); true }
            VoiceCommand.RATE_4 -> { viewModel.selectRating(4); true }
            VoiceCommand.RATE_5 -> { viewModel.selectRating(5); true }
            VoiceCommand.CONFIRM, VoiceCommand.SAVE -> { viewModel.onSubmitPressed(); true }
            VoiceCommand.SKIP, VoiceCommand.CANCEL -> { viewModel.skip(); true }
            else -> false
        }
    }

    private suspend fun collectUiState() {
        viewModel.uiState.collect { state ->
            (activity as? BaseBlindActivity)?.activeCallPeerPhone = state.peerPhone
            binding.tvSummary.text = getString(
                R.string.blind_review_summary_format,
                state.distanceMeters / 1000.0,
                state.durationSeconds / 60,
            )
            updateCardSelection(state.selectedRating)
        }
    }

    private suspend fun collectNavEvents() {
        viewModel.navEvent.collect { event ->
            when (event) {
                BlindReviewNavEvent.ToHome -> (activity as? BlindActivity)?.navigateToHome()
            }
        }
    }

    private fun updateCardSelection(selected: Int) {
        val ctx = requireContext()
        val selectedStroke = ContextCompat.getColor(ctx, R.color.blind_primary)
        val unselectedStroke = ContextCompat.getColor(ctx, R.color.blind_on_surface)
        val selectedBg = ContextCompat.getColor(ctx, R.color.blind_primary)
        val unselectedBg = ContextCompat.getColor(ctx, R.color.blind_surface)
        val selectedText = ContextCompat.getColor(ctx, R.color.blind_on_primary)
        val unselectedText = ContextCompat.getColor(ctx, R.color.blind_on_surface)

        ratingCards.forEachIndexed { index, card ->
            val rating = index + 1
            val isSelected = rating == selected
            card.setCardBackgroundColor(if (isSelected) selectedBg else unselectedBg)
            card.strokeColor = if (isSelected) selectedStroke else unselectedStroke
            card.strokeWidth = if (isSelected) STROKE_SELECTED_DP else STROKE_UNSELECTED_DP
            (card.getChildAt(0) as? android.widget.TextView)?.setTextColor(
                if (isSelected) selectedText else unselectedText,
            )
        }
    }

    /**
     * 全局触摸（仅响应空白区域；卡片自身 onClick 已消费 DOWN，不会落到这里）：
     * - 短按 < 2s → 朗读当前选中评分
     * - 长按 ≥ 2s 松手 → 启动 5s 倒计时提交评分
     * - 倒计时进行中按下 → 撤销
     * 跳过评价改走返回键弹窗 / 语音 SKIP 指令。
     */
    private fun onGestureEvent(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (viewModel.uiState.value.submitCountdown != null) {
                    gestureConsumedByCountdownDismiss = true
                    viewModel.onSubmitPressed() // toggle 撤销
                    return
                }
                gestureConsumedByCountdownDismiss = false
                pressStartTime = SystemClock.elapsedRealtime()
                pressThresholdJob?.cancel()
                pressThresholdJob = viewLifecycleOwner.lifecycleScope.launch {
                    delay(2_000)
                    viewModel.onLongPressThresholdSubmit()
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
                if (elapsed >= 2_000) viewModel.onSubmitPressed()
                else viewModel.onShortPressHint()
            }
            MotionEvent.ACTION_CANCEL -> {
                gestureConsumedByCountdownDismiss = false
                pressThresholdJob?.cancel()
                pressThresholdJob = null
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onScreenResumed()
        (activity as? BaseBlindActivity)?.apply {
            activeRequestId = null
            activeCallPeerPhone = viewModel.uiState.value.peerPhone
            touchEventForwarder = ::onGestureEvent
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.onScreenPaused()
        pressThresholdJob?.cancel()
        pressThresholdJob = null
        (activity as? BaseBlindActivity)?.apply {
            activeCallPeerPhone = null
            touchEventForwarder = null
        }
    }

    /**
     * 返回键：弹「确定离开（不评价）/继续评价」对话框。
     * 用户选择离开 → 调 viewModel.skip()，复用现有跳过评价逻辑。
     */
    private fun setupBackPressInterception() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    showInterruptDialog(
                        activity = requireActivity(),
                        title = getString(R.string.interrupt_title_leave_review),
                        message = getString(R.string.interrupt_message_leave_review),
                        cancelLabel = getString(R.string.interrupt_btn_leave),
                        onCancel = { viewModel.skip() },
                        stayLabel = getString(R.string.interrupt_btn_continue_review),
                    )
                }
            },
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        ratingCards = emptyList()
        _binding = null
    }

    private companion object {
        const val STROKE_SELECTED_DP = 6
        const val STROKE_UNSELECTED_DP = 1
    }
}
