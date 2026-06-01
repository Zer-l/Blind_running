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
import timber.log.Timber
import javax.inject.Inject

/** 等待匹配页 UI 状态；waitingMessage 由 WaitingMessageGenerator 每 15s 生成换样式的安抚文案。 */
data class BlindWaitingMatchUiState(
    val elapsedSeconds: Long = 0L,
    val waitingMessage: String = "",
    val isCancelling: Boolean = false,
)

sealed interface BlindWaitingMatchNavEvent {
    data class ToMatched(val requestId: String) : BlindWaitingMatchNavEvent
    /**
     * 返回首页。reasonRes 非空时由 BlindHomeFragment.onResume 接力播报，
     * 避免本页 onPause→ttsManager.release()→engine.stop() 清队列吞掉。
     */
    data class ToHome(@StringRes val reasonRes: Int? = null) : BlindWaitingMatchNavEvent
}

/**
 * 视障端等待匹配页 ViewModel。
 *
 * 核心逻辑：
 * - 计时器每秒 +1，每 15 秒用 WaitingMessageGenerator 生成换样式的等候文案并播报（避免用户焦虑）
 * - 状态轮询：PollRunRequestUseCase 5s 一次（WS 兜底），检测到 ACCEPTED/EN_ROUTE/MET → ToMatched；ABORTED → ToHome
 * - 取消入口三合一（长按手势 / 语音 CANCEL / 返回键弹窗）→ executeCancel，内部去重（isCancelling 标志）
 * - suppressAutoAnnounceUntil：长按手势触发时抑制周期播报，防止"已等待 X 秒"和手势 TTS 抢播
 *
 * TTS 接力：ABORTED 路径不在本页播，存入 pendingTts 由 BlindHomeFragment.onResume 消费。
 */
@HiltViewModel
class BlindWaitingMatchViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val ttsManager: TtsManager,
    private val hapticFeedback: HapticFeedback,
    private val pollRunRequest: PollRunRequestUseCase,
    private val cancelRunRequest: CancelRunRequestUseCase,
) : ViewModel() {

    val requestId: String = checkNotNull(savedStateHandle["requestId"])

    private val _uiState = MutableStateFlow(BlindWaitingMatchUiState())
    val uiState: StateFlow<BlindWaitingMatchUiState> = _uiState.asStateFlow()

    private val _navEvent = MutableSharedFlow<BlindWaitingMatchNavEvent>(replay = 0)
    val navEvent: SharedFlow<BlindWaitingMatchNavEvent> = _navEvent.asSharedFlow()

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
                        _navEvent.emit(BlindWaitingMatchNavEvent.ToMatched(requestId))
                    }
                    RunRequestStatus.ABORTED -> {
                        // TTS 由 BlindHomeFragment 接力播报，本页 release 不会吞掉提示
                        suppressAutoAnnounce()
                        _navEvent.emit(BlindWaitingMatchNavEvent.ToHome(R.string.tts_request_aborted))
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
            context.getString(R.string.tts_waiting_elapsed_min_sec, minutes, seconds)
        else
            context.getString(R.string.tts_waiting_elapsed_sec, seconds)
        ttsManager.speak(msg, TtsManager.Priority.HIGH)
    }

    /**
     * 真正执行取消订单：来自三个入口
     * 1. footer BlindLongPressGestureView 长按 2s+5s 后 onCountdownCommitted
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
                    _navEvent.emit(BlindWaitingMatchNavEvent.ToHome(R.string.tts_cancel_success))
                }
                .onFailure { e ->
                    // 失败仍留本页，直接播；不向 TTS 透传原始异常
                    Timber.e(e, "WaitingMatchVM: executeCancel failed")
                    _uiState.update { it.copy(isCancelling = false) }
                    ttsManager.speak(context.getString(R.string.tts_cancel_failed, context.getString(R.string.common_retry)))
                    hapticFeedback.error()
                }
        }
    }
}
