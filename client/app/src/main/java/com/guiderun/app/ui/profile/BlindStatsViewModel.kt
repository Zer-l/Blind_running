package com.guiderun.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guiderun.app.domain.model.RunRequest
import com.guiderun.app.domain.model.RunRequestStatus
import com.guiderun.app.domain.repository.RunRequestRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "加载失败",
                    )
                }
        }
    }

    private fun calculateStats(requests: List<RunRequest>) {
        val closed = requests.filter { it.status == RunRequestStatus.CLOSED }

        val totalDistance: Long = closed.sumOf { (it.actualDistanceMeters ?: 0).toLong() }
        val totalDurationSeconds: Int = closed.sumOf { it.actualDurationSeconds ?: 0 }
        val totalDurationMinutes: Int = totalDurationSeconds / 60

        val averageDuration = if (closed.isNotEmpty()) {
            totalDurationMinutes / closed.size
        } else {
            null
        }

        // 计算本月跑步次数
        val calendar = Calendar.getInstance()
        val currentMonth = calendar.get(Calendar.MONTH)
        val currentYear = calendar.get(Calendar.YEAR)

        val currentMonthRuns = closed.count { request ->
            request.closedAt?.let { closedAt ->
                calendar.timeInMillis = closedAt.toLong()
                calendar.get(Calendar.MONTH) == currentMonth &&
                    calendar.get(Calendar.YEAR) == currentYear
            } ?: false
        }

        _uiState.value = _uiState.value.copy(
            totalRuns = closed.size,
            totalDistanceMeters = totalDistance,
            totalDurationMinutes = totalDurationMinutes,
            currentMonthRuns = currentMonthRuns,
            averageRunDurationMinutes = averageDuration,
            isLoading = false,
        )
    }
}
