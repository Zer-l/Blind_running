package com.guiderun.app.ui.blind

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guiderun.app.R
import com.guiderun.app.accessibility.HapticFeedback
import com.guiderun.app.accessibility.TtsManager
import com.guiderun.app.domain.model.CreateReviewParams
import com.guiderun.app.domain.repository.RunRequestRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BlindReviewUiState(
    val selectedRating: Int = DEFAULT_RATING,
    val isSubmitting: Boolean = false,
    val distanceMeters: Int = 0,
    val durationSeconds: Int = 0,
    /** 志愿者手机号；FINISHED 阶段服务端仍下发，供视障端音量+键拨号。 */
    val peerPhone: String? = null,
) {
    companion object {
        const val DEFAULT_RATING = 5
    }
}

sealed interface BlindReviewNavEvent {
    data object ToHome : BlindReviewNavEvent
}

@HiltViewModel
class BlindReviewViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    savedStateHandle: SavedStateHandle,
    private val ttsManager: TtsManager,
    private val hapticFeedback: HapticFeedback,
    private val runRequestRepository: RunRequestRepository,
) : ViewModel() {

    private val requestId: String = checkNotNull(savedStateHandle["requestId"])

    private val _uiState = MutableStateFlow(BlindReviewUiState())
    val uiState: StateFlow<BlindReviewUiState> = _uiState.asStateFlow()

    private val _navEvent = MutableSharedFlow<BlindReviewNavEvent>(replay = 0)
    val navEvent: SharedFlow<BlindReviewNavEvent> = _navEvent.asSharedFlow()

    init {
        viewModelScope.launch {
            val req = runRequestRepository.getRunRequest(requestId).getOrNull()
            if (req != null) {
                val distance = req.actualDistanceMeters ?: 0
                val duration = req.actualDurationSeconds ?: 0
                _uiState.update {
                    it.copy(
                        distanceMeters = distance,
                        durationSeconds = duration,
                        peerPhone = req.volunteer?.phone,
                    )
                }
                ttsManager.speak(
                    appContext.getString(
                        R.string.blind_review_intro_format,
                        distance / 1000.0,
                        duration / 60,
                    ),
                    TtsManager.Priority.HIGH,
                )
            } else {
                ttsManager.speak(
                    appContext.getString(R.string.blind_review_gesture_hint),
                    TtsManager.Priority.HIGH,
                )
            }
        }
    }

    /** 单击卡片：选择评分，TTS 朗读，震动反馈，但不提交。 */
    fun selectRating(rating: Int) {
        val clamped = rating.coerceIn(1, 5)
        if (_uiState.value.selectedRating == clamped) return
        _uiState.update { it.copy(selectedRating = clamped) }
        hapticFeedback.tick()
        ttsManager.speak(
            appContext.getString(R.string.blind_review_selected_format, ratingLabel(clamped)),
            TtsManager.Priority.HIGH,
        )
    }

    /** 双击屏幕：提交当前选中评分。 */
    fun submitSelected() {
        val state = _uiState.value
        if (state.isSubmitting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            runRequestRepository.createReview(
                requestId = requestId,
                params = CreateReviewParams(rating = state.selectedRating),
            ).onSuccess {
                ttsManager.speak(
                    appContext.getString(R.string.blind_review_submit_success),
                    TtsManager.Priority.HIGH,
                )
                hapticFeedback.confirm()
                _navEvent.emit(BlindReviewNavEvent.ToHome)
            }.onFailure { e ->
                _uiState.update { it.copy(isSubmitting = false) }
                val reason = e.message
                    ?: appContext.getString(R.string.blind_review_submit_default_error)
                ttsManager.speak(
                    appContext.getString(R.string.blind_review_submit_failed_format, reason),
                    TtsManager.Priority.HIGH,
                )
                hapticFeedback.error()
            }
        }
    }

    /** 长按 3 秒：跳过评价直接回首页。 */
    fun skip() {
        viewModelScope.launch {
            ttsManager.speak(
                appContext.getString(R.string.blind_review_skipped),
                TtsManager.Priority.HIGH,
            )
            hapticFeedback.warning()
            _navEvent.emit(BlindReviewNavEvent.ToHome)
        }
    }

    fun onScreenResumed() {
        ttsManager.acquire()
    }

    fun onScreenPaused() {
        ttsManager.release()
    }

    private fun ratingLabel(rating: Int): String = appContext.getString(
        when (rating) {
            1 -> R.string.blind_review_rating_1_label
            2 -> R.string.blind_review_rating_2_label
            3 -> R.string.blind_review_rating_3_label
            4 -> R.string.blind_review_rating_4_label
            else -> R.string.blind_review_rating_5_label
        },
    )
}
