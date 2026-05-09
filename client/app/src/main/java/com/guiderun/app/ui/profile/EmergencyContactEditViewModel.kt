package com.guiderun.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guiderun.app.domain.model.EmergencyContact
import com.guiderun.app.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EmergencyContactEditUiState(
    val name: String = "",
    val phone: String = "",
    val relationship: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isEditMode: Boolean = false,
)

sealed class EmergencyContactEditEvent {
    data object Saved : EmergencyContactEditEvent()
    data class Error(val message: String) : EmergencyContactEditEvent()
}

@HiltViewModel
class EmergencyContactEditViewModel @Inject constructor(
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EmergencyContactEditUiState())
    val uiState: StateFlow<EmergencyContactEditUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<EmergencyContactEditEvent>()
    val events: SharedFlow<EmergencyContactEditEvent> = _events.asSharedFlow()

    private var editIndex: Int = -1

    fun initEditMode(index: Int) {
        editIndex = index
        if (index >= 0) {
            _uiState.value = _uiState.value.copy(isLoading = true, isEditMode = true)
            viewModelScope.launch {
                userRepository.getEmergencyContacts()
                    .onSuccess { contacts ->
                        if (index < contacts.size) {
                            val contact = contacts[index]
                            _uiState.value = _uiState.value.copy(
                                name = contact.name,
                                phone = contact.phone,
                                relationship = contact.relationship,
                                isLoading = false,
                            )
                        } else {
                            _uiState.value = _uiState.value.copy(isLoading = false)
                            _events.emit(EmergencyContactEditEvent.Error("联系人不存在"))
                        }
                    }
                    .onFailure { e ->
                        _uiState.value = _uiState.value.copy(isLoading = false)
                        _events.emit(EmergencyContactEditEvent.Error(e.message ?: "加载失败"))
                    }
            }
        }
    }

    fun updateName(name: String) {
        _uiState.value = _uiState.value.copy(name = name)
    }

    fun updatePhone(phone: String) {
        _uiState.value = _uiState.value.copy(phone = phone)
    }

    fun updateRelationship(relationship: String) {
        _uiState.value = _uiState.value.copy(relationship = relationship)
    }

    fun save() {
        val state = _uiState.value

        if (state.name.isBlank()) {
            _uiState.value = state.copy(error = "姓名不能为空")
            viewModelScope.launch { _events.emit(EmergencyContactEditEvent.Error("姓名不能为空")) }
            return
        }
        if (state.phone.isBlank() || !state.phone.matches("^1[3-9]\\d{9}$".toRegex())) {
            _uiState.value = state.copy(error = "手机号格式不正确")
            viewModelScope.launch { _events.emit(EmergencyContactEditEvent.Error("手机号格式不正确")) }
            return
        }
        if (state.relationship.isBlank()) {
            _uiState.value = state.copy(error = "关系不能为空")
            viewModelScope.launch { _events.emit(EmergencyContactEditEvent.Error("关系不能为空")) }
            return
        }

        val contact = EmergencyContact(
            name = state.name,
            phone = state.phone,
            relationship = state.relationship,
        )

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val result = if (editIndex >= 0) {
                userRepository.updateEmergencyContact(editIndex, contact)
            } else {
                userRepository.addEmergencyContact(contact)
            }

            result
                .onSuccess {
                    _events.emit(EmergencyContactEditEvent.Saved)
                }
                .onFailure { e ->
                    val errorMsg = when {
                        e.message?.contains("已达上限") == true -> "紧急联系人数量已达上限，最多5位"
                        else -> e.message ?: "保存失败"
                    }
                    _uiState.value = _uiState.value.copy(isLoading = false, error = errorMsg)
                    _events.emit(EmergencyContactEditEvent.Error(errorMsg))
                }
        }
    }
}
