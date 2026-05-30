package com.guiderun.app.ui.blind

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guiderun.app.R
import com.guiderun.app.domain.model.EmergencyContact
import com.guiderun.app.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BlindEmergencyContactListUiState(
    val contacts: List<EmergencyContact> = emptyList(),
    val isLoading: Boolean = true, // 初始为 true，避免初始空列表触发空状态播报
    val isLoaded: Boolean = false, // 标记是否已完成首次加载
    val error: String? = null,
)

@HiltViewModel
class BlindEmergencyContactListViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BlindEmergencyContactListUiState())
    val uiState: StateFlow<BlindEmergencyContactListUiState> = _uiState.asStateFlow()

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
                    Timber.e(e, "EmergencyContactListVM: load failed")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoaded = true,
                        error = context.getString(R.string.error_load_failed),
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
                    Timber.e(e, "EmergencyContactListVM: delete failed")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = context.getString(R.string.error_delete_failed),
                    )
                }
        }
    }
}
