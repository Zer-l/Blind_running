package com.guiderun.app.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.guiderun.app.R
import com.guiderun.app.accessibility.TtsManager
import com.guiderun.app.databinding.FragmentAccessibilitySettingsBinding
import com.guiderun.app.util.EdgeToEdgeHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AccessibilitySettingsFragment : Fragment() {

    private var _binding: FragmentAccessibilitySettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AccessibilitySettingsViewModel by viewModels()

    @Inject
    lateinit var ttsManager: TtsManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAccessibilitySettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        EdgeToEdgeHelper.applyInsets(view)
        ttsManager.acquire()

        setupTtsSpeedSlider()
        observeUiState()
        ttsManager.speak("无障碍设置页面", TtsManager.Priority.HIGH)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        ttsManager.release()
        _binding = null
    }

    private fun setupTtsSpeedSlider() {
        binding.seekBarTtsSpeed.max = 30 // 0.5 to 3.5, step 0.1
        binding.seekBarTtsSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val speed = 0.5f + progress * 0.1f
                    viewModel.updateTtsSpeed(speed)
                    binding.tvTtsSpeedValue.text = String.format("%.1fx", speed)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                ttsManager.speak("语速已调整", TtsManager.Priority.NORMAL)
            }
        })
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val progress = ((state.ttsSpeed - 0.5f) / 0.1f).toInt()
                    binding.seekBarTtsSpeed.progress = progress
                    binding.tvTtsSpeedValue.text = String.format("%.1fx", state.ttsSpeed)
                }
            }
        }
    }
}
