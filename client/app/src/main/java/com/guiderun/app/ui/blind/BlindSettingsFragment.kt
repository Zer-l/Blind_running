package com.guiderun.app.ui.blind

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.guiderun.app.MainActivity
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
import com.guiderun.app.databinding.FragmentSettingsBinding
import com.guiderun.app.ui.shared.HomeViewModel
import com.guiderun.app.util.EdgeToEdgeHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 视障端设置页 Fragment。
 *
 * 复用 HomeViewModel（activityViewModels）获取 logout 逻辑，与首页共享同一 VM 实例，
 * 避免 logout 后需要额外跨 Activity 通信。
 * 退出登录采用长按 2s+5s 防误触；成功后启动全新 MainActivity 任务栈（视障路径下 MainActivity 已 finish 自身）。
 * 语音指令：OPEN_xxx 跳子页，LOGOUT 直接登出，CANCEL 退回。
 */
@AndroidEntryPoint
class BlindSettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by activityViewModels()

    @Inject
    lateinit var ttsManager: TtsManager

    @Inject
    lateinit var hapticFeedback: HapticFeedback

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        EdgeToEdgeHelper.applyInsets(view)
        ttsManager.acquire()

        setupMenuItems()
        setupLogoutButton()
        setupVoiceCommands()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { collectUiState() }
            }
        }
    }

    /** 每次页面进入或从子页返回时复播页面名+操作 hint。统一走 speakPageEntry 串行模式，
     *  避免两次 INTERACTION speak 互相 flush 导致只听到 hint。 */
    override fun onResume() {
        super.onResume()
        speakPageEntry(ttsManager, R.string.tts_page_settings, R.string.tts_hint_settings)
    }

    /** 设置页：把"OPEN_xxx"指令翻译成子页导航 */
    private fun setupVoiceCommands() = bindVoiceCommands { cmd ->
        when (cmd) {
            VoiceCommand.OPEN_PROFILE_EDIT -> {
                findNavController().navigate(R.id.action_settings_to_profileEdit); true
            }
            VoiceCommand.OPEN_EMERGENCY_CONTACTS -> {
                findNavController().navigate(R.id.action_settings_to_emergencyContacts); true
            }
            VoiceCommand.OPEN_STATS -> {
                findNavController().navigate(R.id.action_settings_to_blindStats); true
            }
            VoiceCommand.OPEN_ACCESSIBILITY -> {
                findNavController().navigate(R.id.action_settings_to_accessibilitySettings); true
            }
            VoiceCommand.LOGOUT -> {
                // 语音是明确意图，跳过长按 2s+5s 撤销窗口直接登出（与 WaitingMatch CANCEL 一致）；
                // "退出登录/登出/注销" 短语足够独特，CommandParser 单字模糊已禁用，误触风险低
                viewModel.logout(); true
            }
            VoiceCommand.CANCEL -> {
                findNavController().popBackStack(); true
            }
            else -> false
        }
    }

    override fun onPause() {
        super.onPause()
        binding.btnLogout.reset()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        ttsManager.release()
        _binding = null
    }

    private fun setupMenuItems() {
        binding.itemProfile.setOnClickListener {
            hapticFeedback.tick()
            findNavController().navigate(R.id.action_settings_to_profileEdit)
        }

        binding.itemEmergencyContacts.setOnClickListener {
            hapticFeedback.tick()
            findNavController().navigate(R.id.action_settings_to_emergencyContacts)
        }

        binding.itemBlindStats.setOnClickListener {
            hapticFeedback.tick()
            findNavController().navigate(R.id.action_settings_to_blindStats)
        }

        binding.itemAccessibility.setOnClickListener {
            hapticFeedback.tick()
            findNavController().navigate(R.id.action_settings_to_accessibilitySettings)
        }

        binding.itemAbout.setOnClickListener {
            hapticFeedback.tick()
            ttsManager.speak(getString(R.string.tts_about), TtsManager.Priority.INTERACTION)
        }
    }

    /**
     * 退出登录采用长按 2s+5s 倒计时手势。
     * 撤销窗口防误触；倒计时执行后调 logout()，由 collectUiState 监听 loggedOut → finish Activity。
     * 不在此处直接 finish，因为 logout 是 suspend（清 token / 清缓存）需要等完成。
     */
    private fun setupLogoutButton() {
        binding.btnLogout.contentDescription =
            getString(R.string.blind_hint_home_logout_long_press)
        binding.btnLogout.bind(
            scope = viewLifecycleOwner.lifecycleScope,
            ttsManager = ttsManager,
            hapticFeedback = hapticFeedback,
            thresholdLabelRes = R.string.blind_tts_home_logout_threshold,
            countdownLabelRes = R.string.blind_tts_long_press_cancelled,
            onCountdownCommitted = { viewModel.logout() },
        )
    }

    private suspend fun collectUiState() {
        viewModel.uiState.collect { state ->
            // logout 倒计时执行后会清 token；视障路径下 MainActivity 已在启动时 finish 自身，
            // 无法再依赖 MainActivity 收 AuthEvent 跳 Login。这里显式启动一个全新的 MainActivity 任务栈：
            // 新 MainActivity 的 MainViewModel.resolveTarget 读到 token=null → StartTarget.Login → 渲染登录页。
            if (state.loggedOut) {
                viewModel.onNavigated()
                ttsManager.speak(
                    getString(R.string.blind_tts_home_logout_done),
                    TtsManager.Priority.INTERACTION,
                )
                hapticFeedback.confirm()
                val ctx = requireContext().applicationContext
                ctx.startActivity(
                    Intent(ctx, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    },
                )
                requireActivity().finish()
                return@collect
            }
        }
    }
}
