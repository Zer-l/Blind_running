package com.guiderun.app.ui.volunteer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guiderun.app.domain.model.Badge
import com.guiderun.app.domain.model.RunRequest
import com.guiderun.app.domain.model.RunRequestStatus
import com.guiderun.app.domain.repository.RunRequestRepository
import com.guiderun.app.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VolunteerHistoryUiState(
    val requests: List<RunRequest> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val totalRuns: Int = 0,
    val totalDistanceKm: Float = 0f,
    val totalDurationHours: Float = 0f,
    val badges: List<Badge> = emptyList(),
)

@HiltViewModel
class VolunteerHistoryViewModel @Inject constructor(
    private val runRequestRepository: RunRequestRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VolunteerHistoryUiState())
    val uiState: StateFlow<VolunteerHistoryUiState> = _uiState.asStateFlow()

    init {
        loadHistory()
    }

    fun loadHistory() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            runRequestRepository.getMyRequests(role = "VOLUNTEER", page = 0)
                .onSuccess { list ->
                    val closed = list.filter { it.status == RunRequestStatus.CLOSED }
                    val totalDist = closed.sumOf { it.actualDistanceMeters ?: 0 }
                    val totalDur = closed.sumOf { it.actualDurationSeconds ?: 0 }
                    _uiState.update {
                        it.copy(
                            requests = list,
                            isLoading = false,
                            totalRuns = closed.size,
                            totalDistanceKm = totalDist / 1000f,
                            totalDurationHours = totalDur / 3600f,
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
                }
            // 加载徽章
            userRepository.getVolunteerStats()
                .onSuccess { stats ->
                    _uiState.update { it.copy(badges = stats.badges) }
                }
        }
    }

    fun onErrorShown() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
