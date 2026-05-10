package com.guiderun.app.ui.blind

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guiderun.app.accessibility.HapticFeedback
import com.guiderun.app.accessibility.TtsManager
import com.guiderun.app.accessibility.WaitingMessageGenerator
import com.guiderun.app.domain.model.RunRequestStatus
import com.guiderun.app.domain.usecase.CancelRunRequestUseCase
import com.guiderun.app.domain.usecase.PollRunRequestUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WaitingMatchUiState(
    val elapsedSeconds: Long = 0L,
    val waitingMessage: String = "",
    val cancelCountdown: Int? = null,
    val isCancelling: Boolean = false,
)

sealed interface WaitingMatchNavEvent {
    data class ToMatched(val requestId: String) : WaitingMatchNavEvent
    data object ToHome : WaitingMatchNavEvent
}

@HiltViewModel
class WaitingMatchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val ttsManager: TtsManager,
    private val hapticFeedback: HapticFeedback,
    private val pollRunRequest: PollRunRequestUseCase,
    private val cancelRunRequest: CancelRunRequestUseCase,
) : ViewModel() {

    val requestId: String = checkNotNull(savedStateHandle["requestId"])

    private val _uiState = MutableStateFlow(WaitingMatchUiState())
    val uiState: StateFlow<WaitingMatchUiState> = _uiState.asStateFlow()

    private val _navEvent = MutableSharedFlow<WaitingMatchNavEvent>(replay = 0)
    val navEvent: SharedFlow<WaitingMatchNavEvent> = _navEvent.asSharedFlow()

    private var cancelCountdownJob: Job? = null

    // ★ 关键操作后抑制自动播报的时间窗（elapsedRealtime 毫秒）
    private var suppressAutoAnnounceUntil = 0L

    // 抑制时长常量：关键操作后 8 秒内不自动播报
    private companion object {
        const val SUPPRESS_DURATION_MS = 8_000L
    }

    private var hasAnnouncedPage = false

    init {
        startElapsedTimer()
        startPolling()
    }

    // ★ 设置抑制窗口：关键操作前调用
    private fun suppressAutoAnnounce() {
        suppressAutoAnnounceUntil = System.currentTimeMillis() + SUPPRESS_DURATION_MS
    }

    private fun startElapsedTimer() {
        viewModelScope.launch {
            while (true) {
                delay(1_000)
                val elapsed = _uiState.value.elapsedSeconds + 1
                _uiState.update { it.copy(elapsedSeconds = elapsed) }
                if (elapsed % 15 == 0L) {
                    // ★ 检查是否在抑制窗口内
                    if (System.currentTimeMillis() < suppressAutoAnnounceUntil) {
                        // 抑制期内，跳过本次播报
                        continue
                    }
                    val msg = WaitingMessageGenerator.getMessage(elapsed)
                    _uiState.update { it.copy(waitingMessage = msg) }
                    ttsManager.speak(msg)
                }
            }
        }
    }

    private fun startPolling() {
        val initialMsg = WaitingMessageGenerator.getMessage(0)
        _uiState.update { it.copy(waitingMessage = initialMsg) }

        viewModelScope.launch {
            pollRunRequest(requestId).collect { request ->
                when (request.status) {
                    RunRequestStatus.ACCEPTED,
                    RunRequestStatus.EN_ROUTE,
                    RunRequestStatus.MET -> {
                        cancelCountdownJob?.cancel()
                        suppressAutoAnnounce()
                        ttsManager.speak("已找到志愿者！正在前往与您汇合", TtsManager.Priority.HIGH)
                        hapticFeedback.confirm()
                        _navEvent.emit(WaitingMatchNavEvent.ToMatched(requestId))
                    }
                    RunRequestStatus.ABORTED -> {
                        cancelCountdownJob?.cancel()
                        suppressAutoAnnounce()
                        ttsManager.speak("请求已终止")
                        _navEvent.emit(WaitingMatchNavEvent.ToHome)
                    }
                    else -> Unit
                }
            }
        }
    }

    fun onScreenResumed() {
        ttsManager.acquire()
        if (!hasAnnouncedPage) {
            hasAnnouncedPage = true
            ttsManager.speak("正在等待志愿者接单，短按屏幕查询等待时长，长按2秒取消请求")
        }
    }

    fun onScreenPaused() {
        ttsManager.release()
        cancelCountdownJob?.cancel()
        cancelCountdownJob = null
        _uiState.update { it.copy(cancelCountdown = null) }
    }

    // ★ 长按取消：先播提示语 → 再倒数
    fun onLongPressCancel() {
        if (cancelCountdownJob?.isActive == true) return

        hapticFeedback.warning()
        suppressAutoAnnounce()
        cancelCountdownJob = viewModelScope.launch {
            ttsManager.speakAndWait("5秒后取消请求，再按一次可撤销", TtsManager.Priority.HIGH)

            for (i in 5 downTo 1) {
                ensureActive()
                _uiState.update { it.copy(cancelCountdown = i) }
                ttsManager.speakAndWait("$i", TtsManager.Priority.HIGH)
                suppressAutoAnnounce()
            }

            ensureActive()
            _uiState.update { it.copy(cancelCountdown = null) }
            executeCancelRequest()
        }
    }

    /** 按住达到 2 秒阈值时的触觉反馈 + 语音提示。 */
    fun onLongPressThreshold2s() {
        hapticFeedback.warning()
        suppressAutoAnnounce()
        ttsManager.speak("松开取消请求")
    }

    // ★ 短按：播报等待时长（唯一入口）
    fun onShortPressHint() {
        suppressAutoAnnounce()
        speakWaitTime()
    }

    fun onCancelPressed() {
        if (cancelCountdownJob?.isActive == true) {
            cancelCountdownJob?.cancel()
            cancelCountdownJob = null
            _uiState.update { it.copy(cancelCountdown = null) }
            suppressAutoAnnounce()
            ttsManager.speak("已取消", TtsManager.Priority.HIGH)
        } else {
            onLongPressCancel()
        }
    }

    private fun speakWaitTime() {
        val state = _uiState.value
        val minutes = state.elapsedSeconds / 60
        val seconds = state.elapsedSeconds % 60
        val msg = if (minutes > 0)
            "已等待${minutes}分${seconds}秒，正在等待志愿者接单"
        else
            "已等待${seconds}秒，正在等待志愿者接单"
        ttsManager.speak(msg, TtsManager.Priority.HIGH)
    }

    private suspend fun executeCancelRequest() {
        _uiState.update { it.copy(isCancelling = true, cancelCountdown = null) }
        suppressAutoAnnounce()
        cancelRunRequest(requestId, reason = "用户主动取消")
            .onSuccess {
                ttsManager.speak("请求已取消", TtsManager.Priority.HIGH)
                hapticFeedback.confirm()
                _navEvent.emit(WaitingMatchNavEvent.ToHome)
            }
            .onFailure { e ->
                _uiState.update { it.copy(isCancelling = false) }
                ttsManager.speak("取消失败：${e.message ?: "请重试"}")
                hapticFeedback.error()
            }
    }
}
