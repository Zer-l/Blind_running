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
import com.guiderun.app.domain.repository.LocationProvider
import com.guiderun.app.domain.repository.RunRequestRepository
import com.guiderun.app.util.PaceCalculator
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

data class BlindMatchedUiState(
    val volunteerName: String = "",
    val volunteerRating: Float? = null,
    val volunteerTotalRuns: Int = 0,
    val statusText: String = "",
    val isCancelling: Boolean = false,
    /** 当前订单状态：决定 footer 按钮是否可用、返回键是否允许 cancel。 */
    val currentStatus: RunRequestStatus? = null,
    /** 志愿者手机号；接单后下发，供视障端音量+键拨号。 */
    val peerPhone: String? = null,
    /** 志愿者距离视障端的直线距离（米），未知时为 null。 */
    val distanceMeters: Int? = null,
)

sealed interface BlindMatchedNavEvent {
    /**
     * 返回首页。reasonRes 非空时由目标页（BlindHomeFragment）在 onResume 内播报，
     * 不在本页自播——避免 onPause→ttsManager.release()→engine.stop() 清队列吞掉提示。
     */
    data class ToHome(@StringRes val reasonRes: Int? = null) : BlindMatchedNavEvent
    /**
     * 志愿者放弃接单（abandon 第 1/2 次），订单回 MATCHING，视障端退回等待匹配页。
     * 同样走"目标页接力 TTS"机制。
     */
    data class ToWaitingMatch(val requestId: String, @StringRes val reasonRes: Int? = null) : BlindMatchedNavEvent
    data class ToRunning(val requestId: String) : BlindMatchedNavEvent
}

/**
 * 视障端已匹配 ViewModel（推广重构第二波）。
 *
 * 手势模型：长按 2s+5s 由 footer 的 BlindLongPressGestureView 接管，
 * 仅 [RunRequestStatus.MET] 状态下 footer 主按钮 enabled。
 * [executeConfirmMet] 是统一执行入口（手势/语音 CONFIRM），内部仍做状态检查，
 * 状态错误时（语音指令绕过 footer disabled）播报"志愿者尚未到达"而不实际 startRun。
 */
