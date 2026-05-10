package com.guiderun.app.ui.blind

import android.Manifest
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
import com.guiderun.app.accessibility.SpeechRecognizerManager
import com.guiderun.app.databinding.FragmentCreateRequestBinding
import com.guiderun.app.util.AppPermissions
import com.guiderun.app.util.EdgeToEdgeHelper
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

    private var voiceInputManager: SpeechRecognizerManager? = null
    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startVoiceInputForNotes()
        else toast(R.string.voice_input_permission_denied)
    }

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
        EdgeToEdgeHelper.applyInsets(view)
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
            // lat/lng 仅在地理编码成功时存在；缺失则保留旧 GPS 坐标
            val newLat = if (bundle.containsKey("lat")) bundle.getDouble("lat") else null
            val newLng = if (bundle.containsKey("lng")) bundle.getDouble("lng") else null
            viewModel.onEditRequestResult(
                durationMinutes = bundle.getInt("durationMinutes", 60),
                locationDescription = bundle.getString("locationDescription", "当前位置") ?: "当前位置",
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
        binding.btnConfirm.setOnClickListener { viewModel.onConfirmPressed() }
        binding.btnCancel.setOnClickListener { viewModel.onCancelPressed() }
        binding.tvLocationStatus.setOnClickListener { viewModel.onRetryLocation() }
        binding.btnModify.setOnClickListener { navigateToEditRequest() }
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
                // CreateRequest 是 nav graph 起始目的地，没有"上一页"。
                // 调 popBackStack 会清空 backQueue 把 NavController 弄成废状态，后续 navigate 必崩。
                // 直接 finish 回 MainActivity HomeScreen 才是正确语义。
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
        voiceInputManager?.destroy()
        voiceInputManager = null
        _binding = null
    }
}
