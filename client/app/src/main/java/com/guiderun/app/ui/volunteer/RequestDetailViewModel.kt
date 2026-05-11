package com.guiderun.app.ui.volunteer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guiderun.app.domain.exception.RequestConflictException
import com.guiderun.app.domain.model.RunRequest
import com.guiderun.app.domain.repository.RunRequestRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RequestDetailUiState(
    val request: RunRequest? = null,
    val isLoading: Boolean = false,
    val isAccepting: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface RequestDetailNavEvent {
    data class ToNavigating(val requestId: String) : RequestDetailNavEvent
}

@HiltViewModel
class RequestDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val runRequestRepository: RunRequestRepository,
) : ViewModel() {

    private val requestId: String = checkNotNull(savedStateHandle["requestId"])

    private val _uiState = MutableStateFlow(RequestDetailUiState())
    val uiState = _uiState.asStateFlow()

    private val _navEvent = MutableSharedFlow<RequestDetailNavEvent>(replay = 0)
    val navEvent: SharedFlow<RequestDetailNavEvent> = _navEvent.asSharedFlow()

    /** 当前是否已有进行中订单：用于禁用接单按钮。 */
    val activeRequest: StateFlow<RunRequest?> = runRequestRepository.activeRequest
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        loadRequest()
    }

    private fun loadRequest() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            runRequestRepository.getRunRequest(requestId)
                .onSuccess { req -> _uiState.update { it.copy(request = req, isLoading = false) } }
                .onFailure { _uiState.update { it.copy(isLoading = false) } }
        }
    }

    fun onAccept() {
        // 前端兜底：有进行中订单时拒绝；服务端 HAS_ACTIVE_ORDER 也是兜底保护
        if (activeRequest.value != null) {
            _uiState.update { it.copy(errorMessage = "您已有进行中的订单，请先处理后再接单") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isAccepting = true) }
            runRequestRepository.accept(requestId)
                .onSuccess { _navEvent.emit(RequestDetailNavEvent.ToNavigating(requestId)) }
                .onFailure { e ->
                    val msg = when {
                        e is RequestConflictException -> "手慢了，该订单已被接"
                        e.message?.contains("进行中") == true -> e.message!!
                        else -> "接单失败，请重试"
                    }
                    _uiState.update { it.copy(isAccepting = false, errorMessage = msg) }
                }
        }
    }

    fun onErrorShown() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
