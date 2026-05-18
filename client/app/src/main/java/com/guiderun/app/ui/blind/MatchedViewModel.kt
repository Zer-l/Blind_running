package com.guiderun.app.ui.blind

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
    data object ToHome : MatchedNavEvent
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
                suppressStatusAnnounce()
                ttsManager.speak(context.getString(R.string.tts_request_aborted), TtsManager.Priority.HIGH)
                _navEvent.emit(MatchedNavEvent.ToHome)
                return
            }
            else -> return
        }

        // 首次进入志愿者信息 + 状态合并为一条 TTS，避免被打断
        if (volunteer != null && !hasAnnouncedVolunteer) {
            hasAnnouncedVolunteer = true
            val rating = volunteer.rating?.let { r -> "评分${r}分" } ?: "暂无评分"
            val combined =
                "志愿者${volunteer.nickname}已接单。${rating}，共陪跑${volunteer.totalRuns}次。${statusText}。"
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
        viewModelScope.launch {
            // 仅播报页面名；操作提示由 handleUpdate 根据当前状态精确播报：
            // ACCEPTED/EN_ROUTE → "志愿者正在准备/在赶往"，MET → "已到达汇合点，请长按确认按钮 2 秒汇合"
            // 避免在按钮 disabled 状态下播报"按住 2 秒确认汇合"误导用户
            ttsManager.speakAndWait(context.getString(R.string.tts_page_matched), TtsManager.Priority.HIGH)
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
                    ttsManager.speak(context.getString(R.string.tts_order_cancelled), TtsManager.Priority.HIGH)
                    hapticFeedback.confirm()
                    _navEvent.emit(MatchedNavEvent.ToHome)
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isCancelling = false) }
                    ttsManager.speak(context.getString(R.string.tts_order_cancel_failed, e.message ?: "请重试"), TtsManager.Priority.HIGH)
                    hapticFeedback.error()
                }
        }
    }
}
