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
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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
    /** 长按 ≥2s 松手后启动的 5 秒确认倒计时；非 null 表示倒计时进行中。 */
    val confirmCountdown: Int? = null,
    /** 当前订单状态（用于决定返回键拦截时哪些动作可用，MET 不允许 cancel）。 */
    val currentStatus: RunRequestStatus? = null,
    /** 志愿者手机号；接单后下发，供视障端音量+键拨号。 */
    val peerPhone: String? = null,
)

sealed interface MatchedNavEvent {
    data object ToHome : MatchedNavEvent
    data class ToRunning(val requestId: String) : MatchedNavEvent
}

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

    // ★ FIX: replay = 0
    private val _navEvent = MutableSharedFlow<MatchedNavEvent>(replay = 0)
    val navEvent: SharedFlow<MatchedNavEvent> = _navEvent.asSharedFlow()

    private var hasAnnouncedVolunteer = false
    private var hasNavigatedToRunning = false

    /** 长按确认 → 5 秒倒计时 → startRun 的 Job，撤销时取消。 */
    private var confirmCountdownJob: Job? = null

    // ★ 状态变更播报抑制窗口
    private var suppressStatusAnnounceUntil = 0L

    private companion object {
        const val SUPPRESS_DURATION_MS = 8_000L
        val TERMINAL_STATUSES = setOf(
            RunRequestStatus.ABORTED,
            RunRequestStatus.MET,
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
            RunRequestStatus.MET -> "志愿者已到达集合点，请长按屏幕确认汇合"
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

        // ★ FIX: 首次进入，志愿者信息 + 状态合并为一条 TTS，避免被打断
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

        // ★ 后续状态变更：MET 是需要用户操作的关键状态，绕过抑制窗口强制播报
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
            ttsManager.speakAndWait(context.getString(R.string.tts_page_matched), TtsManager.Priority.HIGH)
            ttsManager.speak(context.getString(R.string.tts_hint_matched), TtsManager.Priority.HIGH)
        }
    }

    fun onScreenPaused() {
        ttsManager.release()
        confirmCountdownJob?.cancel()
        confirmCountdownJob = null
        _uiState.update { it.copy(confirmCountdown = null) }
    }

    /** 短按：朗读当前状态（已知志愿者信息 / 等待汇合 / 已到达汇合点等）。 */
    fun onShortPress() {
        suppressStatusAnnounce()
        val text = _uiState.value.statusText.ifEmpty { context.getString(R.string.tts_hint_matched) }
        ttsManager.speak(text, TtsManager.Priority.HIGH)
    }

    /**
     * 按住 ≥2 秒松开：仅当志愿者已 confirmMet（状态=MET）时启动 5 秒确认倒计时；
     * 倒计时进行中再按一次即撤销；倒计时结束后真正发起 startRun。
     */
    fun onConfirmMetPressed() {
        // 倒计时进行中 → 撤销
        if (confirmCountdownJob?.isActive == true) {
            confirmCountdownJob?.cancel()
            confirmCountdownJob = null
            _uiState.update { it.copy(confirmCountdown = null) }
            suppressStatusAnnounce()
            hapticFeedback.confirm()
            ttsManager.speak(context.getString(R.string.tts_cancelled), TtsManager.Priority.HIGH)
            return
        }

        suppressStatusAnnounce()
        when (_uiState.value.currentStatus) {
            RunRequestStatus.MET -> startConfirmCountdown()
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

    private fun startConfirmCountdown() {
        hapticFeedback.warning()
        confirmCountdownJob = viewModelScope.launch {
            ttsManager.speakAndWait(context.getString(R.string.tts_start_running_countdown, 5), TtsManager.Priority.HIGH)
            for (i in 5 downTo 1) {
                ensureActive()
                _uiState.update { it.copy(confirmCountdown = i) }
                ttsManager.speakAndWait("$i", TtsManager.Priority.HIGH)
                suppressStatusAnnounce()
            }
            ensureActive()
            _uiState.update { it.copy(confirmCountdown = null) }
            executeStartRun()
        }
    }

    private suspend fun executeStartRun() {
        suppressStatusAnnounce()
        ttsManager.speak(context.getString(R.string.tts_start_running), TtsManager.Priority.HIGH)
        runRequestRepository.startRun(requestId)
            .onSuccess { navigateToRunning() }
            .onFailure { e ->
                Timber.e(e, "executeStartRun failed, error=%s", e.javaClass.simpleName)
                // 幂等处理：如果已经是 RUNNING 状态，视为成功（轮询滞后场景）
                val current = runRequestRepository.getRunRequest(requestId).getOrNull()
                if (current?.status == RunRequestStatus.RUNNING) {
                    navigateToRunning()
                } else {
                    ttsManager.speak(
                        context.getString(R.string.tts_submit_failed, e.message ?: "请重试"),
                        TtsManager.Priority.HIGH,
                    )
                    hapticFeedback.error()
                }
            }
    }

    /**
     * 按住达到 2 秒阈值时的触觉反馈 + 语音提示。文案随当前状态切换：
     * - MET：松开即启动 5 秒确认倒计时，提示"松开确认汇合"
     * - ACCEPTED/EN_ROUTE：松开也无效，提示用户继续等待，避免误导
     */
    fun onLongPressThreshold2s() {
        hapticFeedback.warning()
        suppressStatusAnnounce()
        val prompt = when (_uiState.value.currentStatus) {
            RunRequestStatus.MET -> R.string.tts_confirm_met
            RunRequestStatus.ACCEPTED -> R.string.tts_waiting_volunteer_depart
            RunRequestStatus.EN_ROUTE -> R.string.tts_waiting_volunteer_arrive
            else -> R.string.tts_confirm_met
        }
        ttsManager.speak(context.getString(prompt), TtsManager.Priority.HIGH)
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
