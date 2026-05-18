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
import androidx.navigation.fragment.findNavController
import com.guiderun.app.R
import com.guiderun.app.accessibility.HapticFeedback
import com.guiderun.app.accessibility.TtsManager
import com.guiderun.app.accessibility.voice.VoiceCommand
import com.guiderun.app.accessibility.voice.bindVoiceCommands
import com.guiderun.app.databinding.FragmentAccessibilitySettingsBinding
import com.guiderun.app.ui.theme.BlindDesignTokens
import com.guiderun.app.util.EdgeToEdgeHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AccessibilitySettingsFragment : Fragment() {

    private var _binding: FragmentAccessibilitySettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AccessibilitySettingsViewModel by viewModels()

    @Inject lateinit var ttsManager: TtsManager
    @Inject lateinit var hapticFeedback: HapticFeedback

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

        setupFontScaleToggle()
        setupContrastToggle()
        setupHapticToggle()
        setupTtsSpeedSlider()
        setupTtsVolumeSlider()
        setupVoiceCommands()
        observeUiState()
        observeEvents()

        binding.header.announceOnEnter(
            tts = ttsManager,
            fullText = getString(R.string.tts_page_accessibility_settings),
        )
        ttsManager.speak(
            getString(R.string.tts_hint_accessibility_settings),
            TtsManager.Priority.HIGH,
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        ttsManager.release()
        _binding = null
    }

    private fun setupFontScaleToggle() {
        binding.toggleFontScale.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val scale = when (checkedId) {
                R.id.btn_font_125 -> BlindDesignTokens.FontScale.Large
                R.id.btn_font_150 -> BlindDesignTokens.FontScale.ExtraLarge
                R.id.btn_font_200 -> BlindDesignTokens.FontScale.Huge
                else -> BlindDesignTokens.FontScale.Normal
            }
            viewModel.updateFontScale(scale)
        }
    }

    private fun setupContrastToggle() {
        binding.toggleContrast.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val theme = when (checkedId) {
                R.id.btn_contrast_white -> BlindDesignTokens.ContrastTheme.White
                R.id.btn_contrast_yellow -> BlindDesignTokens.ContrastTheme.Yellow
                else -> BlindDesignTokens.ContrastTheme.Black
            }
            viewModel.updateContrastTheme(theme)
        }
    }

    private fun setupHapticToggle() {
        binding.toggleHaptic.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val strength = when (checkedId) {
                R.id.btn_haptic_off -> HapticFeedback.STRENGTH_OFF
                R.id.btn_haptic_strong -> HapticFeedback.STRENGTH_STRONG
                else -> HapticFeedback.STRENGTH_NORMAL
            }
            viewModel.updateHapticStrength(strength)
            // 立即播放 sample，让用户感知当前强度（OFF 不震动）
            hapticFeedback.confirm()
        }
    }

    private fun setupTtsSpeedSlider() {
        binding.seekBarTtsSpeed.max = 30 // 0.5 ~ 3.5，每格 0.1
        binding.seekBarTtsSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val speed = 0.5f + progress * 0.1f
                    viewModel.updateTtsSpeed(speed)
                    binding.tvTtsSpeedValue.text = String.format("%.1fx", speed)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                ttsManager.speak(getString(R.string.tts_speed_adjusted), TtsManager.Priority.NORMAL)
            }
        })
    }

    private fun setupTtsVolumeSlider() {
        binding.seekBarTtsVolume.max = 10 // 0.0 ~ 1.0，每格 0.1
        binding.seekBarTtsVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val volume = (progress * 0.1f).coerceIn(0.0f, 1.0f)
                    viewModel.updateTtsVolume(volume)
                    binding.tvTtsVolumeValue.text = "${(volume * 100).toInt()}%"
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
    }

    private fun setupVoiceCommands() = bindVoiceCommands { cmd ->
        when (cmd) {
            VoiceCommand.SPEED_FASTER -> {
                adjustTtsSpeed(+5)
                true
            }
            VoiceCommand.SPEED_SLOWER -> {
                adjustTtsSpeed(-5)
                true
            }
            VoiceCommand.CANCEL -> {
                findNavController().popBackStack()
                true
            }
            else -> false
        }
    }

    private fun adjustTtsSpeed(delta: Int) {
        val current = binding.seekBarTtsSpeed.progress
        val next = (current + delta).coerceIn(0, binding.seekBarTtsSpeed.max)
        binding.seekBarTtsSpeed.progress = next
        val speed = 0.5f + next * 0.1f
        viewModel.updateTtsSpeed(speed)
        binding.tvTtsSpeedValue.text = String.format("%.1fx", speed)
        ttsManager.speak(getString(R.string.tts_speed_adjusted), TtsManager.Priority.HIGH)
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // 字号档位
                    val fontBtnId = when (state.fontScale) {
                        BlindDesignTokens.FontScale.Large -> R.id.btn_font_125
                        BlindDesignTokens.FontScale.ExtraLarge -> R.id.btn_font_150
                        BlindDesignTokens.FontScale.Huge -> R.id.btn_font_200
                        else -> R.id.btn_font_100
                    }
                    if (binding.toggleFontScale.checkedButtonId != fontBtnId) {
                        binding.toggleFontScale.check(fontBtnId)
                    }

                    // 对比度
                    val contrastBtnId = when (state.contrastTheme) {
                        BlindDesignTokens.ContrastTheme.White -> R.id.btn_contrast_white
                        BlindDesignTokens.ContrastTheme.Yellow -> R.id.btn_contrast_yellow
                        else -> R.id.btn_contrast_black
                    }
                    if (binding.toggleContrast.checkedButtonId != contrastBtnId) {
                        binding.toggleContrast.check(contrastBtnId)
                    }

                    // 震动强度
                    val hapticBtnId = when (state.hapticStrength) {
                        HapticFeedback.STRENGTH_OFF -> R.id.btn_haptic_off
                        HapticFeedback.STRENGTH_STRONG -> R.id.btn_haptic_strong
                        else -> R.id.btn_haptic_normal
                    }
                    if (binding.toggleHaptic.checkedButtonId != hapticBtnId) {
                        binding.toggleHaptic.check(hapticBtnId)
                    }

                    // TTS 语速
                    val speedProgress = ((state.ttsSpeed - 0.5f) / 0.1f).toInt()
                    if (binding.seekBarTtsSpeed.progress != speedProgress) {
                        binding.seekBarTtsSpeed.progress = speedProgress
                    }
                    binding.tvTtsSpeedValue.text = String.format("%.1fx", state.ttsSpeed)

                    // TTS 音量
                    val volProgress = (state.ttsVolume * 10).toInt()
                    if (binding.seekBarTtsVolume.progress != volProgress) {
                        binding.seekBarTtsVolume.progress = volProgress
                    }
                    binding.tvTtsVolumeValue.text = "${(state.ttsVolume * 100).toInt()}%"
                }
            }
        }
    }

    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    if (event == AccessibilitySettingsEvent.RecreateRequired) {
                        viewModel.onEventHandled()
                        ttsManager.speak(
                            getString(R.string.blind_tts_a11y_setting_applied),
                            TtsManager.Priority.HIGH,
                        )
                        requireActivity().recreate()
                    }
                }
            }
        }
    }
}
