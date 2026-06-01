package com.guiderun.app.ui.blind

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.guiderun.app.R
import com.guiderun.app.accessibility.HapticFeedback
import com.guiderun.app.accessibility.TtsManager
import com.guiderun.app.data.local.UserPreferences
import com.guiderun.app.data.local.dao.RunSessionStatsDao
import com.guiderun.app.data.remote.WebSocketManager
import com.guiderun.app.domain.model.RunRequestStatus
import com.guiderun.app.domain.repository.RunRequestRepository
import com.guiderun.app.service.BlindRunTrackingService
import com.guiderun.app.service.RunTrackingService
import com.guiderun.app.util.PaceCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** 跑步中页 UI 状态；配速字段由 Service 的 SpeedSmoother 预处理，UI 不再叠加第二层平滑。 */
data class BlindRunningUiState(
    val totalDistanceMeters: Int = 0,
    val totalDurationSeconds: Int = 0,
    /** 原始瞬时配速。 */
    val currentPaceSeconds: Int? = null,
    /** 显示用瞬时配速：基于 currentPaceSeconds 做 EMA 平滑。 */
    val displayPaceSeconds: Int? = null,
    val avgPaceSeconds: Int? = null,
    /** 本机采集端是否处于自动暂停。 */
    val isPaused: Boolean = false,
    val statusText: String = "",
    /** 志愿者已发起结束申请，等待视障端确认。 */
    val endRequestedByVolunteer: Boolean = false,
    /** 志愿者手机号；接单后下发，供视障端音量+键拨号。 */
    val peerPhone: String? = null,
)

sealed interface BlindRunningNavEvent {
    data class ToReview(val requestId: String) : BlindRunningNavEvent
    /**
     * 返回首页。reasonRes 非空时由 BlindHomeFragment.onResume 接力播报，
     * 避免本页 onPause→ttsManager.release()→engine.stop() 清队列吞掉。
     */
    data class ToHome(@StringRes val reasonRes: Int? = null) : BlindRunningNavEvent
}

/**
 * 视障端跑步中页 ViewModel（AndroidViewModel，持有 Application 引用以启停前台 Service）。
 *
 * 数据驱动：
 * - 跑步指标（距离/时长/配速）由 [BlindRunTrackingService] 写入 Room [RunSessionStatsDao]，
 *   本 ViewModel 通过 observe Flow 驱动 UI，不直接操作 GPS。
 * - 志愿者申请结束：WS endRunRequested → uiState.endRequestedByVolunteer = true → UI 显示确认引导
 * - FINISHED WS → 停止 Service → NavEvent.ToReview；ABORTED WS → ToHome + TTS 接力
 *
 * 生命周期设计：onCleared 故意不停 Service。视障端可从跑步中页返回首页（后台跑步），
 * Service 应继续运行；终态（FINISHED/ABORTED/doEndRun 成功）才由对应分支显式 stopService。
 */
