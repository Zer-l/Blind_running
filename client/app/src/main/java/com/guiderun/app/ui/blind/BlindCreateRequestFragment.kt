package com.guiderun.app.ui.blind

import android.Manifest
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
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
import com.guiderun.app.accessibility.asr.AsrEngine
import com.guiderun.app.accessibility.voice.RequestVoiceParser
import com.guiderun.app.accessibility.voice.VoiceCommand
import com.guiderun.app.accessibility.voice.bindVoiceCommands
import com.guiderun.app.databinding.FragmentCreateRequestBinding
import com.guiderun.app.util.AppPermissions
import com.guiderun.app.util.EdgeToEdgeHelper
import com.guiderun.app.util.PermissionHelper
import com.guiderun.app.util.applyAdaptiveOrientation
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 视障端发起跑步请求页 Fragment。
 *
 * 支持两条进入路径：
 * 1. 正常进入：onResume 申请定位权限→获取 GPS→逆地理编码填充集合点
 * 2. 一键发起（EXTRA_QUICK_START）：跳过页面渲染直接调 submitWithLastPrefs()，成功后导航到 WaitingMatch
 *
 * 语音输入（批量模式）：btnVoiceInput 或音量键长按 → SpeechRecognizerManager → RequestVoiceParser
 * 解析出地点/时长/备注，回填字段并 TTS 朗读确认；解析失败提示重说不污染已有数据。
 *
 * 返回键直接 popBackStack（取消无破坏性），不弹确认弹窗。
 */
@AndroidEntryPoint
class BlindCreateRequestFragment : Fragment() {

    private val viewModel: BlindCreateRequestViewModel by viewModels()
    private var _binding: FragmentCreateRequestBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var blindFeedback: BlindFeedback
    @Inject lateinit var ttsManager: TtsManager
    @Inject lateinit var hapticFeedback: HapticFeedback

    private lateinit var permissionHelper: PermissionHelper
    private var permissionChecked = false

    /**
     * 一键发起标志：onViewCreated 检测到 EXTRA_QUICK_START 即设 true，
     * 跳过 onResume 的页面 TTS 播报与 startLocationUpdates，避免：
     * ① 用户已被路由到 WaitingMatch 仍听到"发起跑步请求...长按2秒..."误导提示
     * ② 真实重新定位浪费电 + 播报"定位成功，xxx"扰乱听感
     * ③ startLocationUpdates 与 submitWithLastPrefs 竞态覆盖 uiState.locationStatus
     */
    private var isQuickStart = false

