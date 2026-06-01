package com.guiderun.app.ui.blind

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
import com.guiderun.app.accessibility.HapticFeedback
import com.guiderun.app.accessibility.TtsManager
import com.guiderun.app.accessibility.speakPageEntry
import com.guiderun.app.accessibility.voice.VoiceCommand
import com.guiderun.app.accessibility.voice.bindVoiceCommands
import com.guiderun.app.databinding.FragmentProfileEditBinding
import com.guiderun.app.domain.model.Gender
import com.guiderun.app.util.EdgeToEdgeHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 视障端个人资料编辑页 Fragment。
 *
 * 使用 isUpdatingFromState 标志防止 UI 回填触发 ViewModel 重复更新（避免 setState→collect→setText→doAfterTextChanged→setState 无限循环）。
 * 主按钮长按 2s+5s 保存，与视障端其他 Primary 按钮统一。
 */
@AndroidEntryPoint
class BlindProfileEditFragment : Fragment() {

    private var _binding: FragmentProfileEditBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BlindProfileEditViewModel by viewModels()

    @Inject
    lateinit var ttsManager: TtsManager

    @Inject
    lateinit var hapticFeedback: HapticFeedback

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
        EdgeToEdgeHelper.applyInsets(view)
        ttsManager.acquire()

        setupInputs()
        setupSaveButton()
        setupVoiceCommands()
        observeUiState()
        observeEvents()
    }

    override fun onResume() {
        super.onResume()
        speakPageEntry(ttsManager, R.string.tts_page_profile_edit, R.string.tts_hint_profile_edit)
    }

    override fun onPause() {
        super.onPause()
        binding.btnSave.reset()
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

    /** 主按钮长按 2s + 5s 倒计时确认保存，与其他视障端 Primary 一致 */
    private fun setupSaveButton() {
        binding.btnSave.bind(
            scope = viewLifecycleOwner.lifecycleScope,
            ttsManager = ttsManager,
            hapticFeedback = hapticFeedback,
            thresholdLabelRes = R.string.blind_tts_save_profile_threshold,
            countdownLabelRes = R.string.blind_tts_long_press_cancelled,
            onCountdownCommitted = { viewModel.save() },
        )
        binding.btnSave.contentDescription = getString(R.string.blind_hint_save_profile_long_press)
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
                        is BlindProfileEditEvent.Saved -> {
                            ttsManager.speak(getString(R.string.tts_profile_save_success), TtsManager.Priority.INTERACTION)
                            findNavController().popBackStack()
                        }
                        is BlindProfileEditEvent.Error -> {
                            // Error already handled in uiState
                        }
                    }
                }
            }
        }
    }
}
