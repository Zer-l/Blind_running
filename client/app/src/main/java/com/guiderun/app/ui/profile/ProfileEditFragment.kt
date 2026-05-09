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
import com.guiderun.app.databinding.FragmentProfileEditBinding
import com.guiderun.app.domain.model.Gender
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ProfileEditFragment : Fragment() {

    private var _binding: FragmentProfileEditBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProfileEditViewModel by viewModels()

    @Inject
    lateinit var ttsManager: TtsManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentProfileEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ttsManager.acquire()

        setupInputs()
        setupSaveButton()
        observeUiState()
        observeEvents()

        ttsManager.speak("编辑个人资料", TtsManager.Priority.HIGH)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        ttsManager.release()
        _binding = null
    }

    private var isUpdatingFromState = false

    private fun setupInputs() {
        binding.etNickname.doAfterTextChanged {
            if (!isUpdatingFromState) viewModel.updateNickname(it.toString())
        }
        binding.etVisionLevel.doAfterTextChanged {
            if (!isUpdatingFromState) viewModel.updateVisionLevel(it.toString())
        }
        binding.etPace.doAfterTextChanged {
            if (!isUpdatingFromState) viewModel.updatePreferredPaceSeconds(it.toString())
        }
        binding.etDuration.doAfterTextChanged {
            if (!isUpdatingFromState) viewModel.updatePreferredDurationMinutes(it.toString())
        }
        binding.etMedicalNotes.doAfterTextChanged {
            if (!isUpdatingFromState) viewModel.updateMedicalNotes(it.toString())
        }
        binding.etVisualDescription.doAfterTextChanged {
            if (!isUpdatingFromState) viewModel.updateVisualDescription(it.toString())
        }

        binding.rgGender.setOnCheckedChangeListener { _, checkedId ->
            if (!isUpdatingFromState) {
                val gender = when (checkedId) {
                    R.id.rbMale -> Gender.MALE
                    R.id.rbFemale -> Gender.FEMALE
                    else -> null
                }
                viewModel.updateGender(gender)
            }
        }
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

                    isUpdatingFromState = true
                    if (binding.etNickname.text.toString() != state.nickname) {
                        binding.etNickname.setText(state.nickname)
                    }
                    if (binding.etVisionLevel.text.toString() != state.visionLevel) {
                        binding.etVisionLevel.setText(state.visionLevel)
                    }
                    if (binding.etPace.text.toString() != state.preferredPaceSeconds) {
                        binding.etPace.setText(state.preferredPaceSeconds)
                    }
                    if (binding.etDuration.text.toString() != state.preferredDurationMinutes) {
                        binding.etDuration.setText(state.preferredDurationMinutes)
                    }
                    if (binding.etMedicalNotes.text.toString() != state.medicalNotes) {
                        binding.etMedicalNotes.setText(state.medicalNotes)
                    }
                    if (binding.etVisualDescription.text.toString() != state.visualDescription) {
                        binding.etVisualDescription.setText(state.visualDescription)
                    }
                    when (state.gender) {
                        Gender.MALE -> binding.rbMale.isChecked = true
                        Gender.FEMALE -> binding.rbFemale.isChecked = true
                        else -> binding.rgGender.clearCheck()
                    }
                    isUpdatingFromState = false

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
                        is ProfileEditEvent.Saved -> {
                            ttsManager.speak("保存成功", TtsManager.Priority.HIGH)
                            findNavController().popBackStack()
                        }
                        is ProfileEditEvent.Error -> {
                            // Error already handled in uiState
                        }
                    }
                }
            }
        }
    }
}
