package com.guiderun.app.ui.blind

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.guiderun.app.accessibility.TtsManager
import com.guiderun.app.ui.volunteer.TrackPlaybackScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class TrackPlaybackFragment : Fragment() {

    @Inject lateinit var ttsManager: TtsManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val requestId = requireArguments().getString("requestId") ?: ""
        return ComposeView(requireContext()).apply {
            setContent {
                TrackPlaybackScreen(
                    requestId = requestId,
                    onBack = { findNavController().navigateUp() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ttsManager.acquire()
        ttsManager.speak("轨迹回放页面。点击播放按钮开始回放，可调节回放速度")
    }

    override fun onPause() {
        super.onPause()
        ttsManager.release()
    }
}
