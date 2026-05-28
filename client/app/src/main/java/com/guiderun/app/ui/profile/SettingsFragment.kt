package com.guiderun.app.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
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
import com.guiderun.app.ui.home.HomeViewModel
import com.guiderun.app.util.EdgeToEdgeHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SettingsFragment : Fragment() {

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
            // logout 倒计时执行后会清 token + emit AuthEvents，BlindActivity 必须 finish
            // 才能让 MainActivity 恢复并响应 authEvents 跳 Login；
            // 否则 BlindActivity 残留会导致后续视障端 API 调用因 token 失效全部 403。
            if (state.loggedOut) {
                viewModel.onNavigated()
                ttsManager.speak(
                    getString(R.string.blind_tts_home_logout_done),
                    TtsManager.Priority.INTERACTION,
                )
                hapticFeedback.confirm()
                requireActivity().finish()
                return@collect
            }
        }
    }
}
