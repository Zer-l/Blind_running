package com.guiderun.app.ui.blind

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.first
import androidx.navigation.fragment.findNavController
import com.guiderun.app.R
import com.guiderun.app.accessibility.HapticFeedback
import com.guiderun.app.accessibility.TtsManager
import com.guiderun.app.accessibility.voice.VoiceCommand
import com.guiderun.app.accessibility.voice.bindVoiceCommands
import com.guiderun.app.databinding.FragmentBlindHomeBinding
import com.guiderun.app.domain.model.RunRequest
import com.guiderun.app.domain.model.RunRequestStatus
import com.guiderun.app.ui.home.HomeViewModel
import com.guiderun.app.ui.navigation.ActiveOrderRouter
import com.guiderun.app.util.EdgeToEdgeHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 视障端首页：与视障端其他 13 个 XML 页面共用 BaseBlindActivity 框架（对比度主题 / 字号缩放 /
 * 权限批量申请 / 音量键语音 / SOS 三连击）。
 *
 * 关键交互：
 * - 主按钮 btn_start（长按 2s+5s）→ 进入 CreateRequestFragment
 * - 主按钮 btn_quick_start（长按 2s+5s，仅 quickStartEnabled 时显示）→ 进入 CreateRequest 并自动提交上次设置
 * - 历史记录 / 设置卡片（单击）→ 跳对应 Fragment
 * - 进行中订单横幅（visible 时单击）→ ActiveOrderRouter.blindFragmentId 直接跳对应业务 Fragment
 *
 * ViewModel：直接复用 HomeViewModel via activityViewModels()。与 Compose HomeScreen 共享同一份
 * 用户/活跃订单/quickStartEnabled 数据流，避免重复初始化 TTS 欢迎语。
 */
@AndroidEntryPoint
class BlindHomeFragment : Fragment() {