@HiltViewModel
class BlindMatchedViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val ttsManager: TtsManager,
    private val hapticFeedback: HapticFeedback,
    private val runRequestRepository: RunRequestRepository,
    private val locationProvider: LocationProvider,
) : ViewModel() {

    val requestId: String = checkNotNull(savedStateHandle["requestId"])

    private val _uiState = MutableStateFlow(BlindMatchedUiState())
    val uiState: StateFlow<BlindMatchedUiState> = _uiState.asStateFlow()

    private val _navEvent = MutableSharedFlow<BlindMatchedNavEvent>(replay = 0)
    val navEvent: SharedFlow<BlindMatchedNavEvent> = _navEvent.asSharedFlow()

    private var hasAnnouncedVolunteer = false
    private var hasNavigatedToRunning = false
    private var hasStartingRun = false

    // 状态变更播报抑制窗口
    private var suppressStatusAnnounceUntil = 0L

    // 距离计算双源：本机 GPS + 服务端 RunRequest.volunteerPosition
    private var blindLatLng: Pair<Double, Double>? = null
    private var volunteerLatLng: Pair<Double, Double>? = null

    private companion object {
        const val SUPPRESS_DURATION_MS = 8_000L
        // 与 BlindWaitingMatchViewModel 的等待播报节奏一致：15 秒一次
        const val DISTANCE_ANNOUNCE_INTERVAL_S = 15L
        val TERMINAL_STATUSES = setOf(
            RunRequestStatus.ABORTED,
            RunRequestStatus.RUNNING,
            RunRequestStatus.CLOSED,
        )
    }

    init {
        loadAndPoll()
        observeBlindLocation()
        startDistanceAnnouncer()
    }

    /** 本机定位 5s 一次；志愿者位置由 RunRequest.volunteerPosition 轮询拿到。两者齐全即重算距离。 */
    private fun observeBlindLocation() {
        viewModelScope.launch {
            runCatching {
                locationProvider.locationUpdates(5_000L).collect { geo ->
                    blindLatLng = geo.lat to geo.lng
                    recomputeDistance()
                }
            }.onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                Timber.w(it, "BlindMatchedVM: blind location updates failed")
            }
        }
    }

    private fun recomputeDistance() {
        val blind = blindLatLng ?: return
        val volunteer = volunteerLatLng ?: return
        val meters = PaceCalculator.distanceMeters(blind.first, blind.second, volunteer.first, volunteer.second)
        _uiState.update { it.copy(distanceMeters = meters.toInt()) }
    }

    /**
     * 与 BlindWaitingMatchViewModel.startElapsedTimer 同节奏：每 15 秒播报一次距离。
     * 触发条件：志愿者已知 + 距离已知 + 非 MET（到达后距离无意义）+ 非抑制窗口期。
     */
    private fun startDistanceAnnouncer() {
        viewModelScope.launch {
            var ticks = 0L
            while (currentCoroutineContext().isActive) {
                delay(1_000)
                ticks++
                if (ticks % DISTANCE_ANNOUNCE_INTERVAL_S != 0L) continue
                if (System.currentTimeMillis() < suppressStatusAnnounceUntil) continue
                val state = _uiState.value
                val distance = state.distanceMeters ?: continue
                if (state.volunteerName.isBlank()) continue
                // MET 已到达，距离信息无意义；ABORTED/RUNNING/CLOSED 也不再播
                val status = state.currentStatus ?: continue
                if (status == RunRequestStatus.MET || status in TERMINAL_STATUSES) continue
                val msg = context.getString(R.string.tts_matched_volunteer_distance, distance)
                ttsManager.speak(msg, TtsManager.Priority.NORMAL)
            }
        }
    }

    private fun suppressStatusAnnounce() {
        suppressStatusAnnounceUntil = System.currentTimeMillis() + SUPPRESS_DURATION_MS
    }

    /** 长按手势按下时调用：抑制 5s 轮询状态播报，避免抢播打断长按阈值/倒计时提示。 */
    fun onLongPressStarted() = suppressStatusAnnounce()

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
        // 服务端 volunteerPosition 是 WS 推送的志愿者最新坐标；与本机 GPS 配对算直线距离
        request.volunteerPosition?.let { pos ->
            volunteerLatLng = pos.lat to pos.lng
            recomputeDistance()
        }

        val statusText = when (request.status) {
            RunRequestStatus.ACCEPTED -> context.getString(R.string.matched_status_accepted)
            RunRequestStatus.EN_ROUTE -> context.getString(R.string.matched_status_en_route)
            RunRequestStatus.MET -> context.getString(R.string.matched_status_met)
            RunRequestStatus.RUNNING -> {
                navigateToRunning()
                return
            }
            RunRequestStatus.ABORTED -> {
                // 对端取消 / abandon 第3次：TTS 由 BlindHomeFragment 接力播报，避免被 onPause 吞掉
                suppressStatusAnnounce()
                _navEvent.emit(BlindMatchedNavEvent.ToHome(R.string.tts_aborted_by_volunteer))
                return
            }
            RunRequestStatus.MATCHING -> {
                // 志愿者 abandon 前 2 次：订单回 MATCHING 重新匹配，视障端退回等待页
                // 重置志愿者播报标志，下次匹配新志愿者时能再播报一次
                hasAnnouncedVolunteer = false
                suppressStatusAnnounce()
                _navEvent.emit(
                    BlindMatchedNavEvent.ToWaitingMatch(
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
            // 页面名作为前缀拼进合并消息，单次 speak 播完整句，
            // 避免 onScreenResumed.speakAndWait(页面名) 与本处 speak 在不同协程并发被互相 FLUSH。
            val combined = if (request.status == RunRequestStatus.MET) {
                // 已到达（多为首页重进）：用户已知志愿者，跳过"志愿者X已接单评分…"介绍，
                // 只播"志愿者已到达页面 + 到达状态"
                context.getString(R.string.tts_page_matched_arrived) + "。" + statusText
            } else {
                // 首次匹配（ACCEPTED/EN_ROUTE）：完整介绍志愿者 + 距离段（如已知）
                val rating = volunteer.rating
                    ?.let { r -> context.getString(R.string.matched_volunteer_rating_tts, r) }
                    ?: context.getString(R.string.matched_volunteer_rating_tts_none)
                val distanceSegment = _uiState.value.distanceMeters
                    ?.let { d -> context.getString(R.string.matched_volunteer_distance_segment, d) }
                    .orEmpty()
                context.getString(R.string.tts_page_matched) + "。" +
                    context.getString(
                        R.string.matched_volunteer_tts_combined,
                        volunteer.nickname,
                        rating,
                        volunteer.totalRuns,
                        statusText,
                    ) + distanceSegment
            }
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
                // 到达(MET)时页面名变更，前缀"志愿者已到达页面"一并播报，告知用户已切到新页面
                val announce = if (isMetArrival) {
                    context.getString(R.string.tts_page_matched_arrived) + "。" + statusText
                } else {
                    statusText
                }
                ttsManager.speak(announce, TtsManager.Priority.HIGH)
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
            // 页面名随状态：MET="志愿者已到达页面"，否则"志愿者已接单页面"
            val pageRes = if (_uiState.value.currentStatus == RunRequestStatus.MET) {
                R.string.tts_page_matched_arrived
            } else {
                R.string.tts_page_matched
            }
            ttsManager.speakAndWait(context.getString(pageRes), TtsManager.Priority.HIGH)
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
     * - footer BlindLongPressGestureView 长按 2s+5s 后 onCountdownCommitted（仅 MET 时按钮 enabled）
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
                    // 不向 TTS 透传原始异常，避免视障用户听到技术性报错；细节记日志
                    ttsManager.speak(
                        context.getString(R.string.tts_submit_failed_generic),
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
        _navEvent.emit(BlindMatchedNavEvent.ToRunning(requestId))
    }

    /** 返回键触发的取消订单：服务端状态机限制 ACCEPTED/EN_ROUTE 允许，MET 不允许（调用方需先判断）。 */
    fun cancelByUser() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCancelling = true) }
            runRequestRepository.cancel(requestId, reason = "用户主动取消")
                .onSuccess {
                    // 取消成功后先清除状态再导航，避免"加载中..."闪烁
                    _uiState.update { it.copy(isCancelling = false) }
                    hapticFeedback.confirm()
                    _navEvent.emit(BlindMatchedNavEvent.ToHome(R.string.tts_order_cancelled))
                }
                .onFailure { e ->
                    // 失败仍留在本页，TTS 直接播即可（不切页面，不会被 release 吞）
                    Timber.e(e, "MatchedVM: cancelByUser failed")
                    _uiState.update { it.copy(isCancelling = false) }
                    ttsManager.speak(context.getString(R.string.tts_order_cancel_failed_generic), TtsManager.Priority.HIGH)
                    hapticFeedback.error()
                }
        }
    }
}
