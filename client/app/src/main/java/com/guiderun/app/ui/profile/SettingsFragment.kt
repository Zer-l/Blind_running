package com.guiderun.app.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.guiderun.app.R
import com.guiderun.app.accessibility.TtsManager
import com.guiderun.app.databinding.FragmentSettingsBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var ttsManager: TtsManager

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
        ttsManager.acquire()

        setupMenuItems()
        ttsManager.speak("设置页面", TtsManager.Priority.HIGH)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        ttsManager.release()
        _binding = null
    }

    private fun setupMenuItems() {
        binding.itemProfile.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_profileEdit)
        }

        binding.itemEmergencyContacts.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_emergencyContacts)
        }

        binding.itemBlindStats.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_blindStats)
        }

        binding.itemAccessibility.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_accessibilitySettings)
        }

        binding.itemAbout.setOnClickListener {
            ttsManager.speak("助盲跑，让视障人士也能享受跑步的乐趣", TtsManager.Priority.NORMAL)
        }
    }
}
