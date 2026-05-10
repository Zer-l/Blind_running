package com.guiderun.app.ui.blind

import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.card.MaterialCardView
import com.guiderun.app.R
import com.guiderun.app.databinding.FragmentBlindReviewBinding
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

    private var longPressJob: Job? = null
    private var lastTapMs: Long = 0L

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

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { collectUiState() }
                launch { collectNavEvents() }
            }
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
     * 全局触摸：
     * - 双击屏幕（两次 ACTION_DOWN < DOUBLE_TAP_INTERVAL）→ 提交当前选中评分
     * - 单次 ACTION_DOWN 持续 LONG_PRESS_MS 未抬起 → 跳过评价
     * 卡片自身的 onClick 仅负责选中，不在此处理。
     */
    private fun onGestureEvent(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val now = SystemClock.elapsedRealtime()
                if (now - lastTapMs in 1 until DOUBLE_TAP_INTERVAL_MS) {
                    lastTapMs = 0L
                    longPressJob?.cancel()
                    longPressJob = null
                    viewModel.submitSelected()
                    return
                }
                lastTapMs = now
                longPressJob?.cancel()
                longPressJob = viewLifecycleOwner.lifecycleScope.launch {
                    delay(LONG_PRESS_MS)
                    viewModel.skip()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                longPressJob?.cancel()
                longPressJob = null
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
        longPressJob?.cancel()
        longPressJob = null
        (activity as? BaseBlindActivity)?.apply {
            activeCallPeerPhone = null
            touchEventForwarder = null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        ratingCards = emptyList()
        _binding = null
    }

    private companion object {
        const val DOUBLE_TAP_INTERVAL_MS = 400L
        const val LONG_PRESS_MS = 3_000L
        const val STROKE_SELECTED_DP = 6
        const val STROKE_UNSELECTED_DP = 1
    }
}
