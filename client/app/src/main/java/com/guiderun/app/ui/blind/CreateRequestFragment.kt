package com.guiderun.app.ui.blind

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.guiderun.app.databinding.FragmentCreateRequestBinding
import com.guiderun.app.util.AppPermissions
import com.guiderun.app.util.PermissionHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CreateRequestFragment : Fragment() {

    private val viewModel: CreateRequestViewModel by viewModels()
    private var _binding: FragmentCreateRequestBinding? = null
    private val binding get() = _binding!!

    private lateinit var permissionHelper: PermissionHelper
    private var permissionChecked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionHelper = PermissionHelper(this) { allGranted, _ ->
            if (allGranted) {
                // ★ 权限授予后才启动定位
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
        setupDurationToggle()
        setupListeners()
        setupEditResultListener()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { collectUiState() }
                launch { collectNavEvents() }
            }
        }
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
            viewModel.onEditRequestResult(
                durationMinutes = bundle.getInt("durationMinutes", 60),
                locationDescription = bundle.getString("locationDescription", "当前位置") ?: "当前位置",
                notes = bundle.getString("notes", "") ?: "",
            )
        }
    }

    private fun setupListeners() {
        binding.etNotes.doAfterTextChanged { text ->
            viewModel.onNotesChanged(text?.toString() ?: "")
        }
        binding.btnConfirm.setOnClickListener { viewModel.onConfirmPressed() }
        binding.btnCancel.setOnClickListener { viewModel.onCancelPressed() }
        binding.tvLocationStatus.setOnClickListener { viewModel.onRetryLocation() }
        binding.btnModify.setOnClickListener { navigateToEditRequest() }
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

            binding.tvLocationStatus.text = when (state.locationStatus) {
                LocationStatus.Loading -> getString(R.string.create_request_location_loading)
                is LocationStatus.Located -> getString(R.string.create_request_location_found)
                LocationStatus.Failed -> getString(R.string.create_request_location_failed)
            }

            if (state.errorMessage != null) {
                binding.tvError.text = state.errorMessage
                binding.tvError.visibility = View.VISIBLE
            } else {
                binding.tvError.visibility = View.GONE
            }

            val isCountingDown = state.confirmCountdown != null
            binding.btnConfirm.text = when {
                state.isSubmitting -> getString(R.string.create_request_submitting)
                isCountingDown -> getString(R.string.create_request_btn_confirm_countdown, state.confirmCountdown)
                else -> getString(R.string.create_request_btn_confirm)
            }
            binding.btnConfirm.isEnabled = !state.isSubmitting

            binding.btnCancel.text = if (isCountingDown)
                getString(R.string.create_request_btn_cancel_countdown)
            else
                getString(R.string.create_request_btn_cancel)
        }
    }

    private suspend fun collectNavEvents() {
        viewModel.navEvent.collect { event ->
            when (event) {
                is CreateRequestNavEvent.ToWaitingMatch -> {
                    val args = bundleOf("requestId" to event.requestId)
                    findNavController().navigate(R.id.action_createRequest_to_waitingMatch, args)
                }
                CreateRequestNavEvent.Back -> findNavController().popBackStack()
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
            // ★ GPS 关闭再打开后，重新请求定位
            viewModel.onRetryLocation()
        }

        // no-op: this page has no active request
    }

    override fun onPause() {
        super.onPause()
        viewModel.onScreenPaused()
        // no-op
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
