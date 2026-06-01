package com.guiderun.app.ui.blind

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.guiderun.app.R
import com.guiderun.app.accessibility.TtsManager
import com.guiderun.app.accessibility.speakPageEntry
import com.guiderun.app.accessibility.voice.VoiceCommand
import com.guiderun.app.accessibility.voice.bindVoiceCommands
import com.guiderun.app.ui.volunteer.VolunteerTrackPlaybackScreen
import com.guiderun.app.ui.volunteer.VolunteerTrackPlaybackViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 视障端轨迹回放页 Fragment（XML 容器，内容为 Compose）。
 *
 * 视障端使用与志愿者端相同的 VolunteerTrackPlaybackScreen（Compose），
 * 通过 ComposeView 嵌入。role 固定传 "BLIND" 确保只回放视障端自己采集的轨迹。
 * Fragment 持有 VolunteerTrackPlaybackViewModel 实例，使语音指令（播放/暂停/重播）
 * 能直接调用 VM 方法而不需要事件总线跨 Compose 边界通信。
 */
@AndroidEntryPoint
class BlindTrackPlaybackFragment : Fragment() {

    @Inject lateinit var ttsManager: TtsManager

    // 与 Compose Screen 共享同一 VM 实例（Fragment 作为 ViewModelStoreOwner），
    // 让语音指令能直接调用 togglePlayback / seekToStart 控制回放
    private val viewModel: VolunteerTrackPlaybackViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val requestId = requireArguments().getString("requestId") ?: ""
        // 视障端入口固定 role=BLIND，只回放自己采集的轨迹
        val role = requireArguments().getString("role") ?: "BLIND"
        return ComposeView(requireContext()).apply {
            setContent {
                VolunteerTrackPlaybackScreen(
                    requestId = requestId,
                    role = role,
                    onBack = { findNavController().navigateUp() },
                    viewModel = viewModel,
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupVoiceCommands()
    }

    /**
     * 轨迹回放页语音控制：
     * - 播放/暂停：CONFIRM（"确认"）/ PAUSE_RUN（"暂停"）/ RESUME_RUN（"继续"）统一 toggle
     * - 重播：RETRY（"重来/再来一次"）回到起点
     * 变速暂不语音化（5/10/20x 多档，低频且无干净短语映射）。
     */
    private fun setupVoiceCommands() = bindVoiceCommands { cmd ->
        when (cmd) {
            VoiceCommand.CONFIRM,
            VoiceCommand.PAUSE_RUN,
            VoiceCommand.RESUME_RUN -> { viewModel.togglePlayback(); true }
            VoiceCommand.RETRY -> { viewModel.seekToStart(); true }
            else -> false
        }
    }

    override fun onResume() {
        super.onResume()
        ttsManager.acquire()
        speakPageEntry(ttsManager, R.string.tts_page_track_playback, R.string.tts_hint_track_playback)
    }

    override fun onPause() {
        super.onPause()
        ttsManager.release()
    }
}
