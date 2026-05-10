package com.guiderun.app.ui.blind

import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.guiderun.app.R
import com.guiderun.app.databinding.FragmentMatchedBinding
import com.guiderun.app.util.EdgeToEdgeHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MatchedFragment : Fragment() {

    private val viewModel: MatchedViewModel by viewModels()
    private var _binding: FragmentMatchedBinding? = null
    private val binding get() = _binding!!

    private var pressStartTime = 0L
    private var pressThresholdJob: Job? = null

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
                pressStartTime = SystemClock.elapsedRealtime()
                pressThresholdJob?.cancel()
                pressThresholdJob = viewLifecycleOwner.lifecycleScope.launch {
                    delay(1_000)
                    viewModel.onLongPressThreshold1s()
                    delay(2_000)
                    viewModel.onLongPressThreshold3s()
                }
            }
            MotionEvent.ACTION_UP -> {
                pressThresholdJob?.cancel()
                pressThresholdJob = null
                val elapsed = SystemClock.elapsedRealtime() - pressStartTime
                when {
                    elapsed >= 3_000 -> viewModel.onReleasePressed()
                    elapsed >= 1_000 -> viewModel.onConfirmAccept()
                    else -> viewModel.onShortPress()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                pressThresholdJob?.cancel()
                pressThresholdJob = null
            }
        }
    }

    private suspend fun collectUiState() {
        viewModel.uiState.collect { state ->
            // peer phone 异步加载完成后实时同步给 Activity，供音量+键拨号使用
            (activity as? BaseBlindActivity)?.activeCallPeerPhone = state.peerPhone
            binding.tvVolunteerName.text = if (state.volunteerName.isNotEmpty())
                getString(R.string.matched_volunteer_name, state.volunteerName)
            else ""

            binding.tvVolunteerRating.text = if (state.volunteerRating != null)
                getString(R.string.matched_volunteer_rating, state.volunteerRating, state.volunteerTotalRuns)
            else ""

            binding.tvStatus.text = state.statusText
        }
    }

    private suspend fun collectNavEvents() {
        viewModel.navEvent.collect { event ->
            when (event) {
                is MatchedNavEvent.ToWaiting -> {
                    val args = Bundle().apply { putString("requestId", event.requestId) }
                    findNavController().navigate(R.id.action_matched_to_waiting, args)
                }
                MatchedNavEvent.ToHome ->
                    (activity as? BlindActivity)?.navigateToHome()
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
            touchEventForwarder = ::onGestureEvent
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.onScreenPaused()
        pressThresholdJob?.cancel()
        (activity as? BaseBlindActivity)?.apply {
            activeRequestId = null
            activeCallPeerPhone = null
            touchEventForwarder = null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
