package com.guiderun.app.ui.blind

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guiderun.app.accessibility.TtsManager
import com.guiderun.app.domain.model.RunRequest
import com.guiderun.app.domain.model.RunRequestStatus
import com.guiderun.app.domain.repository.RunRequestRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val PAGE_SIZE = 20

enum class HistoryFilter { ALL, CLOSED, ABORTED }

data class BlindHistoryUiState(
    val requests: List<RunRequest> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null,
    val totalRuns: Int = 0,
    val totalDistanceKm: Float = 0f,
    val totalDurationHours: Float = 0f,
    val totalAborted: Int = 0,
    val hasMore: Boolean = true,
    val statusFilter: HistoryFilter = HistoryFilter.ALL,
)

@HiltViewModel
class BlindHistoryViewModel @Inject constructor(
    private val runRequestRepository: RunRequestRepository,
    private val ttsManager: TtsManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BlindHistoryUiState())
    val uiState: StateFlow<BlindHistoryUiState> = _uiState.asStateFlow()

    private var currentPage = 0
    private var allRequests: List<RunRequest> = emptyList()
    private var hasAnnouncedPage = false

    init {
        loadHistory()
    }

    fun loadHistory() {
        viewModelScope.launch {
            currentPage = 0
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runRequestRepository.getMyRequests(role = "BLIND", page = 0)
                .onSuccess { list ->
                    applyList(list, append = false)
                    if (list.isEmpty()) {
                        ttsManager.speak("暂无跑步记录")
                    } else {
                        val state = _uiState.value
                        val distText = "%.1f".format(state.totalDistanceKm)
                        val durText = "%.1f".format(state.totalDurationHours)
                        ttsManager.speak("共${list.size}条记录，已完成${state.totalRuns}次，总距离${distText}公里，总时长${durText}小时")
                    }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "加载失败，请重试")
                    }
                    ttsManager.speak("加载失败，请点击重试按钮重新加载")
                }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            val nextPage = currentPage + 1
            runRequestRepository.getMyRequests(role = "BLIND", page = nextPage)
                .onSuccess { list ->
                    currentPage = nextPage
                    applyList(list, append = true)
                }
                .onFailure {
                    _uiState.update { it.copy(isLoadingMore = false) }
                }
        }
    }

    private fun applyList(fetched: List<RunRequest>, append: Boolean) {
        allRequests = if (append) allRequests + fetched else fetched
        val closed = allRequests.filter { it.status == RunRequestStatus.CLOSED }
        val totalDist = closed.sumOf { it.actualDistanceMeters ?: 0 }
        val totalDur = closed.sumOf { it.actualDurationSeconds ?: 0 }
        val aborted = allRequests.count { it.status == RunRequestStatus.ABORTED }
        val filter = _uiState.value.statusFilter
        _uiState.update {
            it.copy(
                requests = applyFilter(allRequests, filter),
                isLoading = false,
                isLoadingMore = false,
                hasMore = fetched.size >= PAGE_SIZE,
                totalRuns = closed.size,
                totalDistanceKm = totalDist / 1000f,
                totalDurationHours = totalDur / 3600f,
                totalAborted = aborted,
            )
        }
    }

    fun setFilter(filter: HistoryFilter) {
        val count = when (filter) {
            HistoryFilter.ALL -> allRequests.size
            HistoryFilter.CLOSED -> allRequests.count { it.status == RunRequestStatus.CLOSED }
            HistoryFilter.ABORTED -> allRequests.count { it.status == RunRequestStatus.ABORTED }
        }
        ttsManager.speak(
            when (filter) {
                HistoryFilter.ALL -> "显示全部，共${allRequests.size}条"
                HistoryFilter.CLOSED -> "筛选已完成，共${count}条"
                HistoryFilter.ABORTED -> "筛选已取消，共${count}条"
            }
        )
        _uiState.update {
            it.copy(
                statusFilter = filter,
                requests = applyFilter(allRequests, filter),
            )
        }
    }

    private fun applyFilter(list: List<RunRequest>, filter: HistoryFilter): List<RunRequest> =
        when (filter) {
            HistoryFilter.ALL -> list
            HistoryFilter.CLOSED -> list.filter { it.status == RunRequestStatus.CLOSED }
            HistoryFilter.ABORTED -> list.filter { it.status == RunRequestStatus.ABORTED }
        }

    fun onErrorShown() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun onScreenResumed() {
        ttsManager.acquire()
        if (!hasAnnouncedPage) {
            hasAnnouncedPage = true
            ttsManager.speak("跑步历史记录页面")
        }
    }

    fun onScreenPaused() {
        ttsManager.release()
    }
}
