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
import com.guiderun.app.accessibility.TtsManager
import com.guiderun.app.databinding.FragmentEmergencyContactEditBinding
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
        ttsManager.acquire()

        val index = arguments?.getInt("index", -1) ?: -1
        if (index >= 0) {
            viewModel.initEditMode(index)
        }

        setupInputs()
        setupSaveButton()
        observeUiState()
        observeEvents()

        ttsManager.speak(
            if (index >= 0) "编辑紧急联系人" else "添加紧急联系人",
            TtsManager.Priority.HIGH,
        )
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
                            ttsManager.speak("保存成功", TtsManager.Priority.HIGH)
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