@HiltViewModel
class BlindRunningViewModel @Inject constructor(
    application: Application,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val ttsManager: TtsManager,
    private val hapticFeedback: HapticFeedback,
    private val runRequestRepository: RunRequestRepository,
    private val sessionStatsDao: RunSessionStatsDao,
    private val userPreferences: UserPreferences,
    private val wsManager: WebSocketManager,
) : AndroidViewModel(application) {

    val requestId: String = checkNotNull(savedStateHandle["requestId"])

    private val _uiState = MutableStateFlow(BlindRunningUiState())
    val uiState: StateFlow<BlindRunningUiState> = _uiState.asStateFlow()

    private val _navEvent = Channel<BlindRunningNavEvent>(Channel.CONFLATED)
    val navEvent = _navEvent.receiveAsFlow()

    private var userId: String = ""
    private var suppressAnnounceUntil = 0L
    /** 长按手势期间抑制周期播报（每公里 / 每5分钟）的截止时间，避免抢播打断阈值/倒计时提示。 */
    private var longPressSuppressUntil = 0L
    /** 是否正在长按结束按钮：手指按下到抬起期间为 true，用于直接屏蔽自动暂停的"已暂停"播报。 */
    private var isLongPressing = false
    private var lastAnnouncedKm: Int = 0
    /** 上次"每 5 分钟整点"播报的"分钟"刻度，避免同一分钟反复触发。 */
    private var lastAnnouncedMinuteBucket: Int = 0
    /** 上次观测到的暂停状态，用于检测切换并触发 TTS+震动反馈。 */
    private var lastIsPaused: Boolean? = null

    init {
        viewModelScope.launch {
            userId = userPreferences.getCurrentUserId() ?: ""
            // 区分"全新开始 vs 返回首页重进"：DB 已存在该 request 的 stats 行且时长 > 0 → 重进；
            // 否则视为首次进入。startTrackingService 是幂等的，service 已在跑也安全。
            val isResume = userId.isNotEmpty() &&
                (sessionStatsDao.get(requestId, userId)?.totalDurationSeconds ?: 0) > 0
            try {
                startTrackingService()
            } catch (e: Exception) {
                Timber.e(e, "Failed to start tracking service")
            }
            loadPeerPhone()
            observeSessionStats()
            observeWs()
            val ttsRes = if (isResume) R.string.blind_tts_running_resumed else R.string.tts_running_started
            suppressAndSpeak(context.getString(ttsRes), TtsManager.Priority.HIGH)
        }
    }

    private suspend fun loadPeerPhone() {
        runRequestRepository.getRunRequest(requestId)
            .onSuccess { req -> _uiState.update { it.copy(peerPhone = req.volunteer?.phone) } }
            .onFailure { Timber.w(it, "load peer phone failed") }
    }

    private fun suppressAndSpeak(text: String, priority: TtsManager.Priority = TtsManager.Priority.NORMAL) {
        suppressAnnounceUntil = System.currentTimeMillis() + 5_000L
        ttsManager.speak(text, priority)
    }

    private fun startTrackingService() {
        val intent = Intent(getApplication(), BlindRunTrackingService::class.java).apply {
            putExtra(RunTrackingService.EXTRA_REQUEST_ID, requestId)
        }
        getApplication<Application>().startForegroundService(intent)
    }

    private fun stopTrackingService() {
        getApplication<Application>().stopService(
            Intent(getApplication(), BlindRunTrackingService::class.java)
        )
    }

    private fun observeSessionStats() {
        viewModelScope.launch {
            if (userId.isEmpty()) return@launch
            sessionStatsDao.observe(requestId, userId).collect { stats ->
                if (stats != null) {
                    // 暂停状态切换：朗读 + 震动，视障用户无屏幕反馈也能感知
                    announcePauseToggleIfChanged(stats.isPaused)
                    // 双端独立：本端 UI 100% 由本机 RunTrackingService 写入的 stats 驱动；
                    // 不再订阅对端 peerMetrics，避免对端数据异常 / 5s 跳变 / 0 覆盖等问题。
                    // 配速平滑已在 service 的 SpeedSmoother（5s 滑动平均）完成，UI 不再叠第二层 EMA。
                    _uiState.update {
                        it.copy(
                            totalDistanceMeters = stats.totalDistanceMeters,
                            totalDurationSeconds = stats.totalDurationSeconds,
                            currentPaceSeconds = stats.currentPaceSeconds,
                            displayPaceSeconds = stats.currentPaceSeconds,
                            avgPaceSeconds = stats.avgPaceSeconds,
                            isPaused = stats.isPaused,
                        )
                    }
                    checkKmAnnouncement(stats.totalDistanceMeters)
                    maybeAnnouncePeriodic(stats.totalDurationSeconds, stats.totalDistanceMeters)
                }
            }
        }
    }

    /** 检测 isPaused 翻转：进入暂停→警告震动+TTS "已暂停"；恢复→确认震动+TTS "继续跑步"。 */
    private fun announcePauseToggleIfChanged(nowPaused: Boolean) {
        // 正在长按结束按钮时直接屏蔽："已暂停"会插进倒计时数字队列打乱节奏、warning 震动也与手势 tick 冲突。
        // 不更新 lastIsPaused，手指抬起后状态仍不同则顺延补播（与每公里/周期播报一致）。
        if (isLongPressing) return
        val prev = lastIsPaused
        lastIsPaused = nowPaused
        if (prev == null || prev == nowPaused) return
        if (nowPaused) {
            hapticFeedback.warning()
            suppressAndSpeak(context.getString(R.string.tts_running_paused))
        } else {
            hapticFeedback.confirm()
            suppressAndSpeak(context.getString(R.string.tts_running_resumed))
        }
    }

    /** 每 5 分钟整点播报一次跑步进度，由 stats 1Hz tick 驱动。 */
    private fun maybeAnnouncePeriodic(durationSec: Int, distanceMeters: Int) {
        // 长按期间跳过，且不更新 bucket，使其在长按结束后顺延补播
        if (System.currentTimeMillis() < longPressSuppressUntil) return
        if (durationSec <= 0) return
        val minute = durationSec / 60
        if (minute == 0 || minute % 5 != 0) return
        if (minute == lastAnnouncedMinuteBucket) return
        lastAnnouncedMinuteBucket = minute
        val km = distanceMeters / 1000.0
        suppressAndSpeak(context.getString(R.string.tts_running_progress, minute, km))
    }

    private fun checkKmAnnouncement(distanceMeters: Int) {
        // 长按期间跳过，且不更新 lastAnnouncedKm，使其在长按结束后顺延补播
        if (System.currentTimeMillis() < longPressSuppressUntil) return
        val km = distanceMeters / 1000
        if (km > lastAnnouncedKm && km > 0) {
            lastAnnouncedKm = km
            val state = _uiState.value
            val durationMin = state.totalDurationSeconds / 60
            val durationSec = state.totalDurationSeconds % 60
            val paceText = state.currentPaceSeconds?.let { "配速${PaceCalculator.formatPace(it)}每公里" } ?: ""
            suppressAndSpeak(context.getString(R.string.tts_running_km, km, durationMin, durationSec, paceText))
        }
    }

    private fun observeWs() {
        viewModelScope.launch {
            wsManager.messages.collect { msg ->
                if (msg.requestId != requestId) return@collect
                when (msg.toStatus) {
                    RunRequestStatus.FINISHED.name -> {
                        stopTrackingService()
                        suppressAndSpeak(context.getString(R.string.tts_running_ended), TtsManager.Priority.HIGH)
                        hapticFeedback.confirm()
                        _navEvent.send(BlindRunningNavEvent.ToReview(requestId))
                    }
                    RunRequestStatus.ABORTED.name -> {
                        // 跑步中对端取消：TTS 由 BlindHomeFragment 接力，避免本页 release 吞掉
                        stopTrackingService()
                        hapticFeedback.error()
                        _navEvent.send(BlindRunningNavEvent.ToHome(R.string.tts_aborted_by_volunteer))
                    }
                    else -> {}
                }
            }
        }
        viewModelScope.launch {
            wsManager.endRunRequested.collect { msg ->
                if (msg.requestId != requestId) return@collect
                _uiState.update { it.copy(endRequestedByVolunteer = true) }
                // 跑步中手机多在臂带，单次双脉冲易被忽略；连续震两轮放大感知
                hapticFeedback.warning()
                viewModelScope.launch {
                    delay(700)
                    hapticFeedback.warning()
                }
                suppressAndSpeak(
                    context.getString(R.string.tts_peer_end_request),
                    TtsManager.Priority.HIGH,
                )
            }
        }
        viewModelScope.launch {
            wsManager.reconnected.collect {
                runRequestRepository.getRunRequest(requestId)
            }
        }
    }

    /**
     * 统一的"申请结束跑步"入口：
     * - 由 BlindLongPressGestureView 长按 2s + 5s 倒计时结束后调用（手势路径）；
     * - 由语音指令 END_RUN 直接调用（语音路径，跳过倒计时）。
     */
    /** 长按手势按下时调用：抑制每公里/每5分钟周期播报，并直接屏蔽自动暂停播报，避免抢播打断长按提示。 */
    fun onLongPressStarted() {
        isLongPressing = true
        longPressSuppressUntil = System.currentTimeMillis() + 8_000L
    }

    /** 长按手势结束（手指抬起 / 取消 / 页面 reset）时调用：解除"已暂停"播报屏蔽。 */
    fun onLongPressEnded() {
        isLongPressing = false
    }

    fun executeEndRun() {
        hapticFeedback.confirm()
        suppressAndSpeak(
            context.getString(R.string.tts_running_ending),
            TtsManager.Priority.HIGH,
        )
        viewModelScope.launch { doEndRun() }
    }

    /** 朗读当前跑步状态（距离/时长/配速）。供语音指令 STATUS 调用。 */
    fun announceCurrentStatus() {
        val state = _uiState.value
        val distanceKm = state.totalDistanceMeters / 1000.0
        val minutes = state.totalDurationSeconds / 60
        val seconds = state.totalDurationSeconds % 60
        val paceText = state.displayPaceSeconds?.let { p ->
            context.getString(R.string.tts_running_pace_format, p / 60, p % 60)
        } ?: context.getString(R.string.tts_running_pace_unknown)
        val msg = context.getString(
            R.string.tts_running_status,
            "%.2f".format(distanceKm),
            minutes,
            seconds,
            paceText,
        )
        suppressAndSpeak(msg, TtsManager.Priority.HIGH)
    }

    private suspend fun doEndRun() {
        val state = _uiState.value
        runRequestRepository.endRun(
            id = requestId,
            actualDistanceMeters = state.totalDistanceMeters,
            actualDurationSeconds = state.totalDurationSeconds,
            avgPaceSeconds = state.avgPaceSeconds,
        ).onSuccess {
            stopTrackingService()
            suppressAndSpeak(context.getString(R.string.tts_running_ended), TtsManager.Priority.HIGH)
            hapticFeedback.confirm()
            _navEvent.send(BlindRunningNavEvent.ToReview(requestId))
        }.onFailure { e ->
            Timber.e(e, "endRun failed")
            // 不向 TTS 透传原始异常，改用通用重试提示
            suppressAndSpeak(context.getString(R.string.tts_running_end_failed, context.getString(R.string.common_retry)), TtsManager.Priority.HIGH)
            hapticFeedback.error()
        }
    }

    private var hasAnnouncedPageOnce = false

    fun onScreenResumed() {
        ttsManager.acquire()
        val firstTime = !hasAnnouncedPageOnce
        hasAnnouncedPageOnce = true
        viewModelScope.launch {
            if (firstTime) {
                // 首次：init 已播"跑步开始/已恢复跑步"，本次仅补操作 hint 串在其后
                ttsManager.speak(
                    context.getString(R.string.tts_hint_blind_running),
                    TtsManager.Priority.HIGH,
                )
            } else {
                // 从子页/后台返回：完整播"跑步中页面" + 操作 hint，重新告知用户当前位置
                ttsManager.speakAndWait(
                    context.getString(R.string.tts_page_blind_running),
                    TtsManager.Priority.HIGH,
                )
                ttsManager.speak(
                    context.getString(R.string.tts_hint_blind_running),
                    TtsManager.Priority.HIGH,
                )
            }
        }
    }

    fun onScreenPaused() {
        ttsManager.release()
    }

    // 故意不在 onCleared 内 stopTrackingService。
    // 返回首页（popBackStack 弹出 BlindRunningFragment）会触发 ViewModel.onCleared，
    // 此时跑步应在后台继续（TTS"跑步在后台继续"，首页横幅可恢复），
    // 终态停止由显式分支负责：FINISHED WS / ABORTED WS / doEndRun success 都已调 stopTrackingService。
}
