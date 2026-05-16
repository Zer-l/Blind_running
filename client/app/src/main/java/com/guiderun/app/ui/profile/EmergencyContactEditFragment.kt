package com.guiderun.app.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.guiderun.app.R
import com.guiderun.app.accessibility.TtsManager
import com.guiderun.app.accessibility.voice.VoiceCommand
import com.guiderun.app.accessibility.voice.bindVoiceCommands
import com.guiderun.app.databinding.FragmentEmergencyContactEditBinding
import com.guiderun.app.util.EdgeToEdgeHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class EmergencyContactEditFragment : Fragment() {

    private var _binding: FragmentEmergencyContactEditBinding? = null
    private val binding get() = _binding!!

    private val viewModel: EmergencyContactEditViewModel by viewModels()

    @Inject
    lateinit var ttsManager: TtsManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentEmergencyContactEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        EdgeToEdgeHelper.applyInsets(view)
        ttsManager.acquire()

        val index = arguments?.getInt("index", -1) ?: -1
        if (index >= 0) {
            viewModel.initEditMode(index)
        }

        setupInputs()
        setupSaveButton()
        setupVoiceCommands()
        observeUiState()
        observeEvents()

        if (index >= 0) {
            ttsManager.speak(getString(R.string.tts_page_emergency_contact_edit_edit), TtsManager.Priority.HIGH)
        } else {
            ttsManager.speak(getString(R.string.tts_page_emergency_contact_edit_add), TtsManager.Priority.HIGH)
        }
        ttsManager.speak(getString(R.string.tts_hint_emergency_contact_edit), TtsManager.Priority.HIGH)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        ttsManager.release()
        _binding = null
    }

    private fun setupInputs() {
        binding.etName.doAfterTextChanged { viewModel.updateName(it.toString()) }
        binding.etPhone.doAfterTextChanged { viewModel.updatePhone(it.toString()) }
        binding.etRelationship.doAfterTextChanged { viewModel.updateRelationship(it.toString()) }
    }

    private fun setupSaveButton() {
        binding.btnSave.setOnClickListener {
            viewModel.save()
        }
    }

    private fun setupVoiceCommands() = bindVoiceCommands { cmd ->
        when (cmd) {
            VoiceCommand.SAVE, VoiceCommand.CONFIRM -> { viewModel.save(); true }
            VoiceCommand.CANCEL -> { findNavController().popBackStack(); true }
            else -> false
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                    binding.btnSave.isEnabled = !state.isLoading

                    if (binding.etName.text.toString() != state.name) {
                        binding.etName.setText(state.name)
                    }
                    if (binding.etPhone.text.toString() != state.phone) {
                        binding.etPhone.setText(state.phone)
                    }
                    if (binding.etRelationship.text.toString() != state.relationship) {
                        binding.etRelationship.setText(state.relationship)
                    }

                    state.error?.let { error ->
                        binding.tvError.text = error
                        binding.tvError.visibility = View.VISIBLE
                    } ?: run {
                        binding.tvError.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is EmergencyContactEditEvent.Saved -> {
                            ttsManager.speak(getString(R.string.tts_contact_save_success), TtsManager.Priority.HIGH)
                            findNavController().popBackStack()
                        }
                        is EmergencyContactEditEvent.Error -> {
                            ttsManager.speak(event.message, TtsManager.Priority.HIGH)
                        }
                    }
                }
            }
        }
    }
}
