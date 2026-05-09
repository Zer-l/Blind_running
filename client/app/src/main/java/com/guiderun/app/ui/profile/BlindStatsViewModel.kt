package com.guiderun.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guiderun.app.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BlindStatsUiState(
    val totalRuns: Int = 0,
    val totalDistanceMeters: Long = 0,
    val totalDurationMinutes: Int = 0,
    val currentMonthRuns: Int = 0,
    val averageRunDurationMinutes: Int? = null,
    val isLoading: Boolean = true, // 初始为 true，避免初始状态触发播报
    val error: String? = null,
)

@HiltViewModel
class BlindStatsViewModel @Inject constructor(
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BlindStatsUiState())
    val uiState: StateFlow<BlindStatsUiState> = _uiState.asStateFlow()

    init {
        loadStats()
    }

    private fun loadStats() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            userRepository.getBlindStats()
                .onSuccess { stats ->
                    _uiState.value = _uiState.value.copy(
                        totalRuns = stats.totalRuns,
                        totalDistanceMeters = stats.totalDistanceMeters,
                        totalDurationMinutes = stats.totalDurationMinutes,
                        currentMonthRuns = stats.currentMonthRuns,
                        averageRunDurationMinutes = stats.averageRunDurationMinutes,
                        isLoading = false,
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "加载失败",
                    )
                }
        }
    }
}
