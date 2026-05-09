package com.guiderun.app.ui.blind

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guiderun.app.accessibility.HapticFeedback
import com.guiderun.app.accessibility.TtsManager
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

data class BlindReviewUiState(
    val isSubmitting: Boolean = false,
    val distanceMeters: Int = 0,
    val durationSeconds: Int = 0,
)

sealed interface BlindReviewNavEvent {
    data object ToHome : BlindReviewNavEvent
    data object ToVoiceRecord : BlindReviewNavEvent
}

@HiltViewModel
class BlindReviewViewModel @Inject constructor(
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
                _uiState.update {
                    it.copy(
                        distanceMeters = req.actualDistanceMeters ?: 0,
                        durationSeconds = req.actualDurationSeconds ?: 0,
                    )
                }
                val km = (req.actualDistanceMeters ?: 0) / 1000.0
                val min = (req.actualDurationSeconds ?: 0) / 60
                ttsManager.speak(
                    "本次跑步结束，共${"%.1f".format(km)}公里，用时${min}分钟。对志愿者满意吗？",
                    TtsManager.Priority.HIGH,
                )
            } else {
                ttsManager.speak("请评价志愿者", TtsManager.Priority.HIGH)
            }
        }
    }

    /** 双击 → 满意(5分) → 直接提交 */
    fun setSatisfied() {
        hapticFeedback.confirm()
        submit(rating = 5, voiceUrl = null)
    }

    /** 长按3秒 → 不满意(3分) → 进入录音 */
    fun setUnsatisfied() {
        hapticFeedback.warning()
        viewModelScope.launch {
            _navEvent.emit(BlindReviewNavEvent.ToVoiceRecord)
        }
    }

    /** 录音完成后提交 */
    fun submitWithVoice(voiceUrl: String?) {
        submit(rating = 3, voiceUrl = voiceUrl)
    }

    private fun submit(rating: Int, voiceUrl: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            runRequestRepository.createReview(
                requestId = requestId,
                params = CreateReviewParams(
                    rating = rating,
                    comment = null,
                    voiceUrl = voiceUrl,
                ),
            ).onSuccess {
                ttsManager.speak("评价已提交，感谢您的反馈", TtsManager.Priority.HIGH)
                hapticFeedback.confirm()
                _navEvent.emit(BlindReviewNavEvent.ToHome)
            }.onFailure { e ->
                _uiState.update { it.copy(isSubmitting = false) }
                ttsManager.speak("提交失败：${e.message ?: "请重试"}", TtsManager.Priority.HIGH)
                hapticFeedback.error()
            }
        }
    }

    fun skip() {
        viewModelScope.launch {
            ttsManager.speak("已跳过评价", TtsManager.Priority.HIGH)
            _navEvent.emit(BlindReviewNavEvent.ToHome)
        }
    }

    /** 播报本次跑步摘要 */
    fun announceSummary() {
        val state = _uiState.value
        val km = state.distanceMeters / 1000.0
        val min = state.durationSeconds / 60
        ttsManager.speak("本次跑步${"%.1f".format(km)}公里，用时${min}分钟。双击屏幕满意，长按3秒不满意")
    }

    fun onScreenResumed() {
        ttsManager.acquire()
    }

    fun onScreenPaused() {
        ttsManager.release()
    }
}
