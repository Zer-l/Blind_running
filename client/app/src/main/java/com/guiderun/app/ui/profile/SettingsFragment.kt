package com.guiderun.app.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.guiderun.app.R
import com.guiderun.app.accessibility.HapticFeedback
import com.guiderun.app.accessibility.TtsManager
import com.guiderun.app.accessibility.voice.VoiceCommand
import com.guiderun.app.accessibility.voice.bindVoiceCommands
import com.guiderun.app.databinding.FragmentSettingsBinding
import com.guiderun.app.util.EdgeToEdgeHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

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
        setupVoiceCommands()
    }

    /** 每次页面进入或从子页返回时复播，确保视障用户始终能听到自己在哪。 */
    override fun onResume() {
        super.onResume()
        ttsManager.speak(getString(R.string.tts_page_settings), TtsManager.Priority.HIGH)
        ttsManager.speak(getString(R.string.tts_hint_settings), TtsManager.Priority.HIGH)
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
            ttsManager.speak(getString(R.string.tts_about), TtsManager.Priority.HIGH)
        }
    }
}
