package com.guiderun.app.ui.blind

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guiderun.app.R
import com.guiderun.app.accessibility.HapticFeedback
import com.guiderun.app.accessibility.TtsManager
import com.guiderun.app.domain.model.RunRequest
import com.guiderun.app.domain.model.RunRequestStatus
import com.guiderun.app.domain.repository.RunRequestRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import android.content.Context
import javax.inject.Inject

data class MatchedUiState(
    val volunteerName: String = "",
    val volunteerRating: Float? = null,
    val volunteerTotalRuns: Int = 0,
    val statusText: String = "",
    val isCancelling: Boolean = false,
    /** 当前订单状态：决定 footer 按钮是否可用、返回键是否允许 cancel。 */
    val currentStatus: RunRequestStatus? = null,
    /** 志愿者手机号；接单后下发，供视障端音量+键拨号。 */
    val peerPhone: String? = null,
)

sealed interface MatchedNavEvent {
    /**
     * 返回首页。reasonRes 非空时由目标页（BlindHomeFragment）在 onResume 内播报，
     * 不在本页自播——避免 onPause→ttsManager.release()→engine.stop() 清队列吞掉提示。
     */
    data class ToHome(@StringRes val reasonRes: Int? = null) : MatchedNavEvent
    /**
     * 志愿者放弃接单（abandon 第 1/2 次），订单回 MATCHING，视障端退回等待匹配页。
     * 同样走"目标页接力 TTS"机制。
     */
    data class ToWaitingMatch(val requestId: String, @StringRes val reasonRes: Int? = null) : MatchedNavEvent
    data class ToRunning(val requestId: String) : MatchedNavEvent
}

/**
 * 视障端已匹配 ViewModel（推广重构第二波）。
 *
 * 手势模型：长按 2s+5s 由 footer 的 LongPressGestureView 接管，
 * 仅 [RunRequestStatus.MET] 状态下 footer 主按钮 enabled。
 * [executeConfirmMet] 是统一执行入口（手势/语音 CONFIRM），内部仍做状态检查，
 * 状态错误时（语音指令绕过 footer disabled）播报"志愿者尚未到达"而不实际 startRun。
 */
