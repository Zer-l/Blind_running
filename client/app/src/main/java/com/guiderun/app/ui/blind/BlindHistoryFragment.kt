package com.guiderun.app.ui.blind

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.guiderun.app.R
import com.guiderun.app.accessibility.HapticFeedback
import com.guiderun.app.accessibility.voice.VoiceCommand
import com.guiderun.app.accessibility.voice.bindVoiceCommands
import javax.inject.Inject
import com.guiderun.app.databinding.FragmentBlindHistoryBinding
import com.guiderun.app.databinding.ItemBlindHistoryBinding
import com.guiderun.app.domain.model.RunRequest
import com.guiderun.app.domain.model.RunRequestStatus
import com.guiderun.app.util.EdgeToEdgeHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BlindHistoryFragment : Fragment() {

    private val viewModel: BlindHistoryViewModel by viewModels()
    private var _binding: FragmentBlindHistoryBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var hapticFeedback: HapticFeedback

    private val adapter by lazy {
        HistoryAdapter(
            onItemClick = { requestId ->
                hapticFeedback.tick()
                val bundle = Bundle().apply { putString("requestId", requestId) }
                findNavController().navigate(R.id.action_history_to_trackPlayback, bundle)
            },
            onReviewClick = { requestId ->
                hapticFeedback.tick()
                val bundle = Bundle().apply { putString("requestId", requestId) }
                findNavController().navigate(R.id.action_history_to_review, bundle)
            },
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentBlindHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        EdgeToEdgeHelper.applyInsets(view)
        val lm = LinearLayoutManager(requireContext())
        binding.rvHistory.layoutManager = lm
        binding.rvHistory.adapter = adapter
        binding.btnRetry.setOnClickListener { viewModel.loadHistory() }

        binding.refreshLayout.setOnRefreshListener { viewModel.loadHistory() }

        binding.chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            hapticFeedback.tick()
            val filter = when (checkedIds.firstOrNull()) {
                R.id.chip_closed -> HistoryFilter.CLOSED
                R.id.chip_aborted -> HistoryFilter.ABORTED
                else -> HistoryFilter.ALL
            }
            viewModel.setFilter(filter)
        }

        // 分页监听改到 NestedScrollView（RecyclerView 已 nestedScrollingEnabled=false 不再独立滚动）
        binding.nestedScroll.setOnScrollChangeListener(
            NestedScrollView.OnScrollChangeListener { v, _, scrollY, _, oldScrollY ->
                if (scrollY <= oldScrollY) return@OnScrollChangeListener
                val child = v.getChildAt(0) ?: return@OnScrollChangeListener
                val maxScroll = child.measuredHeight - v.measuredHeight
                if (scrollY >= maxScroll - LOAD_MORE_TRIGGER_PX) {
                    viewModel.loadMore()
                }
            }
        )

        setupVoiceCommands()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val hasError = state.errorMessage != null
                    val isEmpty = !state.isLoading && !hasError && state.requests.isEmpty()
                    val hasData = state.requests.isNotEmpty()

                    binding.refreshLayout.isVisible = hasData || state.isLoadingMore
                    binding.refreshLayout.isRefreshing = state.isLoading || state.isLoadingMore
                    binding.tvEmpty.isVisible = isEmpty
                    binding.layoutError.isVisible = hasError
                    binding.tvError.text = state.errorMessage
                    binding.cardStats.isVisible = hasData
                    binding.tvStatRuns.text = getString(
                        R.string.blind_ui_history_stat_runs_format, state.totalRuns
                    )
                    binding.tvStatDistance.text = getString(
                        R.string.blind_ui_history_stat_distance_format, state.totalDistanceKm
                    )
                    binding.tvStatDuration.text = getString(
                        R.string.blind_ui_history_stat_duration_format, state.totalDurationHours
                    )
                    binding.tvStatAborted.text = getString(
                        R.string.blind_ui_history_stat_aborted_format, state.totalAborted
                    )
                    adapter.submitList(state.requests)
                }
            }
        }
    }

    /** 历史页：RETRY / REFRESH 重新加载；FILTER_xxx 切换筛选 chip */
    private fun setupVoiceCommands() = bindVoiceCommands { cmd ->
        when (cmd) {
            VoiceCommand.RETRY, VoiceCommand.REFRESH -> {
                viewModel.loadHistory()
                true
            }
            VoiceCommand.FILTER_FINISHED -> {
                binding.chipGroupFilter.check(R.id.chip_closed)
                viewModel.setFilter(HistoryFilter.CLOSED)
                true
            }
            VoiceCommand.FILTER_CANCELLED -> {
                binding.chipGroupFilter.check(R.id.chip_aborted)
                viewModel.setFilter(HistoryFilter.ABORTED)
                true
            }
            VoiceCommand.FILTER_ALL -> {
                binding.chipGroupFilter.check(R.id.chip_all)
                viewModel.setFilter(HistoryFilter.ALL)
                true
            }
            else -> false
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onScreenResumed()
    }

    override fun onPause() {
        super.onPause()
        viewModel.onScreenPaused()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        // 距底部 300px 时预加载下一页
        const val LOAD_MORE_TRIGGER_PX = 300
    }
}

private class HistoryAdapter(
    private val onItemClick: (String) -> Unit,
    private val onReviewClick: (String) -> Unit,
) : ListAdapter<RunRequest, HistoryViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemBlindHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return HistoryViewHolder(binding, onItemClick, onReviewClick)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<RunRequest>() {
            override fun areItemsTheSame(a: RunRequest, b: RunRequest) = a.id == b.id
            override fun areContentsTheSame(a: RunRequest, b: RunRequest) = a == b
        }
    }
}

private class HistoryViewHolder(
    private val binding: ItemBlindHistoryBinding,
    private val onItemClick: (String) -> Unit,
    private val onReviewClick: (String) -> Unit,
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(request: RunRequest) {
        val ctx = binding.root.context
        binding.tvLocation.text = request.meetingLocation.description
        binding.tvVolunteer.text = ctx.getString(
            R.string.blind_history_volunteer,
            request.volunteer?.nickname ?: "未知"
        )
        binding.tvStatus.text = when {
            request.status.isCompleted() -> "已完成"
            request.status == RunRequestStatus.ABORTED -> "已取消"
            else -> request.status.name
        }
        val distKm = (request.actualDistanceMeters ?: 0) / 1000f
        binding.tvDistance.text = if (distKm > 0) {
            ctx.getString(R.string.blind_history_actual_distance, distKm)
        } else {
            ""
        }
        val durMin = (request.actualDurationSeconds ?: 0) / 60
        binding.tvDuration.text = if (durMin > 0) {
            ctx.getString(R.string.blind_history_actual_duration, durMin)
        } else {
            "${request.expectedDurationMinutes}分钟（预计）"
        }
        binding.root.setOnClickListener { onItemClick(request.id) }

        // 补评按钮：已完成 + 自己未评 才显示。myReviewSubmitted == null 时按"未评"显示（兼容旧数据）
        val canReview = request.status.isCompleted() && request.myReviewSubmitted == false
        binding.btnReview.isVisible = canReview
        binding.btnReview.setOnClickListener { onReviewClick(request.id) }
    }
}
