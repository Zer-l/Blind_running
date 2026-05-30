package com.guiderun.app.ui.blind

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guiderun.app.R
import com.guiderun.app.domain.model.BlindProfileUpdate
import com.guiderun.app.domain.model.Gender
import com.guiderun.app.domain.model.UpdateProfileParams
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

data class BlindProfileEditUiState(
    val nickname: String = "",
    val gender: Gender? = null,
    val visionLevel: String = "",
    val preferredPaceSeconds: String = "",
    val preferredDurationMinutes: String = "",
    val medicalNotes: String = "",
    val visualDescription: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
)

sealed class BlindProfileEditEvent {
    data object Saved : BlindProfileEditEvent()
    data class Error(val message: String) : BlindProfileEditEvent()
}

@HiltViewModel
class BlindProfileEditViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BlindProfileEditUiState())
    val uiState: StateFlow<BlindProfileEditUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<BlindProfileEditEvent>()
    val events: SharedFlow<BlindProfileEditEvent> = _events.asSharedFlow()

    init {
        loadProfile()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            userRepository.getMe()
                .onSuccess { user ->
                    val blindProfile = user.blindProfile
                    _uiState.value = _uiState.value.copy(
                        nickname = user.nickname,
                        gender = user.gender,
                        visionLevel = blindProfile?.visionLevel ?: "",
                        preferredPaceSeconds = blindProfile?.preferredPaceSeconds?.toString() ?: "",
                        preferredDurationMinutes = blindProfile?.preferredDurationMinutes?.toString() ?: "",
                        medicalNotes = blindProfile?.medicalNotes ?: "",
                        visualDescription = blindProfile?.visualDescription ?: "",
                    )
                }
        }
    }

    fun updateNickname(nickname: String) {
        _uiState.value = _uiState.value.copy(nickname = nickname)
    }

    fun updateGender(gender: Gender?) {
        _uiState.value = _uiState.value.copy(gender = gender)
    }

    fun updateVisionLevel(visionLevel: String) {
        _uiState.value = _uiState.value.copy(visionLevel = visionLevel)
    }

    fun updatePreferredPaceSeconds(pace: String) {
        _uiState.value = _uiState.value.copy(preferredPaceSeconds = pace)
    }

    fun updatePreferredDurationMinutes(duration: String) {
        _uiState.value = _uiState.value.copy(preferredDurationMinutes = duration)
    }

    fun updateMedicalNotes(notes: String) {
        _uiState.value = _uiState.value.copy(medicalNotes = notes)
    }

    fun updateVisualDescription(description: String) {
        _uiState.value = _uiState.value.copy(visualDescription = description)
    }

    fun save() {
        val state = _uiState.value
        if (state.nickname.isBlank()) {
            _uiState.value = state.copy(error = context.getString(R.string.profile_nickname_required))
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val params = UpdateProfileParams(
                nickname = state.nickname,
                gender = state.gender,
                blindProfile = BlindProfileUpdate(
                    visionLevel = state.visionLevel.ifBlank { null },
                    preferredPaceSeconds = state.preferredPaceSeconds.toIntOrNull(),
                    preferredDurationMinutes = state.preferredDurationMinutes.toIntOrNull(),
                    medicalNotes = state.medicalNotes.ifBlank { null },
                    visualDescription = state.visualDescription.ifBlank { null },
                ),
            )

            userRepository.updateProfile(params)
                .onSuccess {
                    _events.emit(BlindProfileEditEvent.Saved)
                }
                .onFailure { e ->
                    Timber.e(e, "ProfileEditVM: save failed")
                    val msg = context.getString(R.string.error_save_failed)
                    _uiState.value = _uiState.value.copy(isLoading = false, error = msg)
                    _events.emit(BlindProfileEditEvent.Error(msg))
                }
        }
    }
}