    private val viewModel: HomeViewModel by activityViewModels()
    private var _binding: FragmentBlindHomeBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var ttsManager: TtsManager
    @Inject lateinit var hapticFeedback: HapticFeedback

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentBlindHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        EdgeToEdgeHelper.applyInsets(view)
        setupPrimaryButton()
        setupQuickStartButton()
        setupSecondaryButtons()
        setupBackPressIntercept()
        setupVoiceCommands()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { collectActiveRequest() }
                launch { collectQuickStartEnabled() }
                launch { collectUiState() }
            }
        }
    }

    private fun setupPrimaryButton() {
        binding.btnStart.contentDescription =
            getString(R.string.blind_hint_home_create_long_press)
        binding.btnStart.bind(
            scope = viewLifecycleOwner.lifecycleScope,
            ttsManager = ttsManager,
            hapticFeedback = hapticFeedback,
            thresholdLabelRes = R.string.blind_tts_home_create_threshold,
            countdownLabelRes = R.string.blind_tts_long_press_cancelled,
            onCountdownCommitted = { navigateToCreateRequest(quickStart = false) },
        )
    }

    private fun setupQuickStartButton() {
        binding.btnQuickStart.contentDescription =
            getString(R.string.blind_hint_home_quick_start_long_press)
        binding.btnQuickStart.bind(
            scope = viewLifecycleOwner.lifecycleScope,
            ttsManager = ttsManager,
            hapticFeedback = hapticFeedback,
            thresholdLabelRes = 0, // 不播报静态文本，改为 onThresholdReached 动态播报
            countdownLabelRes = R.string.blind_tts_long_press_cancelled,
            onThresholdReached = { speakLastRequestSummary() },
            onCountdownCommitted = { navigateToCreateRequest(quickStart = true) },
        )
    }

    /** 长按阈值达到时播报上次订单信息，等待播报完成后再进入倒计时 */
    private suspend fun speakLastRequestSummary() {
        val summary = viewModel.uiState.value.lastRequestSummary
        val msg = if (summary != null) {
            getString(R.string.blind_tts_quick_start_summary, summary)
        } else {
            getString(R.string.blind_tts_quick_start_no_history)
        }
        ttsManager.speakAndWait(msg, TtsManager.Priority.INTERACTION)
    }

    private fun setupSecondaryButtons() {
        binding.itemHistory.setOnClickListener {
            hapticFeedback.tick()
            findNavController().navigate(R.id.blindHistoryFragment)
        }
        binding.itemSettings.setOnClickListener {
            hapticFeedback.tick()
            findNavController().navigate(R.id.settingsFragment)
        }
        binding.cardActiveOrder.setOnClickListener {
            hapticFeedback.tick()
            val request = viewModel.activeRequest.value ?: return@setOnClickListener
            navigateToActiveOrder(request)
        }
    }


    private fun setupBackPressIntercept() {
        // BlindHome 是 BlindActivity 的起始目的地，返回键应直接退出 Activity 而不是 popBackStack。
        // BlindActivity.onCreate 的 onBackPressedDispatcher 默认会在起点 finish()，
        // 这里再显式注册以便先播 TTS 反馈再退出。
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            },
        )
    }

    private fun setupVoiceCommands() = bindVoiceCommands { cmd ->
        when (cmd) {
            VoiceCommand.CREATE_REQUEST -> {
                navigateToCreateRequest(quickStart = false); true
            }
            VoiceCommand.VIEW_HISTORY -> {
                findNavController().navigate(R.id.blindHistoryFragment); true
            }
            VoiceCommand.PROFILE -> {
                findNavController().navigate(R.id.settingsFragment); true
            }
            VoiceCommand.CONFIRM, VoiceCommand.SAVE -> {
                // 等价于长按主按钮直发：跳到 CreateRequest 让用户走完确认流程，
                // 避免语音指令直接绕过 5s 撤销窗口
                navigateToCreateRequest(quickStart = false); true
            }
            else -> false
        }
    }

    private fun navigateToCreateRequest(quickStart: Boolean) {
        val args = if (quickStart) bundleOf(BlindActivity.EXTRA_QUICK_START to true) else null
        findNavController().navigate(R.id.blindCreateRequestFragment, args)
    }

    private fun navigateToActiveOrder(request: RunRequest) {
        if (request.status.isTerminal()) return
        val destId = ActiveOrderRouter.blindFragmentId(request.status) ?: return
        findNavController().navigate(destId, bundleOf("requestId" to request.id))
    }

    private suspend fun collectActiveRequest() {
        viewModel.activeRequest.collect { request ->
            if (request != null && !request.status.isTerminal()) {
                binding.cardActiveOrder.visibility = View.VISIBLE
                binding.tvActiveOrderStatus.text = getString(
                    R.string.blind_ui_home_active_order_status,
                    statusText(request.status),
                )
                binding.cardActiveOrder.contentDescription = getString(
                    R.string.blind_ui_home_active_order_cd,
                    statusText(request.status),
                )
            } else {
                binding.cardActiveOrder.visibility = View.GONE
            }
        }
    }

    private suspend fun collectQuickStartEnabled() {
        viewModel.quickStartEnabled.collect { enabled ->
            binding.btnQuickStart.visibility = if (enabled) View.VISIBLE else View.GONE
        }
    }

    private suspend fun collectUiState() {
        viewModel.uiState.collect { state ->
            binding.tvWelcome.text = if (state.nickname.isNotBlank()) {
                getString(R.string.blind_ui_home_welcome, state.nickname)
            } else {
                getString(R.string.blind_ui_home_welcome_anonymous)
            }
            binding.tvActiveRole.text = getString(
                R.string.blind_ui_home_active_role,
                state.activeRole.ifBlank { getString(R.string.blind_ui_home_role_unknown) },
            )
        }
    }

    private fun statusText(status: RunRequestStatus): String {
        val resId = when (status) {
            RunRequestStatus.CREATED, RunRequestStatus.MATCHING -> R.string.blind_ui_home_status_matching
            RunRequestStatus.ACCEPTED -> R.string.blind_ui_home_status_accepted
            RunRequestStatus.EN_ROUTE -> R.string.blind_ui_home_status_en_route
            RunRequestStatus.MET -> R.string.blind_ui_home_status_met
            RunRequestStatus.RUNNING -> R.string.blind_ui_home_status_running
            RunRequestStatus.FINISHED -> R.string.blind_ui_home_status_review
            else -> R.string.blind_ui_home_status_in_progress
        }
        return getString(resId)
    }

    override fun onResume() {
        super.onResume()
        announceHomeOnce()
    }

    /**
     * 入页 TTS：每次 onResume 播一次"首页+昵称+角色"。
     * 由 Fragment 显式控制（HomeViewModel 不再被动播报），避免多入口（init/refreshUser/旋屏）叠加。
     *
     * 首次启动时 nickname 异步加载，最多等 800ms 拿到非空昵称；超时 fallback 到"首页"短播报。
     * 后续从子页面返回时 uiState 已就绪，first { nonBlank } 立即返回。
     *
     * 接力 TTS（pending）：上游 ViewModel（如 Matched/BlindRunning 收到对端 ABORTED）通过
     * BlindActivity.setPendingHomeTts(reasonRes) 暂存原因，本方法优先以 HIGH 播报，跳过欢迎语。
     * 避免在源 Fragment 自播时被 onPause→ttsManager.release()→engine.stop() 清队列吞掉。
     */
    private fun announceHomeOnce() {
        val pending = (activity as? BlindActivity)?.consumePendingHomeTts()
        if (pending != null) {
            ttsManager.speak(getString(pending), TtsManager.Priority.HIGH)
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val state = kotlinx.coroutines.withTimeoutOrNull(800L) {
                viewModel.uiState.first { it.nickname.isNotBlank() || !it.isLoading }
            } ?: viewModel.uiState.value
            val msg = when {
                state.nickname.isBlank() -> getString(R.string.blind_tts_home_title_only)
                state.activeRole.isBlank() -> getString(R.string.blind_tts_home_nickname_only, state.nickname)
                else -> getString(R.string.blind_tts_home_short, state.nickname, state.activeRole)
            }
            ttsManager.speak(msg, TtsManager.Priority.NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        binding.btnStart.reset()
        binding.btnQuickStart.reset()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
