package com.guiderun.app.ui.blind

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guiderun.app.R
import com.guiderun.app.domain.model.RunRequest
import com.guiderun.app.domain.model.RunRequestStatus
import com.guiderun.app.domain.repository.RunRequestRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class BlindStatsUiState(
    val totalRuns: Int = 0,
    val totalDistanceMeters: Long = 0,
    val totalDurationMinutes: Int = 0,
    val currentMonthRuns: Int = 0,
    val averageRunDurationMinutes: Int? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class BlindStatsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val runRequestRepository: RunRequestRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BlindStatsUiState())
    val uiState: StateFlow<BlindStatsUiState> = _uiState.asStateFlow()

    init {
        loadStats()
    }

    private fun loadStats() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runRequestRepository.getMyRequests(role = "BLIND", page = 0)
                .onSuccess { list ->
                    calculateStats(list)
                }
                .onFailure { e ->
                    Timber.e(e, "BlindStatsVM: loadStats failed")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = context.getString(R.string.error_load_failed),
                    )
                }
        }
    }

    private fun calculateStats(requests: List<RunRequest>) {
        // "已完成"包含 FINISHED + CLOSED，与历史页对齐
        val completed = requests.filter { it.status.isCompleted() }

        val totalDistance: Long = completed.sumOf { (it.actualDistanceMeters ?: 0).toLong() }
        val totalDurationSeconds: Int = completed.sumOf { it.actualDurationSeconds ?: 0 }
        val totalDurationMinutes: Int = totalDurationSeconds / 60

        val averageDuration = if (completed.isNotEmpty()) {
            totalDurationMinutes / completed.size
        } else {
            null
        }

        // 本月跑步次数：FINISHED 没有 closedAt，用 runEndedAt 兜底（跑步结束时间）
        val calendar = Calendar.getInstance()
        val currentMonth = calendar.get(Calendar.MONTH)
        val currentYear = calendar.get(Calendar.YEAR)

        val currentMonthRuns = completed.count { request ->
            val timestamp = request.closedAt ?: request.runEndedAt ?: return@count false
            calendar.timeInMillis = timestamp.toLong()
            calendar.get(Calendar.MONTH) == currentMonth &&
                calendar.get(Calendar.YEAR) == currentYear
        }

        _uiState.value = _uiState.value.copy(
            totalRuns = completed.size,
            totalDistanceMeters = totalDistance,
            totalDurationMinutes = totalDurationMinutes,
            currentMonthRuns = currentMonthRuns,
            averageRunDurationMinutes = averageDuration,
            isLoading = false,
        )
    }
}
