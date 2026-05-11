package com.guiderun.app.ui.blind

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guiderun.app.accessibility.HapticFeedback
import com.guiderun.app.accessibility.TtsManager
import com.guiderun.app.domain.model.RunRequest
import com.guiderun.app.domain.model.RunRequestStatus
import com.guiderun.app.domain.repository.RunRequestRepository
import com.guiderun.app.domain.usecase.ReleaseVolunteerUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
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
import javax.inject.Inject

data class MatchedUiState(
    val volunteerName: String = "",
    val volunteerRating: Float? = null,
    val volunteerTotalRuns: Int = 0,
    val statusText: String = "",
    val releaseCountdown: Int? = null,
    val isReleasing: Boolean = false,
    /** 当前订单状态（用于决定返回键拦截时哪些动作可用，MET 不允许 cancel）。 */
    val currentStatus: RunRequestStatus? = null,
    /** 志愿者手机号；接单后下发，供视障端音量+键拨号。 */
    val peerPhone: String? = null,
)

sealed interface MatchedNavEvent {
    data class ToWaiting(val requestId: String) : MatchedNavEvent
    data object ToHome : MatchedNavEvent
    data class ToRunning(val requestId: String) : MatchedNavEvent
}

@HiltViewModel
class MatchedViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val ttsManager: TtsManager,
    private val hapticFeedback: HapticFeedback,
    private val runRequestRepository: RunRequestRepository,
    private val releaseVolunteer: ReleaseVolunteerUseCase,
) : ViewModel() {

    val requestId: String = checkNotNull(savedStateHandle["requestId"])

    private val _uiState = MutableStateFlow(MatchedUiState())
    val uiState: StateFlow<MatchedUiState> = _uiState.asStateFlow()

    // ★ FIX: replay = 0
    private val _navEvent = MutableSharedFlow<MatchedNavEvent>(replay = 0)
    val navEvent: SharedFlow<MatchedNavEvent> = _navEvent.asSharedFlow()

    private var hasAnnouncedVolunteer = false
    private var hasNavigatedToRunning = false

    // ★ FIX: 用手动 Job 替代 ConfirmableAction
    private var releaseCountdownJob: Job? = null

    // ★ 状态变更播报抑制窗口
    private var suppressStatusAnnounceUntil = 0L

    private companion object {
        const val SUPPRESS_DURATION_MS = 8_000L
        val TERMINAL_STATUSES = setOf(
            RunRequestStatus.ABORTED,
            RunRequestStatus.MATCHING,
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
                releaseCountdownJob?.cancel()
                navigateToRunning()
                return
            }
            RunRequestStatus.ABORTED -> {
                releaseCountdownJob?.cancel()
                suppressStatusAnnounce()
                ttsManager.speak("请求已终止", TtsManager.Priority.HIGH)
                _navEvent.emit(MatchedNavEvent.ToHome)
                return
            }

            RunRequestStatus.MATCHING -> {
                releaseCountdownJob?.cancel()
                suppressStatusAnnounce()
                ttsManager.speak("正在重新匹配志愿者", TtsManager.Priority.HIGH)
                _navEvent.emit(MatchedNavEvent.ToWaiting(requestId))
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
    }

    fun onScreenPaused() {
        ttsManager.release()
        releaseCountdownJob?.cancel()
        releaseCountdownJob = null
        _uiState.update { it.copy(releaseCountdown = null) }
    }

    /** 短按：TTS 提示手势操作方法。 */
    fun onShortPress() {
        suppressStatusAnnounce()
        ttsManager.speak("按住1秒确认汇合，按住3秒更换志愿者")
    }

    /** 按住 1–3 秒松开：确认汇合，开始跑步。 */
    fun onConfirmAccept() {
        suppressStatusAnnounce()
        hapticFeedback.confirm()
        viewModelScope.launch {
            ttsManager.speak("正在开始跑步", TtsManager.Priority.HIGH)
            runRequestRepository.startRun(requestId)
                .onSuccess {
                    navigateToRunning()
                }
                .onFailure { e ->
                    Timber.e(e, "onConfirmAccept: startRun failed, error=%s", e.javaClass.simpleName)
                    // 幂等处理：如果已经是 RUNNING 状态，视为成功
                    val current = runRequestRepository.getRunRequest(requestId).getOrNull()
                    if (current?.status == RunRequestStatus.RUNNING) {
                        navigateToRunning()
                    } else {
                        ttsManager.speak("操作失败：${e.message ?: "请重试"}", TtsManager.Priority.HIGH)
                        hapticFeedback.error()
                    }
                }
        }
    }

    /** 按住 ≥3 秒松开：触发更换志愿者倒计时。 */
    fun onReleasePressed() {
        if (releaseCountdownJob?.isActive == true) {
            releaseCountdownJob?.cancel()
            releaseCountdownJob = null
            _uiState.update { it.copy(releaseCountdown = null) }
            suppressStatusAnnounce()
            ttsManager.speak("已取消", TtsManager.Priority.HIGH)
            return
        }

        hapticFeedback.warning()
        suppressStatusAnnounce()
        releaseCountdownJob = viewModelScope.launch {
            ttsManager.speakAndWait("5秒后更换志愿者，再按一次可撤销", TtsManager.Priority.HIGH)

            for (i in 5 downTo 1) {
                ensureActive()
                _uiState.update { it.copy(releaseCountdown = i) }
                ttsManager.speakAndWait("$i", TtsManager.Priority.HIGH)
                suppressStatusAnnounce()
            }

            ensureActive()
            _uiState.update { it.copy(releaseCountdown = null) }
            executeRelease()
        }
    }

    /** 按住达到 1 秒阈值时的触觉反馈 + 语音提示。 */
    fun onLongPressThreshold1s() {
        hapticFeedback.warning()
        suppressStatusAnnounce()
        ttsManager.speak("松开确认汇合")
    }

    /** 按住达到 3 秒阈值时的触觉反馈。 */
    fun onLongPressThreshold3s() {
        hapticFeedback.error()
        suppressStatusAnnounce()
        ttsManager.speak("松开更换志愿者")
    }

    private suspend fun navigateToRunning() {
        if (hasNavigatedToRunning) return
        hasNavigatedToRunning = true
        suppressStatusAnnounce()
        ttsManager.speak("跑步开始", TtsManager.Priority.HIGH)
        hapticFeedback.confirm()
        _navEvent.emit(MatchedNavEvent.ToRunning(requestId))
    }

    /** 返回键触发的取消订单：服务端状态机限制 ACCEPTED/EN_ROUTE 允许，MET 不允许（调用方需先判断）。 */
    fun cancelByUser() {
        viewModelScope.launch {
            _uiState.update { it.copy(isReleasing = true) }
            runRequestRepository.cancel(requestId, reason = "用户主动取消")
                .onSuccess {
                    ttsManager.speak("订单已取消", TtsManager.Priority.HIGH)
                    hapticFeedback.confirm()
                    _navEvent.emit(MatchedNavEvent.ToHome)
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isReleasing = false) }
                    ttsManager.speak("取消失败：${e.message ?: "请重试"}", TtsManager.Priority.HIGH)
                    hapticFeedback.error()
                }
        }
    }

    private suspend fun executeRelease() {
        _uiState.update { it.copy(isReleasing = true, releaseCountdown = null) }
        suppressStatusAnnounce()
        releaseVolunteer(requestId)
            .onSuccess {
                ttsManager.speak("已更换，重新等待匹配", TtsManager.Priority.HIGH)
                hapticFeedback.confirm()
                _navEvent.emit(MatchedNavEvent.ToWaiting(requestId))
            }
            .onFailure { e ->
                _uiState.update { it.copy(isReleasing = false) }
                ttsManager.speak("更换失败：${e.message ?: "请重试"}")
                hapticFeedback.error()
            }
    }
}
