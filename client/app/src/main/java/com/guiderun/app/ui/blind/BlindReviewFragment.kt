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
import com.google.android.material.card.MaterialCardView
import com.guiderun.app.R
import com.guiderun.app.accessibility.HapticFeedback
import com.guiderun.app.accessibility.TtsManager
import com.guiderun.app.accessibility.voice.VoiceCommand
import com.guiderun.app.accessibility.voice.bindVoiceCommands
import com.guiderun.app.databinding.FragmentBlindReviewBinding
import com.guiderun.app.util.EdgeToEdgeHelper
import com.guiderun.app.util.resolveThemeColor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 视障端跑步评价页 Fragment。
 *
 * 5 张评分卡（1~5 星）单击选择；主按钮长按 2s+5s 提交，次按钮跳过。
 * 从历史页进入（补评）：完成后 popBackStack 返回历史页；正常跑步结束流程：navigateToHome。
 * 返回键走 skip 路径（评价可选，跳过即可），无需二次确认弹窗。
 */
@AndroidEntryPoint
class BlindReviewFragment : Fragment() {

    private val viewModel: BlindReviewViewModel by viewModels()
    private var _binding: FragmentBlindReviewBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var ttsManager: TtsManager
    @Inject lateinit var hapticFeedback: HapticFeedback

    private var ratingCards: List<MaterialCardView> = emptyList()

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
            card.setOnClickListener { viewModel.selectRating(index + 1) }
        }

        setupGestureFooter()
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
            thresholdLabelRes = R.string.blind_tts_blind_review_submit_threshold,
            countdownLabelRes = R.string.blind_tts_long_press_cancelled,
            onCountdownCommitted = { viewModel.executeSubmit() },
        )
        binding.footer.secondaryButton.setOnClickListener { viewModel.skip() }
    }

    /** 评价页：RATE_x 选择 + CONFIRM/SAVE 提交 + SKIP/CANCEL 跳过 + STATUS 朗读当前选择。 */
    private fun setupVoiceCommands() = bindVoiceCommands { cmd ->
        when (cmd) {
            VoiceCommand.RATE_1 -> { viewModel.selectRating(1); true }
            VoiceCommand.RATE_2 -> { viewModel.selectRating(2); true }
            VoiceCommand.RATE_3 -> { viewModel.selectRating(3); true }
            VoiceCommand.RATE_4 -> { viewModel.selectRating(4); true }
            VoiceCommand.RATE_5 -> { viewModel.selectRating(5); true }
            VoiceCommand.CONFIRM, VoiceCommand.SAVE -> { viewModel.executeSubmit(); true }
            VoiceCommand.SKIP, VoiceCommand.CANCEL -> { viewModel.skip(); true }
            VoiceCommand.STATUS -> { viewModel.announceCurrentSelection(); true }
            else -> false
        }
    }

    private suspend fun collectUiState() {
        viewModel.uiState.collect { state ->
            (activity as? BaseBlindActivity)?.activeCallPeerPhone = state.peerPhone
            updateCardSelection(state.selectedRating)
        }
    }

    private suspend fun collectNavEvents() {
        viewModel.navEvent.collect { event ->
            when (event) {
                BlindReviewNavEvent.ToHome -> navigateAwayFromReview()
            }
        }
    }

    /**
     * 评价完成后的返回策略：
     * - 从历史页进入补评：popBackStack 回历史页（保留浏览上下文）
     * - 正常跑步结束流程：调 BlindActivity.navigateToHome 清栈回首页
     *
     * 通过 NavController.previousBackStackEntry 判断来源：从历史进入时上一个节点是 blindHistoryFragment；
     * 正常流程通过 action_blindRunning_to_review popUpTo blindRunningFragment inclusive=true，
     * 上一节点会是 blindCreateRequestFragment（栈中残留）或 blindHomeFragment。
     */
    private fun navigateAwayFromReview() {
        val nav = findNavController()
        val prevId = nav.previousBackStackEntry?.destination?.id
        if (prevId == R.id.blindHistoryFragment) {
            nav.popBackStack()
        } else {
            (activity as? BlindActivity)?.navigateToHome()
        }
    }

    private fun updateCardSelection(selected: Int) {
        val ctx = requireContext()
        val selectedStroke = ctx.resolveThemeColor(R.attr.blindPrimary)
        val unselectedStroke = ctx.resolveThemeColor(R.attr.blindOnSurface)
        val selectedBg = ctx.resolveThemeColor(R.attr.blindPrimary)
        val unselectedBg = ctx.resolveThemeColor(R.attr.blindSurface)
        val selectedText = ctx.resolveThemeColor(R.attr.blindOnPrimary)
        val unselectedText = ctx.resolveThemeColor(R.attr.blindOnSurface)

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

    override fun onResume() {
        super.onResume()
        viewModel.onScreenResumed()
        (activity as? BaseBlindActivity)?.apply {
            activeRequestId = null
            activeCallPeerPhone = viewModel.uiState.value.peerPhone
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.onScreenPaused()
        binding.footer.primaryGesture.reset()
        (activity as? BaseBlindActivity)?.activeCallPeerPhone = null
    }

    /**
     * 返回键：评价为可选操作（评价丢失也可在历史页补评），非破坏性。
     * 直接走 skip 路径 + TTS 反馈，无需弹窗二次确认。
     */
    private fun setupBackPressInterception() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    ttsManager.speak(
                        getString(R.string.blind_tts_review_skipped_by_back),
                        TtsManager.Priority.INTERACTION,
                    )
                    viewModel.skip()
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
