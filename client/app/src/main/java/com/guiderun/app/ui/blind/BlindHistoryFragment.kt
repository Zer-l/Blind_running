package com.guiderun.app.ui.blind

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
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

    private val adapter = HistoryAdapter { requestId ->
        val bundle = Bundle().apply { putString("requestId", requestId) }
        findNavController().navigate(R.id.action_history_to_trackPlayback, bundle)
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
            val filter = when (checkedIds.firstOrNull()) {
                R.id.chip_closed -> HistoryFilter.CLOSED
                R.id.chip_aborted -> HistoryFilter.ABORTED
                else -> HistoryFilter.ALL
            }
            viewModel.setFilter(filter)
        }

        binding.rvHistory.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val total = lm.itemCount
                val last = lm.findLastVisibleItemPosition()
                if (last >= total - 3) {
                    viewModel.loadMore()
                }
            }
        })

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
                    binding.tvStatRuns.text = "${state.totalRuns}"
                    binding.tvStatDistance.text = "%.1f km".format(state.totalDistanceKm)
                    binding.tvStatDuration.text = "%.1f h".format(state.totalDurationHours)
                    binding.tvStatAborted.text = "${state.totalAborted}"
                    adapter.submitList(state.requests)
                }
            }
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
}

private class HistoryAdapter(
    private val onItemClick: (String) -> Unit,
) : ListAdapter<RunRequest, HistoryViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemBlindHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return HistoryViewHolder(binding, onItemClick)
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
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(request: RunRequest) {
        val ctx = binding.root.context
        binding.tvLocation.text = request.meetingLocation.description
        binding.tvVolunteer.text = ctx.getString(
            R.string.blind_history_volunteer,
            request.volunteer?.nickname ?: "未知"
        )
        binding.tvStatus.text = when (request.status) {
            RunRequestStatus.CLOSED -> "已完成"
            RunRequestStatus.ABORTED -> "已取消"
            RunRequestStatus.FINISHED -> "待关闭"
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
    }
}
