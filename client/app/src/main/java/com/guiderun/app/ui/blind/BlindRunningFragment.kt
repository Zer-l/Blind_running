package com.guiderun.app.ui.blind

import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.guiderun.app.R
import com.guiderun.app.databinding.FragmentBlindRunningBinding
import com.guiderun.app.util.EdgeToEdgeHelper
import com.guiderun.app.util.PaceCalculator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BlindRunningFragment : Fragment() {

    private val viewModel: BlindRunningViewModel by viewModels()
    private var _binding: FragmentBlindRunningBinding? = null
    private val binding get() = _binding!!

    private var pressStartTime = 0L
    private var pressThresholdJob: Job? = null
    private var savedBrightness: Float = -1f

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentBlindRunningBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        EdgeToEdgeHelper.applyInsets(view)
        view.keepScreenOn = true

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { collectUiState() }
                launch { collectNavEvents() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setScreenDim(true)
        viewModel.onScreenResumed()
        (activity as? BaseBlindActivity)?.apply {
            activeRequestId = viewModel.requestId
            activeCallPeerPhone = viewModel.uiState.value.peerPhone
            touchEventForwarder = ::onGestureEvent
        }
    }

    override fun onPause() {
        super.onPause()
        setScreenDim(false)
        viewModel.onScreenPaused()
        pressThresholdJob?.cancel()
        pressThresholdJob = null
        (activity as? BaseBlindActivity)?.apply {
            activeRequestId = null
            activeCallPeerPhone = null
            touchEventForwarder = null
        }
    }

    private fun setScreenDim(dim: Boolean) {
        val window = activity?.window ?: return
        val lp = window.attributes
        if (dim) {
            savedBrightness = lp.screenBrightness
            lp.screenBrightness = 0.01f
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            if (savedBrightness >= 0f) {
                lp.screenBrightness = savedBrightness
            } else {
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        window.attributes = lp
    }

    private fun onGestureEvent(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressStartTime = SystemClock.elapsedRealtime()
                pressThresholdJob?.cancel()
                pressThresholdJob = viewLifecycleOwner.lifecycleScope.launch {
                    delay(3_000)
                    viewModel.onLongPressThresholdEndRun()
                }
            }
            MotionEvent.ACTION_UP -> {
                pressThresholdJob?.cancel()
                pressThresholdJob = null
                val elapsed = SystemClock.elapsedRealtime() - pressStartTime
                if (elapsed >= 3_000) {
                    viewModel.executeEndRun()
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
            binding.tvDistance.text = "%.2f".format(state.totalDistanceMeters / 1000.0)
            binding.tvDuration.text = formatDuration(state.totalDurationSeconds)
            // 显示用配速，暂停时为 null → 显示 --'--"
            binding.tvPace.text = state.displayPaceSeconds?.let { PaceCalculator.formatPace(it) } ?: "--'--\""
            binding.tvPausedBadge.visibility = if (state.isPaused) View.VISIBLE else View.GONE

            val countdown = state.endCountdown
            binding.tvStatus.text = when {
                countdown != null -> getString(R.string.blind_running_end_confirm)
                state.endRequestedByVolunteer -> getString(R.string.blind_running_end_requested_by_volunteer)
                else -> getString(R.string.blind_running_status_running)
            }
        }
    }

    private suspend fun collectNavEvents() {
        viewModel.navEvent.collect { event ->
            when (event) {
                is BlindRunningNavEvent.ToReview -> {
                    val args = Bundle().apply { putString("requestId", event.requestId) }
                    findNavController().navigate(R.id.action_blindRunning_to_review, args)
                }
                BlindRunningNavEvent.ToHome ->
                    (activity as? BlindActivity)?.navigateToHome()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun formatDuration(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }
}
