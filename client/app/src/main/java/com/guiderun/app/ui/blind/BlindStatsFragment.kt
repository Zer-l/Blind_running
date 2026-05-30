package com.guiderun.app.ui.blind

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.guiderun.app.R
import com.guiderun.app.accessibility.TtsManager
import com.guiderun.app.accessibility.speakPageEntry
import com.guiderun.app.databinding.FragmentBlindStatsBinding
import com.guiderun.app.util.EdgeToEdgeHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BlindStatsFragment : Fragment() {

    private var _binding: FragmentBlindStatsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BlindStatsViewModel by viewModels()

    @Inject
    lateinit var ttsManager: TtsManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentBlindStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        EdgeToEdgeHelper.applyInsets(view)
        ttsManager.acquire()

        observeUiState()
    }

    override fun onResume() {
        super.onResume()
        speakPageEntry(ttsManager, R.string.tts_page_blind_stats, R.string.tts_hint_blind_stats)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        ttsManager.release()
        _binding = null
    }

    private var lastAnnouncedState: BlindStatsUiState? = null

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE

                    if (!state.isLoading) {
                        binding.tvTotalRuns.text = getString(R.string.stats_total_runs, state.totalRuns)
                        binding.tvTotalDistance.text = getString(R.string.stats_total_distance, formatDistance(state.totalDistanceMeters))
                        binding.tvTotalDuration.text = getString(R.string.stats_total_duration, formatDuration(state.totalDurationMinutes))
                        binding.tvCurrentMonthRuns.text = getString(R.string.stats_current_month_runs, state.currentMonthRuns)

                        state.averageRunDurationMinutes?.let { avg ->
                            binding.tvAverageDuration.text = getString(R.string.stats_average_duration, avg)
                            binding.tvAverageDuration.visibility = View.VISIBLE
                            binding.dividerAvg.visibility = View.VISIBLE
                        } ?: run {
                            binding.tvAverageDuration.visibility = View.GONE
                            binding.dividerAvg.visibility = View.GONE
                        }

                        // 只在状态变化时播报
                        if (lastAnnouncedState != state) {
                            lastAnnouncedState = state
                            val avgText = state.averageRunDurationMinutes?.let { "，平均时长${it}分钟" } ?: ""
                            val announcement = "总计跑步${state.totalRuns}次，" +
                                "总距离${formatDistance(state.totalDistanceMeters)}公里，" +
                                "总时长${formatDuration(state.totalDurationMinutes)}，" +
                                "本月跑步${state.currentMonthRuns}次$avgText"
                            ttsManager.speak(announcement, TtsManager.Priority.NORMAL)
                        }
                    }

                    state.error?.let { error ->
                        ttsManager.speak(error, TtsManager.Priority.INTERACTION)
                    }
                }
            }
        }
    }

    private fun formatDistance(meters: Long): String {
        return if (meters >= 1000) {
            String.format(java.util.Locale.ROOT, "%.1f", meters / 1000.0)
        } else {
            "$meters"
        }
    }

    private fun formatDuration(minutes: Int): String {
        return if (minutes >= 60) {
            "${minutes / 60}小时${minutes % 60}分钟"
        } else {
            "${minutes}分钟"
        }
    }
}
