package com.guiderun.app.ui.blind

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.guiderun.app.databinding.FragmentEditRequestBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class EditRequestFragment : Fragment() {

    private val viewModel: EditRequestViewModel by viewModels()
    private var _binding: FragmentEditRequestBinding? = null
    private val binding get() = _binding!!

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
        setupDurationToggle()
        setupListeners()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                collectUiState()
            }
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
        binding.btnSave.setOnClickListener { saveAndReturn() }
        binding.btnCancel.setOnClickListener { findNavController().popBackStack() }
    }

    private fun saveAndReturn() {
        val state = viewModel.uiState.value
        setFragmentResult(
            "edit_request_result",
            bundleOf(
                "durationMinutes" to state.selectedDurationMinutes,
                "locationDescription" to state.locationDescription,
                "notes" to state.notes,
            ),
        )
        findNavController().popBackStack()
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
        _binding = null
    }
}