    private var voiceInputManager: SpeechRecognizerManager? = null
    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startBatchVoiceInput()
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
        setupBackPressInterception()
        setupVoiceCommands()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { collectUiState() }
                launch { collectNavEvents() }
            }
        }

        // 一键发起入口：BlindHome 长按 2s 触发，跳过手势确认直接用上次偏好提交
        if (arguments?.getBoolean(BlindActivity.EXTRA_QUICK_START) == true) {
            arguments?.remove(BlindActivity.EXTRA_QUICK_START)
            isQuickStart = true
            permissionChecked = true  // 跳过 onResume 内的权限再申请路径
            // 隐藏页面内容避免闪屏（提交完成后直接导航到 WaitingMatch）
            binding.root.visibility = View.INVISIBLE
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
    }

    private fun setupVoiceCommands() = bindVoiceCommands(
        // 长按音量键语音入口与"语音输入按钮"完全一致：同一提示语 + 同一批量解析回填
        promptRes = R.string.blind_tts_voice_input_prompt,
        rawTextInterceptor = { text -> fillFromVoice(text) },
    ) { cmd ->
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
            VoiceCommand.DURATION_30 -> { viewModel.onDurationSelected(30); true }
            VoiceCommand.DURATION_60 -> { viewModel.onDurationSelected(60); true }
            VoiceCommand.DURATION_90 -> { viewModel.onDurationSelected(90); true }
            VoiceCommand.DURATION_120 -> { viewModel.onDurationSelected(120); true }
            else -> false
        }
    }

    private fun setupBackPressInterception() {
        // 取消创建无需二次确认：返回键直接退出，与语音"取消"统一走 onBackRequested → finish
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    binding.footer.primaryGesture.reset()
                    viewModel.onBackRequested()
                }
            },
        )
    }

    private fun setupDurationToggle() {
        // 大字号档位（≥150%）切换为竖排，避免选项文字被 autoSize 截断为省略号
        binding.toggleDuration.applyAdaptiveOrientation(resources.configuration.fontScale)
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

    private fun setupListeners() {
        binding.etLocation.doAfterTextChanged { text ->
            viewModel.onLocationDescriptionChanged(text?.toString() ?: "")
        }
        binding.etNotes.doAfterTextChanged { text ->
            viewModel.onNotesChanged(text?.toString() ?: "")
        }
        binding.header.setOnClickListener { viewModel.onRetryLocation() }
        binding.btnVoiceInput.setOnClickListener { onVoiceInputClicked() }
    }

    private fun onVoiceInputClicked() {
        if (permissionHelper.isGranted(requireContext(), AppPermissions.RECORD_AUDIO)) {
            startBatchVoiceInput()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    /**
     * 批量语音录入：用户一次说出 "地点 xxx 时长 60 备注 xxx"，
     * 解析成功后回填字段并 TTS 回放，让用户在长按主按钮前确认。
     *
     * 与备注/地点输入框麦克风的单字段录入不同，此处使用 [RequestVoiceParser]。
     * SpeechRecognizerManager.start() 内部已 beginAsr，期间 TTS 静音；
     * onResult 回调时 mute 已解除，可立即播回放语句。
     */
    private fun startBatchVoiceInput() {
        // 进入语音输入前先重置长按手势，避免用户左手在按按钮，右手点麦克风
        binding.footer.primaryGesture.reset()

        val manager = voiceInputManager ?: SpeechRecognizerManager(
            context = requireContext(),
            onResult = { text ->
                if (!fillFromVoice(text)) {
                    // 完全不匹配关键字 → 提示重说，不污染任何字段
                    ttsManager.speak(
                        getString(R.string.blind_tts_voice_input_not_recognized),
                        TtsManager.Priority.INTERACTION,
                    )
                    hapticFeedback.warning()
                }
            },
            onError = { msg -> blindFeedback.error(msg) },
            onStartListening = { blindFeedback.info(R.string.voice_input_listening) },
        ).also { voiceInputManager = it }

        if (!manager.isAvailable) {
            blindFeedback.warning(R.string.voice_input_unavailable)
            return
        }

        // 提示用户格式，speakAndWait 等待播完才启动 ASR（SpeechRecognizerManager 内会 beginAsr）。
        // 提示语保持精简（约 3 秒），timeoutMs 10s 给慢语速 / 字号缩放档充足缓冲，
        // 避免超时后 beginAsr 打断提示。
        viewLifecycleOwner.lifecycleScope.launch {
            ttsManager.speakAndWait(
                getString(R.string.blind_tts_voice_input_prompt),
                TtsManager.Priority.INTERACTION,
                timeoutMs = 10_000L,
            )
            // 批量录入：放宽后端点静音，容忍"地点…时长…备注…"分段口述间的停顿
            manager.start(AsrEngine.BATCH_EOS_MILLIS)
        }
    }

    /**
     * 批量语音解析回填：两条语音入口（语音输入按钮 / 长按音量键）共用，确保解析、回填、回放完全一致。
     *
     * @return true = 解析命中至少一个字段，已回填并 TTS 回放；
     *         false = 完全不匹配关键字（调用方决定播"未识别"提示或回落到指令解析）。
     */
    private fun fillFromVoice(text: String): Boolean {
        val parsed = RequestVoiceParser.parse(text) ?: return false
        viewModel.onBatchVoiceParsed(parsed)
        hapticFeedback.confirm()

        // 回放识别结果，让用户确认或重说
        val readback = buildString {
            append(getString(R.string.blind_tts_voice_input_readback_prefix))
            parsed.location?.let { append(getString(R.string.blind_tts_voice_input_readback_location, it)) }
            parsed.durationMinutes?.let { append(getString(R.string.blind_tts_voice_input_readback_duration, it)) }
            parsed.notes?.let { append(getString(R.string.blind_tts_voice_input_readback_notes, it)) }
            append(getString(R.string.blind_tts_voice_input_readback_suffix))
        }
        ttsManager.speak(readback, TtsManager.Priority.INTERACTION)
        return true
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

            val currentLocation = binding.etLocation.text?.toString() ?: ""
            if (currentLocation != state.locationDescription) {
                binding.etLocation.setText(state.locationDescription)
                binding.etLocation.setSelection(state.locationDescription.length)
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
                is BlindCreateRequestNavEvent.ToWaitingMatch -> {
                    val args = Bundle().apply { putString("requestId", event.requestId) }
                    findNavController().navigate(
                        R.id.action_createRequest_to_waitingMatch,
                        args,
                    )
                }
                BlindCreateRequestNavEvent.Back -> {
                    // 优先回退到栈中上一页（通常是 BlindHome）；
                    // 仅在栈空（一键发起 / 历史 DEST_CREATE_REQUEST 入口把本页作为起点）时才 finish Activity。
                    // 之前无脑 finish() 会跨过 BlindHome 直接退出整个应用（MainActivity 启动后已 finish）。
                    if (!findNavController().popBackStack()) {
                        requireActivity().finish()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (isQuickStart) return  // 一键发起：已直接提交，等待 navEvent 跳 WaitingMatch
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
