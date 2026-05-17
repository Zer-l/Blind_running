package com.guiderun.app.ui.blind

import android.os.Bundle
import android.view.LayoutInflater
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
import com.guiderun.app.accessibility.HapticFeedback
import com.guiderun.app.accessibility.TtsManager
import com.guiderun.app.accessibility.voice.VoiceCommand
import com.guiderun.app.accessibility.voice.bindVoiceCommands
import com.guiderun.app.databinding.FragmentBlindReviewBinding
import com.guiderun.app.ui.common.showInterruptDialog
import com.guiderun.app.util.EdgeToEdgeHelper
import com.guiderun.app.util.resolveThemeColor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

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

    /** 返回键：弹「确定离开（不评价）/继续评价」对话框。 */
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
