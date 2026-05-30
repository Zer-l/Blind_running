package com.guiderun.app.ui.volunteer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guiderun.app.R
import com.guiderun.app.domain.model.Badge
import com.guiderun.app.domain.model.RunRequest
import com.guiderun.app.domain.model.RunRequestStatus
import com.guiderun.app.domain.repository.RunRequestRepository
import com.guiderun.app.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class VolunteerHistoryUiState(
    val requests: List<RunRequest> = emptyList(),
    val isLoading: Boolean = true,
    /** 下拉刷新中：与 isLoading 区分，刷新时保留列表不显示整屏 loading。 */
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val totalRuns: Int = 0,
    val totalDistanceKm: Float = 0f,
    val totalDurationHours: Float = 0f,
    val badges: List<Badge> = emptyList(),
)

@HiltViewModel
class VolunteerHistoryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val runRequestRepository: RunRequestRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VolunteerHistoryUiState())
    val uiState: StateFlow<VolunteerHistoryUiState> = _uiState.asStateFlow()

    init {
        loadHistory()
    }

    /** 下拉刷新入口：保留列表，仅显示下拉指示器（isRefreshing），不切整屏 loading。 */
    fun onRefresh() = loadHistory(isRefresh = true)

    fun loadHistory(isRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update {
                if (isRefresh) it.copy(isRefreshing = true, errorMessage = null)
                else it.copy(isLoading = true, errorMessage = null)
            }
            runRequestRepository.getMyRequests(role = "VOLUNTEER", page = 0)
                .onSuccess { list ->
                    // "已完成"包含 FINISHED + CLOSED：跑步已结束的都计入累计
                    val completed = list.filter { it.status.isCompleted() }
                    val totalDist = completed.sumOf { it.actualDistanceMeters ?: 0 }
                    val totalDur = completed.sumOf { it.actualDurationSeconds ?: 0 }
                    _uiState.update {
                        it.copy(
                            requests = list,
                            isLoading = false,
                            isRefreshing = false,
                            totalRuns = completed.size,
                            totalDistanceKm = totalDist / 1000f,
                            totalDurationHours = totalDur / 3600f,
                        )
                    }
                }
                .onFailure { e ->
                    // 不向 UI 透传原始异常（IOException 文本含 ip/port 等内部细节）；细节记日志
                    Timber.e(e, "VolunteerHistoryVM: loadHistory failed")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = context.getString(R.string.error_load_failed),
                        )
                    }
                }
            // 加载徽章
            userRepository.getVolunteerStats()
                .onSuccess { stats ->
                    _uiState.update { it.copy(badges = stats.badges) }
                }
                .onFailure { Timber.w(it, "VolunteerHistoryVM: getVolunteerStats failed") }
        }
    }

    fun onErrorShown() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
