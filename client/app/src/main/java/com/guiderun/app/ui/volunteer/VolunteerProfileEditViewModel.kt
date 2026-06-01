package com.guiderun.app.ui.volunteer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guiderun.app.R
import com.guiderun.app.domain.model.Gender
import com.guiderun.app.domain.model.UpdateProfileParams
import com.guiderun.app.domain.model.VolunteerProfileUpdate
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

/** 志愿者资料编辑页 UI 状态。 */
data class VolunteerProfileEditUiState(
    val nickname: String = "",
    val gender: Gender? = null,
    val averagePaceSeconds: String = "",
    val runningLevel: String = "",
    val hasGuideExperience: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
)

/** 志愿者资料编辑页一次性事件（SharedFlow，replay=0）。 */
sealed class VolunteerProfileEditEvent {
    data object Saved : VolunteerProfileEditEvent()
    data class Error(val message: String) : VolunteerProfileEditEvent()
}

/**
 * 志愿者资料编辑页 ViewModel。
 *
 * 职责：初始化加载当前用户数据（getMe），持有可编辑字段的 StateFlow，
 * save() 时拼装 [UpdateProfileParams] 调用 Repository。
 * 保存结果通过 [events]（SharedFlow）通知 UI，避免 UiState 与导航逻辑耦合。
 */
@HiltViewModel
class VolunteerProfileEditViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
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
                    Timber.e(e, "VolunteerProfileEditVM: save failed")
                    val msg = context.getString(R.string.error_save_failed)
                    _uiState.value = _uiState.value.copy(isLoading = false, error = msg)
                    _events.emit(VolunteerProfileEditEvent.Error(msg))
                }
        }
    }
}
