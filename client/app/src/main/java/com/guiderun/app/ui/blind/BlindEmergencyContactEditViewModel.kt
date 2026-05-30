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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BlindEmergencyContactEditUiState(
    val name: String = "",
    val phone: String = "",
    val relationship: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isEditMode: Boolean = false,
)

sealed class BlindEmergencyContactEditEvent {
    data object Saved : BlindEmergencyContactEditEvent()
    data class Error(val message: String) : BlindEmergencyContactEditEvent()
}

@HiltViewModel
class BlindEmergencyContactEditViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BlindEmergencyContactEditUiState())
    val uiState: StateFlow<BlindEmergencyContactEditUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<BlindEmergencyContactEditEvent>()
    val events: SharedFlow<BlindEmergencyContactEditEvent> = _events.asSharedFlow()

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
                            _events.emit(BlindEmergencyContactEditEvent.Error(context.getString(R.string.contact_not_found)))
                        }
                    }
                    .onFailure { e ->
                        Timber.e(e, "EmergencyContactEditVM: load failed")
                        _uiState.value = _uiState.value.copy(isLoading = false)
                        _events.emit(BlindEmergencyContactEditEvent.Error(context.getString(R.string.error_load_failed)))
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
            val msg = context.getString(R.string.contact_name_required)
            _uiState.value = state.copy(error = msg)
            viewModelScope.launch { _events.emit(BlindEmergencyContactEditEvent.Error(msg)) }
            return
        }
        if (state.phone.isBlank() || !state.phone.matches("^1[3-9]\\d{9}$".toRegex())) {
            val msg = context.getString(R.string.contact_phone_invalid)
            _uiState.value = state.copy(error = msg)
            viewModelScope.launch { _events.emit(BlindEmergencyContactEditEvent.Error(msg)) }
            return
        }
        if (state.relationship.isBlank()) {
            val msg = context.getString(R.string.contact_relationship_required)
            _uiState.value = state.copy(error = msg)
            viewModelScope.launch { _events.emit(BlindEmergencyContactEditEvent.Error(msg)) }
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
                    _events.emit(BlindEmergencyContactEditEvent.Saved)
                }
                .onFailure { e ->
                    Timber.e(e, "EmergencyContactEditVM: save failed")
                    // 服务端"已达上限"错误转友好提示；其余统一用通用保存失败文案，不透传 e.message
                    val errorMsg = if (e.message?.contains("已达上限") == true) {
                        context.getString(R.string.contact_limit_reached)
                    } else {
                        context.getString(R.string.error_save_failed)
                    }
                    _uiState.value = _uiState.value.copy(isLoading = false, error = errorMsg)
                    _events.emit(BlindEmergencyContactEditEvent.Error(errorMsg))
                }
        }
    }
}
