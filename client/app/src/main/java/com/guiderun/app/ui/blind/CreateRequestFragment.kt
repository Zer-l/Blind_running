package com.guiderun.app.ui.blind

import android.Manifest
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.guiderun.app.R
import com.guiderun.app.accessibility.BlindFeedback
import com.guiderun.app.accessibility.HapticFeedback
import com.guiderun.app.accessibility.SpeechRecognizerManager
import com.guiderun.app.accessibility.TtsManager
import com.guiderun.app.accessibility.voice.VoiceCommand
import com.guiderun.app.accessibility.voice.bindVoiceCommands
import com.guiderun.app.databinding.FragmentCreateRequestBinding
import com.guiderun.app.ui.common.showInterruptDialog
import com.guiderun.app.util.AppPermissions
import com.guiderun.app.util.EdgeToEdgeHelper
import com.guiderun.app.util.PermissionHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CreateRequestFragment : Fragment() {

    private val viewModel: CreateRequestViewModel by viewModels()
    private var _binding: FragmentCreateRequestBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var blindFeedback: BlindFeedback
    @Inject lateinit var ttsManager: TtsManager
    @Inject lateinit var hapticFeedback: HapticFeedback

    private lateinit var permissionHelper: PermissionHelper
    private var permissionChecked = false

    private var voiceInputManager: SpeechRecognizerManager? = null
    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startVoiceInputForNotes()
        else blindFeedback.permissionDenied(R.string.voice_input_permission_denied)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionHelper = PermissionHelper(this) { allGranted, _ ->
            if (allGranted) {
                viewModel.startLocationUpdates()
            } else {
                viewModel.onLocationPermissionDenied()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentCreateRequestBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        EdgeToEdgeHelper.applyInsets(view)
        setupDurationToggle()
        setupListeners()
        setupGestureFooter()
        setupEditResultListener()
        setupBackPressInterception()
        setupVoiceCommands()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { collectUiState() }
                launch { collectNavEvents() }
            }
        }

        // 一键发起入口：HomeScreen 长按 2s 触发，跳过手势确认直接用上次偏好提交
        if (arguments?.getBoolean(BlindActivity.EXTRA_QUICK_START) == true) {
            arguments?.remove(BlindActivity.EXTRA_QUICK_START)
            viewModel.submitWithLastPrefs()
        }
    }

    private fun setupGestureFooter() {
        binding.footer.primaryGesture.bind(
            scope = viewLifecycleOwner.lifecycleScope,
            ttsManager = ttsManager,
            hapticFeedback = hapticFeedback,
            thresholdLabelRes = R.string.blind_tts_create_request_threshold,
            countdownLabelRes = R.string.blind_tts_long_press_cancelled,
            onCountdownCommitted = { viewModel.submit() },
        )
        binding.footer.secondaryButton.setOnClickListener { navigateToEditRequest() }
    }

    private fun setupVoiceCommands() = bindVoiceCommands { cmd ->
        when (cmd) {
            VoiceCommand.CONFIRM, VoiceCommand.SAVE -> {
                // 语音指令为明确意图，跳过 2s+5s 渐进确认，直接提交
                viewModel.submit()
                true
            }
            VoiceCommand.CANCEL -> {
                binding.footer.primaryGesture.reset()
                viewModel.onBackRequested()
                true
            }
            VoiceCommand.MODIFY_REQUEST -> {
                navigateToEditRequest()
                true
            }
            VoiceCommand.DURATION_30 -> { viewModel.onDurationSelected(30); true }
            VoiceCommand.DURATION_60 -> { viewModel.onDurationSelected(60); true }
            VoiceCommand.DURATION_90 -> { viewModel.onDurationSelected(90); true }
            VoiceCommand.DURATION_120 -> { viewModel.onDurationSelected(120); true }
            else -> false
        }
    }

    private fun setupBackPressInterception() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    showInterruptDialog(
                        activity = requireActivity(),
                        title = getString(R.string.interrupt_title_leave_create),
                        message = getString(R.string.interrupt_message_leave_create),
                        cancelLabel = getString(R.string.interrupt_btn_discard),
                        onCancel = { requireActivity().finish() },
                        stayLabel = getString(R.string.interrupt_btn_stay),
                    )
                }
            },
        )
    }

    private fun setupDurationToggle() {
        binding.toggleDuration.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val minutes = when (checkedId) {
                R.id.btn_30min -> 30
                R.id.btn_60min -> 60
                R.id.btn_90min -> 90
                R.id.btn_120min -> 120
                else -> 30
            }
            if (minutes != viewModel.uiState.value.selectedDurationMinutes) {
                viewModel.onDurationSelected(minutes)
            }
        }
    }

    private fun setupEditResultListener() {
        setFragmentResultListener("edit_request_result") { _, bundle ->
            val newLat = if (bundle.containsKey("lat")) bundle.getDouble("lat") else null
            val newLng = if (bundle.containsKey("lng")) bundle.getDouble("lng") else null
            viewModel.onEditRequestResult(
                durationMinutes = bundle.getInt("durationMinutes", 60),
                locationDescription = bundle.getString("locationDescription", "当前位置")
                    ?: "当前位置",
                notes = bundle.getString("notes", "") ?: "",
                newLat = newLat,
                newLng = newLng,
            )
        }
    }

    private fun setupListeners() {
        binding.etNotes.doAfterTextChanged { text ->
            viewModel.onNotesChanged(text?.toString() ?: "")
        }
        binding.header.setOnClickListener { viewModel.onRetryLocation() }
        binding.tilNotes.setEndIconOnClickListener { onMicClicked() }
    }

    private fun onMicClicked() {
        if (permissionHelper.isGranted(requireContext(), AppPermissions.RECORD_AUDIO)) {
            startVoiceInputForNotes()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startVoiceInputForNotes() {
        val manager = voiceInputManager ?: SpeechRecognizerManager(
            context = requireContext(),
            onResult = { text ->
                val current = binding.etNotes.text?.toString().orEmpty()
                val merged = if (current.isBlank()) text else "$current $text"
                binding.etNotes.setText(merged)
                binding.etNotes.setSelection(merged.length)
            },
            onError = { msg -> blindFeedback.error(msg) },
            onStartListening = { blindFeedback.info(R.string.voice_input_listening) },
        ).also { voiceInputManager = it }
        if (!manager.isAvailable) {
            blindFeedback.warning(R.string.voice_input_unavailable)
            return
        }
        manager.start()
    }

    private fun navigateToEditRequest() {
        val state = viewModel.uiState.value
        val args = bundleOf(
            "durationMinutes" to state.selectedDurationMinutes,
            "locationDescription" to state.locationDescription,
            "notes" to state.notes,
        )
        findNavController().navigate(R.id.action_createRequest_to_editRequest, args)
    }

    private suspend fun collectUiState() {
        viewModel.uiState.collect { state ->
            val expectedButtonId = when (state.selectedDurationMinutes) {
                30 -> R.id.btn_30min
                60 -> R.id.btn_60min
                90 -> R.id.btn_90min
                120 -> R.id.btn_120min
                else -> R.id.btn_30min
            }
            if (binding.toggleDuration.checkedButtonId != expectedButtonId) {
                binding.toggleDuration.check(expectedButtonId)
            }

            val currentNotes = binding.etNotes.text?.toString() ?: ""
            if (currentNotes != state.notes) {
                binding.etNotes.setText(state.notes)
            }

            if (state.errorMessage != null) {
                binding.tvError.text = state.errorMessage
                binding.tvError.visibility = View.VISIBLE
            } else {
                binding.tvError.visibility = View.GONE
            }

            // 提交进行中：主按钮临时禁用，文字提示
            binding.footer.primaryGesture.isEnabled = !state.isSubmitting
            if (state.isSubmitting) {
                binding.footer.primaryGesture.text =
                    getString(R.string.create_request_submitting)
            } else {
                binding.footer.primaryGesture.text =
                    getString(R.string.blind_ui_create_request_btn_primary)
            }
        }
    }

    private suspend fun collectNavEvents() {
        viewModel.navEvent.collect { event ->
            when (event) {
                is CreateRequestNavEvent.ToWaitingMatch -> {
                    val args = bundleOf("requestId" to event.requestId)
                    findNavController().navigate(
                        R.id.action_createRequest_to_waitingMatch,
                        args,
                    )
                }
                CreateRequestNavEvent.Back -> requireActivity().finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onScreenResumed()

        if (!permissionChecked) {
            permissionChecked = true
            if (permissionHelper.areGranted(requireContext(), *AppPermissions.LOCATION)) {
                viewModel.startLocationUpdates()
            } else {
                permissionHelper.request(*AppPermissions.LOCATION)
            }
        } else if (viewModel.uiState.value.locationStatus is LocationStatus.Failed) {
            viewModel.onRetryLocation()
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.onScreenPaused()
        binding.footer.primaryGesture.reset()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        voiceInputManager?.destroy()
        voiceInputManager = null
        _binding = null
    }
}
