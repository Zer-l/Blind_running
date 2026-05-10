package com.guiderun.app.ui.volunteer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guiderun.app.domain.model.CreateReviewParams
import com.guiderun.app.domain.repository.RunRequestRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VolunteerReviewUiState(
    val rating: Int = 5,
    val selectedTags: Set<String> = emptySet(),
    val comment: String = "",
    val isSubmitting: Boolean = false,
    /** 视障用户手机号；FINISHED 阶段服务端已下发，供顶栏拨号按钮使用。 */
    val peerPhone: String? = null,
    val errorMessage: String? = null,
)

sealed interface VolunteerReviewNavEvent {
    data object ToHome : VolunteerReviewNavEvent
}

@HiltViewModel
class VolunteerReviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val runRequestRepository: RunRequestRepository,
) : ViewModel() {

    private val requestId: String = checkNotNull(savedStateHandle["requestId"])

    private val _uiState = MutableStateFlow(VolunteerReviewUiState())
    val uiState: StateFlow<VolunteerReviewUiState> = _uiState.asStateFlow()

    private val _navEvent = MutableSharedFlow<VolunteerReviewNavEvent>(replay = 0)
    val navEvent: SharedFlow<VolunteerReviewNavEvent> = _navEvent.asSharedFlow()

    init {
        viewModelScope.launch {
            runRequestRepository.getRunRequest(requestId)
                .onSuccess { req ->
                    _uiState.update { it.copy(peerPhone = req.blindRunner?.phone) }
                }
        }
    }

    fun setRating(rating: Int) {
        _uiState.update { it.copy(rating = rating.coerceIn(1, 5)) }
    }

    fun toggleTag(tag: String) {
        _uiState.update {
            val newTags = if (tag in it.selectedTags) it.selectedTags - tag else it.selectedTags + tag
            it.copy(selectedTags = newTags)
        }
    }

    fun setComment(comment: String) {
        _uiState.update { it.copy(comment = comment) }
    }

    fun submit() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            val state = _uiState.value
            runRequestRepository.createReview(
                requestId = requestId,
                params = CreateReviewParams(
                    rating = state.rating,
                    tags = state.selectedTags.toList(),
                    comment = state.comment.ifBlank { null },
                ),
            ).onSuccess {
                _navEvent.emit(VolunteerReviewNavEvent.ToHome)
            }.onFailure { e ->
                _uiState.update { it.copy(isSubmitting = false, errorMessage = e.message) }
            }
        }
    }

    fun skip() {
        viewModelScope.launch {
            _navEvent.emit(VolunteerReviewNavEvent.ToHome)
        }
    }

    fun onErrorShown() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
