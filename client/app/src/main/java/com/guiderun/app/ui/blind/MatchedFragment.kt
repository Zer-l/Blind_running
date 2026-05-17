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
import com.guiderun.app.R
import com.guiderun.app.accessibility.HapticFeedback
import com.guiderun.app.accessibility.TtsManager
import com.guiderun.app.accessibility.voice.VoiceCommand
import com.guiderun.app.accessibility.voice.bindVoiceCommands
import com.guiderun.app.databinding.FragmentMatchedBinding
import com.guiderun.app.domain.model.RunRequestStatus
import com.guiderun.app.ui.common.showInterruptDialog
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
            onCountdownCommitted = { viewModel.executeConfirmMet() },
        )
        // 初始 disabled：仅 MET 状态会启用，由 collectUiState 控制
        binding.footer.primaryGesture.isEnabled = false
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

    private fun setupBackPressInterception() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val status = viewModel.uiState.value.currentStatus
                    val isMet = status == RunRequestStatus.MET
                    showInterruptDialog(
                        activity = requireActivity(),
                        title = getString(
                            if (isMet) R.string.interrupt_title_leave_met
                            else R.string.interrupt_title_leave_matched
                        ),
                        message = getString(
                            if (isMet) R.string.interrupt_message_leave_met
                            else R.string.interrupt_message_leave_matched
                        ) + "\n" + getString(R.string.interrupt_hint_resume),
                        cancelLabel = if (isMet) null else getString(R.string.interrupt_btn_cancel_order),
                        onCancel = if (isMet) null else ({ viewModel.cancelByUser() }),
                        stayLabel = getString(R.string.interrupt_btn_stay),
                        homeLabel = getString(R.string.interrupt_btn_back_home),
                        onHome = { (activity as? BlindActivity)?.navigateToHome() },
                    )
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
            else ""

            binding.tvStatus.text = state.statusText

            // ★ footer 主按钮：仅 MET 状态可用；hint 文案动态切换
            val isMet = state.currentStatus == RunRequestStatus.MET
            binding.footer.primaryGesture.isEnabled = isMet
            binding.footer.hintText.text = getString(
                if (isMet) R.string.blind_hint_matched_confirm
                else R.string.blind_hint_matched_waiting
            )
            binding.footer.primaryGesture.contentDescription = getString(
                if (isMet) R.string.blind_hint_matched_confirm
                else R.string.blind_hint_matched_waiting
            )
        }
    }

    private suspend fun collectNavEvents() {
        viewModel.navEvent.collect { event ->
            when (event) {
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
}
