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
import com.guiderun.app.BuildConfig
import com.guiderun.app.R
import com.guiderun.app.accessibility.ThreeFingerTapDetector
import com.guiderun.app.databinding.FragmentBlindRunningBinding
import com.guiderun.app.service.VoiceCallManager
import com.guiderun.app.util.PaceCalculator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BlindRunningFragment : Fragment() {

    private val viewModel: BlindRunningViewModel by viewModels()
    private var _binding: FragmentBlindRunningBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var voiceCallManager: VoiceCallManager

    private var pressStartTime = 0L
    private var savedBrightness: Float = -1f
    private var threeFingerTapDetector: ThreeFingerTapDetector? = null

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
        threeFingerTapDetector = ThreeFingerTapDetector { onThreeFingerTap() }
        (activity as? BaseBlindActivity)?.apply {
            onSingleShakeCallback = { viewModel.announceCurrentStatus() }
            activeRequestId = viewModel.requestId
            touchEventForwarder = ::onGestureEvent
        }
    }

    override fun onPause() {
        super.onPause()
        setScreenDim(false)
        viewModel.onScreenPaused()
        threeFingerTapDetector = null
        (activity as? BaseBlindActivity)?.apply {
            onSingleShakeCallback = null
            activeRequestId = null
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

    private fun onThreeFingerTap() {
        if (BuildConfig.VOICE_CALL_ENABLED) {
            viewModel.initiateCall(voiceCallManager)
        } else {
            viewModel.speakCallUnavailable()
        }
    }

    private fun onGestureEvent(event: MotionEvent) {
        threeFingerTapDetector?.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressStartTime = SystemClock.elapsedRealtime()
            }
            MotionEvent.ACTION_UP -> {
                val elapsed = SystemClock.elapsedRealtime() - pressStartTime
                if (elapsed >= 3_000) {
                    viewModel.executeEndRun()
                }
            }
        }
    }

    private suspend fun collectUiState() {
        viewModel.uiState.collect { state ->
            binding.tvDistance.text = "%.2f".format(state.totalDistanceMeters / 1000.0)
            binding.tvDuration.text = formatDuration(state.totalDurationSeconds)
            binding.tvPace.text = state.currentPaceSeconds?.let { PaceCalculator.formatPace(it) } ?: "--'--\""

            val countdown = state.endCountdown
            binding.tvStatus.text = if (countdown != null) {
                getString(R.string.blind_running_end_confirm)
            } else {
                getString(R.string.blind_running_status_running)
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
