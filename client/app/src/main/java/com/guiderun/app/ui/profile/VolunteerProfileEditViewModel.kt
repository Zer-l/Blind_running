package com.guiderun.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guiderun.app.domain.model.Gender
import com.guiderun.app.domain.model.UpdateProfileParams
import com.guiderun.app.domain.model.VolunteerProfileUpdate
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

data class VolunteerProfileEditUiState(
    val nickname: String = "",
    val gender: Gender? = null,
    val averagePaceSeconds: String = "",
    val runningLevel: String = "",
    val hasGuideExperience: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
)

sealed class VolunteerProfileEditEvent {
    data object Saved : VolunteerProfileEditEvent()
    data class Error(val message: String) : VolunteerProfileEditEvent()
}

@HiltViewModel
class VolunteerProfileEditViewModel @Inject constructor(
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VolunteerProfileEditUiState())
    val uiState: StateFlow<VolunteerProfileEditUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<VolunteerProfileEditEvent>()
    val events: SharedFlow<VolunteerProfileEditEvent> = _events.asSharedFlow()

    init {
        loadProfile()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            userRepository.getMe()
                .onSuccess { user ->
                    val profile = user.volunteerProfile
                    _uiState.value = _uiState.value.copy(
                        nickname = user.nickname,
                        gender = user.gender,
                        averagePaceSeconds = profile?.averagePaceSeconds?.toString() ?: "",
                        runningLevel = profile?.runningLevel ?: "",
                        hasGuideExperience = profile?.hasGuideExperience ?: false,
                        isLoading = false,
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
        }
    }

    fun updateNickname(nickname: String) {
        _uiState.value = _uiState.value.copy(nickname = nickname)
    }

    fun updateGender(gender: Gender?) {
        _uiState.value = _uiState.value.copy(gender = gender)
    }

    fun updateAveragePaceSeconds(pace: String) {
        _uiState.value = _uiState.value.copy(averagePaceSeconds = pace)
    }

    fun updateRunningLevel(level: String) {
        _uiState.value = _uiState.value.copy(runningLevel = level)
    }

    fun updateHasGuideExperience(hasExperience: Boolean) {
        _uiState.value = _uiState.value.copy(hasGuideExperience = hasExperience)
    }

    fun save() {
        val state = _uiState.value

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val params = UpdateProfileParams(
                nickname = state.nickname.ifBlank { null },
                gender = state.gender,
                volunteerProfile = VolunteerProfileUpdate(
                    averagePaceSeconds = state.averagePaceSeconds.toIntOrNull(),
                    runningLevel = state.runningLevel.ifBlank { null },
                    hasGuideExperience = state.hasGuideExperience,
                ),
            )

            userRepository.updateProfile(params)
                .onSuccess {
                    _events.emit(VolunteerProfileEditEvent.Saved)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "保存失败",
                    )
                    _events.emit(VolunteerProfileEditEvent.Error(e.message ?: "保存失败"))
                }
        }
    }
}
