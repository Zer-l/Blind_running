package com.guiderun.app.ui.blind

import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.guiderun.app.R
import com.guiderun.app.databinding.FragmentBlindReviewBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BlindReviewFragment : Fragment() {

    private val viewModel: BlindReviewViewModel by viewModels()
    private var _binding: FragmentBlindReviewBinding? = null
    private val binding get() = _binding!!

    private var longPressJob: Job? = null
    private var lastTapTimeMs: Long = 0L

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

        setFragmentResultListener(VoiceRecordFragment.REQUEST_KEY) { _, bundle ->
            val voiceUrl = bundle.getString(VoiceRecordFragment.KEY_VOICE_URL)
            viewModel.submitWithVoice(voiceUrl)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { collectUiState() }
                launch { collectNavEvents() }
            }
        }
    }

    private fun onGestureEvent(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Long press 3s → unsatisfied
                longPressJob?.cancel()
                longPressJob = viewLifecycleOwner.lifecycleScope.launch {
                    delay(3_000)
                    viewModel.setUnsatisfied()
                }
            }
            MotionEvent.ACTION_UP -> {
                longPressJob?.cancel()
                longPressJob = null
                // Double tap → satisfied
                val now = SystemClock.elapsedRealtime()
                if (now - lastTapTimeMs < 400) {
                    viewModel.setSatisfied()
                }
                lastTapTimeMs = now
            }
            MotionEvent.ACTION_CANCEL -> {
                longPressJob?.cancel()
                longPressJob = null
            }
        }
    }

    private suspend fun collectUiState() {
        viewModel.uiState.collect { state ->
            val km = state.distanceMeters / 1000.0
            val min = state.durationSeconds / 60
            binding.tvSummary.text = "%.1f 公里 · %d 分钟".format(km, min)
        }
    }

    private suspend fun collectNavEvents() {
        viewModel.navEvent.collect { event ->
            when (event) {
                BlindReviewNavEvent.ToHome ->
                    (activity as? BlindActivity)?.navigateToHome()
                BlindReviewNavEvent.ToVoiceRecord ->
                    findNavController().navigate(R.id.action_blindReview_to_voiceRecord)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onScreenResumed()
        (activity as? BaseBlindActivity)?.apply {
            activeRequestId = null
            onSingleShakeCallback = { viewModel.announceSummary() }
            touchEventForwarder = ::onGestureEvent
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.onScreenPaused()
        longPressJob?.cancel()
        (activity as? BaseBlindActivity)?.apply {
            touchEventForwarder = null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
