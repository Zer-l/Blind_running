package com.guiderun.app.ui.blind

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guiderun.app.R
import com.guiderun.app.accessibility.TtsManager
import com.guiderun.app.domain.model.RunRequest
import com.guiderun.app.domain.model.RunRequestStatus
import com.guiderun.app.domain.repository.RunRequestRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.content.Context
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
    @ApplicationContext private val context: Context,
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
                        ttsManager.speak(context.getString(R.string.blind_history_empty), TtsManager.Priority.HIGH)
                    } else {
                        val state = _uiState.value
                        ttsManager.speak(context.getString(R.string.tts_history_loaded, state.totalRuns, "%.1f".format(state.totalDistanceKm), "%.1f".format(state.totalDurationHours)), TtsManager.Priority.HIGH)
                    }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "加载失败，请重试")
                    }
                    ttsManager.speak(context.getString(R.string.error_network), TtsManager.Priority.HIGH)
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
        // "已完成"包含 FINISHED + CLOSED：跑步已结束的都计入累计统计，
        // 不再因为对方没评价就把这单排除在统计外
        val completed = allRequests.filter { it.status.isCompleted() }
        val totalDist = completed.sumOf { it.actualDistanceMeters ?: 0 }
        val totalDur = completed.sumOf { it.actualDurationSeconds ?: 0 }
        val aborted = allRequests.count { it.status == RunRequestStatus.ABORTED }
        val filter = _uiState.value.statusFilter
        _uiState.update {
            it.copy(
                requests = applyFilter(allRequests, filter),
                isLoading = false,
                isLoadingMore = false,
                hasMore = fetched.size >= PAGE_SIZE,
                totalRuns = completed.size,
                totalDistanceKm = totalDist / 1000f,
                totalDurationHours = totalDur / 3600f,
                totalAborted = aborted,
            )
        }
    }

    fun setFilter(filter: HistoryFilter) {
        val count = when (filter) {
            HistoryFilter.ALL -> allRequests.size
            HistoryFilter.CLOSED -> allRequests.count { it.status.isCompleted() }
            HistoryFilter.ABORTED -> allRequests.count { it.status == RunRequestStatus.ABORTED }
        }
        ttsManager.speak(
            when (filter) {
                HistoryFilter.ALL -> context.getString(R.string.tts_history_filter_all, allRequests.size)
                HistoryFilter.CLOSED -> context.getString(R.string.tts_history_filter_closed, count)
                HistoryFilter.ABORTED -> context.getString(R.string.tts_history_filter_aborted, count)
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
            HistoryFilter.CLOSED -> list.filter { it.status.isCompleted() }
            HistoryFilter.ABORTED -> list.filter { it.status == RunRequestStatus.ABORTED }
        }

    fun onErrorShown() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun onScreenResumed() {
        ttsManager.acquire()
        if (!hasAnnouncedPage) {
            hasAnnouncedPage = true
            viewModelScope.launch {
                ttsManager.speakAndWait(context.getString(R.string.tts_page_blind_history), TtsManager.Priority.HIGH)
                ttsManager.speak(context.getString(R.string.tts_hint_blind_history), TtsManager.Priority.HIGH)
            }
        }
    }

    fun onScreenPaused() {
        ttsManager.release()
    }
}
