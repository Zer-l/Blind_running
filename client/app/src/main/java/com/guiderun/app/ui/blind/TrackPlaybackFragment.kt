package com.guiderun.app.ui.blind

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.guiderun.app.ui.volunteer.TrackPlaybackScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TrackPlaybackFragment : Fragment() {

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
}
