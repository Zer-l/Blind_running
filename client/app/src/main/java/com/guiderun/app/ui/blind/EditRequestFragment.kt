package com.guiderun.app.ui.blind

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.guiderun.app.R
import com.guiderun.app.accessibility.SpeechRecognizerManager
import com.guiderun.app.accessibility.voice.VoiceCommand
import com.guiderun.app.accessibility.voice.bindVoiceCommands
import com.guiderun.app.databinding.FragmentEditRequestBinding
import com.guiderun.app.util.EdgeToEdgeHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class EditRequestFragment : Fragment() {

    private val viewModel: EditRequestViewModel by viewModels()
    private var _binding: FragmentEditRequestBinding? = null
    private val binding get() = _binding!!

    private var voiceInputManager: SpeechRecognizerManager? = null
    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startVoiceInputForNotes()
        else toast(R.string.voice_input_permission_denied)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentEditRequestBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        EdgeToEdgeHelper.applyInsets(view)
        setupDurationToggle()
        setupListeners()
        setupVoiceCommands()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { collectUiState() }
                launch { collectNavEvents() }
            }
        }
    }

    private fun setupVoiceCommands() = bindVoiceCommands { cmd ->
        when (cmd) {
            VoiceCommand.SAVE, VoiceCommand.CONFIRM -> { viewModel.onSavePressed(); true }
            VoiceCommand.CANCEL -> { findNavController().popBackStack(); true }
            VoiceCommand.DURATION_30 -> { viewModel.onDurationSelected(30); true }
            VoiceCommand.DURATION_60 -> { viewModel.onDurationSelected(60); true }
            VoiceCommand.DURATION_90 -> { viewModel.onDurationSelected(90); true }
            VoiceCommand.DURATION_120 -> { viewModel.onDurationSelected(120); true }
            else -> false
        }
    }

    private fun setupDurationToggle() {
        binding.toggleDuration.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val minutes = when (checkedId) {
                R.id.btn_edit_30min -> 30   // ★ 加上
                R.id.btn_edit_60min -> 60
                R.id.btn_edit_90min -> 90
                R.id.btn_edit_120min -> 120
                else -> 60
            }
            if (minutes != viewModel.uiState.value.selectedDurationMinutes) {
                viewModel.onDurationSelected(minutes)
            }
        }
    }

    private fun setupListeners() {
        binding.etLocationDescription.doAfterTextChanged { text ->
            viewModel.onLocationDescriptionChanged(text?.toString() ?: "")
        }
        binding.etNotes.doAfterTextChanged { text ->
            viewModel.onNotesChanged(text?.toString() ?: "")
        }
        binding.btnSave.setOnClickListener { viewModel.onSavePressed() }
        binding.btnCancel.setOnClickListener { findNavController().popBackStack() }
        binding.tilNotes.setEndIconOnClickListener { onMicClicked() }
    }

    private fun onMicClicked() {
        val granted = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) startVoiceInputForNotes()
        else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
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
            onError = { msg -> toast(msg) },
            onStartListening = { toast(R.string.voice_input_listening) },
        ).also { voiceInputManager = it }
        if (!manager.isAvailable) {
            toast(R.string.voice_input_unavailable)
            return
        }
        manager.start()
    }

    private fun toast(text: String) {
        Toast.makeText(requireContext(), text, Toast.LENGTH_SHORT).show()
    }

    private fun toast(@androidx.annotation.StringRes resId: Int) {
        Toast.makeText(requireContext(), resId, Toast.LENGTH_SHORT).show()
    }

    private suspend fun collectNavEvents() {
        viewModel.navEvent.collect { event ->
            when (event) {
                is EditRequestNavEvent.SaveAndReturn -> {
                    val bundle = bundleOf(
                        "durationMinutes" to event.durationMinutes,
                        "locationDescription" to event.locationDescription,
                        "notes" to event.notes,
                    )
                    // 仅在地理编码成功时携带 lat/lng；上一页用 containsKey 判空决定是否覆盖坐标
                    if (event.lat != null && event.lng != null) {
                        bundle.putDouble("lat", event.lat)
                        bundle.putDouble("lng", event.lng)
                    }
                    setFragmentResult("edit_request_result", bundle)
                    findNavController().popBackStack()
                }
            }
        }
    }

    private suspend fun collectUiState() {
        viewModel.uiState.collect { state ->
            val expectedButtonId = when (state.selectedDurationMinutes) {
                30 -> R.id.btn_edit_30min   // ★ 加上
                60 -> R.id.btn_edit_60min
                90 -> R.id.btn_edit_90min
                120 -> R.id.btn_edit_120min
                else -> R.id.btn_edit_60min
            }
            if (binding.toggleDuration.checkedButtonId != expectedButtonId) {
                binding.toggleDuration.check(expectedButtonId)
            }

            val currentLoc = binding.etLocationDescription.text?.toString() ?: ""
            if (currentLoc != state.locationDescription) {
                binding.etLocationDescription.setText(state.locationDescription)
                binding.etLocationDescription.setSelection(state.locationDescription.length)
            }

            val currentNotes = binding.etNotes.text?.toString() ?: ""
            if (currentNotes != state.notes) {
                binding.etNotes.setText(state.notes)
            }

            binding.btnSave.isEnabled = !state.isSaving
            binding.btnSave.text = if (state.isSaving) {
                getString(R.string.edit_request_btn_saving)
            } else {
                getString(R.string.edit_request_btn_save)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onScreenResumed()
    }

    override fun onPause() {
        super.onPause()
        viewModel.onScreenPaused()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        voiceInputManager?.destroy()
        voiceInputManager = null
        _binding = null
    }
}