@HiltViewModel
class MatchedViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val ttsManager: TtsManager,
    private val hapticFeedback: HapticFeedback,
    private val runRequestRepository: RunRequestRepository,
) : ViewModel() {

    val requestId: String = checkNotNull(savedStateHandle["requestId"])

    private val _uiState = MutableStateFlow(MatchedUiState())
    val uiState: StateFlow<MatchedUiState> = _uiState.asStateFlow()

    private val _navEvent = MutableSharedFlow<MatchedNavEvent>(replay = 0)
    val navEvent: SharedFlow<MatchedNavEvent> = _navEvent.asSharedFlow()

    private var hasAnnouncedVolunteer = false
    private var hasNavigatedToRunning = false
    private var hasStartingRun = false

    // 状态变更播报抑制窗口
    private var suppressStatusAnnounceUntil = 0L

    private companion object {
        const val SUPPRESS_DURATION_MS = 8_000L
        val TERMINAL_STATUSES = setOf(
            RunRequestStatus.ABORTED,
            RunRequestStatus.RUNNING,
            RunRequestStatus.CLOSED,
        )
    }

    init {
        loadAndPoll()
    }

    private fun suppressStatusAnnounce() {
        suppressStatusAnnounceUntil = System.currentTimeMillis() + SUPPRESS_DURATION_MS
    }

    private fun loadAndPoll() {
        viewModelScope.launch {
            while (currentCoroutineContext().isActive) {
                val result = runRequestRepository.getRunRequest(requestId).getOrNull()
                if (result != null) {
                    handleUpdate(result)
                    if (result.status in TERMINAL_STATUSES) return@launch
                }
                delay(5_000)
            }
        }
    }

    private suspend fun handleUpdate(request: RunRequest) {
        _uiState.update { it.copy(currentStatus = request.status) }
        val volunteer = request.volunteer
        if (volunteer != null) {
            _uiState.update {
                it.copy(
                    volunteerName = volunteer.nickname,
                    volunteerRating = volunteer.rating,
                    volunteerTotalRuns = volunteer.totalRuns,
                    peerPhone = volunteer.phone,
                )
            }
        }

        val statusText = when (request.status) {
            RunRequestStatus.ACCEPTED -> "志愿者正在准备，即将出发"
            RunRequestStatus.EN_ROUTE -> "志愿者正在赶往汇合点"
            RunRequestStatus.MET -> context.getString(R.string.matched_status_met)
            RunRequestStatus.RUNNING -> {
                navigateToRunning()
                return
            }
            RunRequestStatus.ABORTED -> {
                // 对端取消 / abandon 第3次：TTS 由 BlindHomeFragment 接力播报，避免被 onPause 吞掉
                suppressStatusAnnounce()
                _navEvent.emit(MatchedNavEvent.ToHome(R.string.tts_aborted_by_volunteer))
                return
            }
            RunRequestStatus.MATCHING -> {
                // 志愿者 abandon 前 2 次：订单回 MATCHING 重新匹配，视障端退回等待页
                // 重置志愿者播报标志，下次匹配新志愿者时能再播报一次
                hasAnnouncedVolunteer = false
                suppressStatusAnnounce()
                _navEvent.emit(
                    MatchedNavEvent.ToWaitingMatch(
                        requestId = request.id,
                        reasonRes = R.string.tts_volunteer_abandoned_rematching,
                    )
                )
                return
            }
            else -> return
        }

        // 首次进入志愿者信息 + 状态合并为一条 TTS，避免被打断
        if (volunteer != null && !hasAnnouncedVolunteer) {
            hasAnnouncedVolunteer = true
            val rating = volunteer.rating
                ?.let { r -> context.getString(R.string.matched_volunteer_rating_tts, r) }
                ?: context.getString(R.string.matched_volunteer_rating_tts_none)
            // 页面名作为前缀拼进合并消息，单次 speak 播完整句，
            // 避免 onScreenResumed.speakAndWait(页面名) 与本处 speak 在不同协程并发被互相 FLUSH。
            val combined = context.getString(R.string.tts_page_matched) + "。" +
                context.getString(
                    R.string.matched_volunteer_tts_combined,
                    volunteer.nickname,
                    rating,
                    volunteer.totalRuns,
                    statusText,
                )
            suppressStatusAnnounce()
            ttsManager.speak(combined, TtsManager.Priority.HIGH)
            hapticFeedback.confirm()
            _uiState.update { it.copy(statusText = statusText) }
            return
        }

        // 后续状态变更：MET 是关键状态绕过抑制窗口强制播报
        val prevText = _uiState.value.statusText
        if (statusText != prevText) {
            _uiState.update { it.copy(statusText = statusText) }
            val isMetArrival = request.status == RunRequestStatus.MET
            if (isMetArrival || System.currentTimeMillis() >= suppressStatusAnnounceUntil) {
                ttsManager.speak(statusText, TtsManager.Priority.HIGH)
                hapticFeedback.confirm()
            }
        }
    }

    fun onScreenResumed() {
        ttsManager.acquire()
        // 首次入页：页面名已经被 handleUpdate 拼进合并消息（"志愿者已接单页面。志愿者xx已接单..."），
        //          这里不再单独播，避免与 handleUpdate 在不同协程并发被互相 FLUSH。
        // 重入（hasAnnouncedVolunteer 已 true）：合并消息不会再发，需要本方法独立播页面名 + 当前状态。
        if (!hasAnnouncedVolunteer) return
        viewModelScope.launch {
            ttsManager.speakAndWait(context.getString(R.string.tts_page_matched), TtsManager.Priority.HIGH)
            val statusText = _uiState.value.statusText
            if (statusText.isNotBlank()) {
                ttsManager.speak(statusText, TtsManager.Priority.HIGH)
            }
        }
    }

    fun onScreenPaused() {
        ttsManager.release()
    }

    /** 语音 STATUS / 短按朗读当前状态。 */
    fun announceCurrentStatus() {
        suppressStatusAnnounce()
        val text = _uiState.value.statusText.ifEmpty { context.getString(R.string.tts_hint_matched) }
        ttsManager.speak(text, TtsManager.Priority.HIGH)
    }

    /**
     * 真正执行"确认汇合 + 开始跑步"：
     * - footer LongPressGestureView 长按 2s+5s 后 onCountdownCommitted（仅 MET 时按钮 enabled）
     * - 语音 CONFIRM（可能在错误状态触发，需在此处做状态分支）
     */
    fun executeConfirmMet() {
        if (hasStartingRun) return
        val status = _uiState.value.currentStatus
        when (status) {
            RunRequestStatus.MET -> {
                hasStartingRun = true
                suppressStatusAnnounce()
                viewModelScope.launch { executeStartRun() }
            }
            RunRequestStatus.ACCEPTED -> {
                hapticFeedback.warning()
                ttsManager.speak(context.getString(R.string.tts_waiting_volunteer_depart), TtsManager.Priority.HIGH)
            }
            RunRequestStatus.EN_ROUTE -> {
                hapticFeedback.warning()
                ttsManager.speak(context.getString(R.string.tts_waiting_volunteer_arrive), TtsManager.Priority.HIGH)
            }
            else -> {
                hapticFeedback.warning()
                ttsManager.speak(context.getString(R.string.tts_hint_matched), TtsManager.Priority.HIGH)
            }
        }
    }

    private suspend fun executeStartRun() {
        ttsManager.speak(context.getString(R.string.tts_start_running), TtsManager.Priority.HIGH)
        runRequestRepository.startRun(requestId)
            .onSuccess { navigateToRunning() }
            .onFailure { e ->
                Timber.e(e, "executeStartRun failed, error=%s", e.javaClass.simpleName)
                // 幂等处理：如果已经是 RUNNING，视为成功（轮询滞后场景）
                val current = runRequestRepository.getRunRequest(requestId).getOrNull()
                if (current?.status == RunRequestStatus.RUNNING) {
                    navigateToRunning()
                } else {
                    hasStartingRun = false
                    ttsManager.speak(
                        context.getString(R.string.tts_submit_failed, e.message ?: "请重试"),
                        TtsManager.Priority.HIGH,
                    )
                    hapticFeedback.error()
                }
            }
    }

    private suspend fun navigateToRunning() {
        if (hasNavigatedToRunning) return
        hasNavigatedToRunning = true
        suppressStatusAnnounce()
        ttsManager.speak(context.getString(R.string.tts_running_started), TtsManager.Priority.HIGH)
        hapticFeedback.confirm()
        _navEvent.emit(MatchedNavEvent.ToRunning(requestId))
    }

    /** 返回键触发的取消订单：服务端状态机限制 ACCEPTED/EN_ROUTE 允许，MET 不允许（调用方需先判断）。 */
    fun cancelByUser() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCancelling = true) }
            runRequestRepository.cancel(requestId, reason = "用户主动取消")
                .onSuccess {
                    // 取消成功后立即 nav 回首页，TTS 由 BlindHomeFragment 接力播报
                    hapticFeedback.confirm()
                    _navEvent.emit(MatchedNavEvent.ToHome(R.string.tts_order_cancelled))
                }
                .onFailure { e ->
                    // 失败仍留在本页，TTS 直接播即可（不切页面，不会被 release 吞）
                    _uiState.update { it.copy(isCancelling = false) }
                    ttsManager.speak(context.getString(R.string.tts_order_cancel_failed, e.message ?: "请重试"), TtsManager.Priority.HIGH)
                    hapticFeedback.error()
                }
        }
    }
}
