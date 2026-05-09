package com.guiderun.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guiderun.app.domain.model.EmergencyContact
import com.guiderun.app.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EmergencyContactListUiState(
    val contacts: List<EmergencyContact> = emptyList(),
    val isLoading: Boolean = true, // 初始为 true，避免初始空列表触发空状态播报
    val isLoaded: Boolean = false, // 标记是否已完成首次加载
    val error: String? = null,
)

@HiltViewModel
class EmergencyContactListViewModel @Inject constructor(
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EmergencyContactListUiState())
    val uiState: StateFlow<EmergencyContactListUiState> = _uiState.asStateFlow()

    init {
        loadContacts()
    }

    fun loadContacts() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            userRepository.getEmergencyContacts()
                .onSuccess { contacts ->
                    _uiState.value = _uiState.value.copy(
                        contacts = contacts,
                        isLoading = false,
                        isLoaded = true,
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoaded = true,
                        error = e.message ?: "加载失败",
                    )
                }
        }
    }

    fun deleteContact(index: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            userRepository.deleteEmergencyContact(index)
                .onSuccess { contacts ->
                    _uiState.value = _uiState.value.copy(
                        contacts = contacts,
                        isLoading = false,
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "删除失败",
                    )
                }
        }
    }
}
