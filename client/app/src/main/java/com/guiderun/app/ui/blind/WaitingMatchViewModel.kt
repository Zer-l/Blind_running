package com.guiderun.app.ui.blind

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guiderun.app.R
import com.guiderun.app.accessibility.HapticFeedback
import com.guiderun.app.accessibility.TtsManager
import com.guiderun.app.accessibility.WaitingMessageGenerator
import com.guiderun.app.domain.model.RunRequestStatus
import com.guiderun.app.domain.usecase.CancelRunRequestUseCase
import com.guiderun.app.domain.usecase.PollRunRequestUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.content.Context
import javax.inject.Inject

data class WaitingMatchUiState(
    val elapsedSeconds: Long = 0L,
    val waitingMessage: String = "",
    val isCancelling: Boolean = false,
)

sealed interface WaitingMatchNavEvent {
    data class ToMatched(val requestId: String) : WaitingMatchNavEvent
    /**
     * 返回首页。reasonRes 非空时由 BlindHomeFragment.onResume 接力播报，
     * 避免本页 onPause→ttsManager.release()→engine.stop() 清队列吞掉。
     */
    data class ToHome(@StringRes val reasonRes: Int? = null) : WaitingMatchNavEvent
}

/**
 * 等待匹配 ViewModel（推广重构第二波）。
 *
 * 手势模型：长按 2s+5s 由 footer 的 LongPressGestureView 接管，
 * 本 VM 只暴露 [executeCancel] 作为"已经确认取消"的执行入口（手势/语音/返回键统一调用）。
 */
@HiltViewModel
class WaitingMatchViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
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

    // 关键操作后抑制自动播报的时间窗（System.currentTimeMillis 毫秒）
    private var suppressAutoAnnounceUntil = 0L

    private companion object {
        const val SUPPRESS_DURATION_MS = 8_000L
    }

    private var hasAnnouncedPage = false

    init {
        startElapsedTimer()
        startPolling()
    }

    private fun suppressAutoAnnounce() {
        suppressAutoAnnounceUntil = System.currentTimeMillis() + SUPPRESS_DURATION_MS
    }

    /** 长按手势按下时调用：抑制 15s 等待播报，避免抢播打断长按阈值/倒计时提示。 */
    fun onLongPressStarted() = suppressAutoAnnounce()

    private fun startElapsedTimer() {
        viewModelScope.launch {
            while (true) {
                delay(1_000)
                val elapsed = _uiState.value.elapsedSeconds + 1
                _uiState.update { it.copy(elapsedSeconds = elapsed) }
                if (elapsed % 15 == 0L) {
                    if (System.currentTimeMillis() < suppressAutoAnnounceUntil) continue
                    val msg = WaitingMessageGenerator.getMessage(elapsed)
                    _uiState.update { it.copy(waitingMessage = msg) }
                    ttsManager.speak(msg, TtsManager.Priority.NORMAL)
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
                        suppressAutoAnnounce()
                        ttsManager.speak(context.getString(R.string.tts_match_found), TtsManager.Priority.HIGH)
                        hapticFeedback.confirm()
                        _navEvent.emit(WaitingMatchNavEvent.ToMatched(requestId))
                    }
                    RunRequestStatus.ABORTED -> {
                        // TTS 由 BlindHomeFragment 接力播报，本页 release 不会吞掉提示
                        suppressAutoAnnounce()
                        _navEvent.emit(WaitingMatchNavEvent.ToHome(R.string.tts_request_aborted))
                    }
                    else -> Unit
                }
            }
        }
    }

    /**
     * @param pendingTts 上游接力 TTS（如 Matched MATCHING 分支推过来的"志愿者放弃接单..."），
     *                   若非空，先 speakAndWait 这条，再串行播页面 title/hint，
     *                   避免后入队的 HIGH speakAndWait 用 QUEUE_FLUSH 打断 pending。
     */
    fun onScreenResumed(pendingTts: String? = null) {
        ttsManager.acquire()
        viewModelScope.launch {
            if (pendingTts != null) {
                ttsManager.speakAndWait(pendingTts, TtsManager.Priority.HIGH)
            }
            if (!hasAnnouncedPage) {
                hasAnnouncedPage = true
                ttsManager.speakAndWait(context.getString(R.string.tts_page_waiting_match), TtsManager.Priority.HIGH)
                ttsManager.speak(context.getString(R.string.tts_hint_waiting_match), TtsManager.Priority.HIGH)
            }
        }
    }

    fun onScreenPaused() {
        ttsManager.release()
    }

    /** 朗读当前等待时长（语音指令 STATUS 入口）。 */
    fun announceWaitTime() {
        suppressAutoAnnounce()
        val state = _uiState.value
        val minutes = state.elapsedSeconds / 60
        val seconds = state.elapsedSeconds % 60
        val msg = if (minutes > 0)
            "已等待${minutes}分${seconds}秒，正在等待志愿者接单"
        else
            "已等待${seconds}秒，正在等待志愿者接单"
        ttsManager.speak(msg, TtsManager.Priority.HIGH)
    }

    /**
     * 真正执行取消订单：来自三个入口
     * 1. footer LongPressGestureView 长按 2s+5s 后 onCountdownCommitted
     * 2. 语音指令 CANCEL
     * 3. 返回键弹窗"取消订单"按钮
     */
    fun executeCancel() {
        if (_uiState.value.isCancelling) return
        viewModelScope.launch {
            _uiState.update { it.copy(isCancelling = true) }
            suppressAutoAnnounce()
            cancelRunRequest(requestId, reason = "用户主动取消")
                .onSuccess {
                    // 取消成功后先清除状态再导航，避免"加载中..."闪烁
                    _uiState.update { it.copy(isCancelling = false) }
                    hapticFeedback.confirm()
                    _navEvent.emit(WaitingMatchNavEvent.ToHome(R.string.tts_cancel_success))
                }
                .onFailure { e ->
                    // 失败仍留本页，直接播
                    _uiState.update { it.copy(isCancelling = false) }
                    ttsManager.speak(context.getString(R.string.tts_cancel_failed, e.message ?: "请重试"))
                    hapticFeedback.error()
                }
        }
    }
}
