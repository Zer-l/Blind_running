package com.guiderun.app.ui.blind

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.guiderun.app.accessibility.HapticFeedback
import com.guiderun.app.accessibility.TtsManager
import com.guiderun.app.data.local.UserPreferences
import com.guiderun.app.data.local.dao.RunSessionStatsDao
import com.guiderun.app.data.remote.WebSocketManager
import com.guiderun.app.domain.model.RunRequest
import com.guiderun.app.domain.model.RunRequestStatus
import com.guiderun.app.domain.repository.RunRequestRepository
import com.guiderun.app.service.BlindRunTrackingService
import com.guiderun.app.service.RunTrackingService
import com.guiderun.app.util.Ema
import com.guiderun.app.util.PaceCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

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
    val endCountdown: Int? = null,
    /** 志愿者已发起结束申请，等待视障端长按 3 秒确认。 */
    val endRequestedByVolunteer: Boolean = false,
    /** 志愿者手机号；接单后下发，供视障端音量+键拨号。 */
    val peerPhone: String? = null,
)

sealed interface BlindRunningNavEvent {
    data class ToReview(val requestId: String) : BlindRunningNavEvent
    data object ToHome : BlindRunningNavEvent
}

@HiltViewModel
class BlindRunningViewModel @Inject constructor(
    application: Application,
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
    private var endCountdownJob: Job? = null
    private var suppressAnnounceUntil = 0L
    private var lastPeerMetricsTimeMs: Long = 0L
    private var lastAnnouncedKm: Int = 0
    /** 上次"每 5 分钟整点"播报的"分钟"刻度，避免同一分钟反复触发。 */
    private var lastAnnouncedMinuteBucket: Int = 0
    /** 上次观测到的暂停状态，用于检测切换并触发 TTS+震动反馈。 */
    private var lastIsPaused: Boolean? = null
    private val paceEma = Ema(alpha = 0.3)

    init {
        viewModelScope.launch {
            userId = userPreferences.getCurrentUserId() ?: ""
            try {
                startTrackingService()
            } catch (e: Exception) {
                Timber.e(e, "Failed to start tracking service")
            }
            loadPeerPhone()
            observeSessionStats()
            observePeerMetrics()
            observeWs()
            suppressAndSpeak("跑步开始，正在录制轨迹", TtsManager.Priority.HIGH)
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
                    // peer metrics 超过 8 秒未更新时使用本地数据作为 fallback
                    val now = System.currentTimeMillis()
                    if (now - lastPeerMetricsTimeMs > 8_000L) {
                        val display = smoothPaceForDisplay(stats.currentPaceSeconds, stats.isPaused)
                        _uiState.update {
                            it.copy(
                                totalDistanceMeters = stats.totalDistanceMeters,
                                totalDurationSeconds = stats.totalDurationSeconds,
                                currentPaceSeconds = stats.currentPaceSeconds,
                                displayPaceSeconds = display,
                                avgPaceSeconds = stats.avgPaceSeconds,
                                isPaused = stats.isPaused,
                            )
                        }
                        maybeAnnouncePeriodic(stats.totalDurationSeconds, stats.totalDistanceMeters)
                    }
                }
            }
        }
    }

    /** 检测 isPaused 翻转：进入暂停→警告震动+TTS "已暂停"；恢复→确认震动+TTS "继续跑步"。 */
    private fun announcePauseToggleIfChanged(nowPaused: Boolean) {
        val prev = lastIsPaused
        lastIsPaused = nowPaused
        if (prev == null || prev == nowPaused) return
        if (nowPaused) {
            hapticFeedback.warning()
            suppressAndSpeak("已暂停")
        } else {
            hapticFeedback.confirm()
            suppressAndSpeak("继续跑步")
        }
    }

    /** UI 配速 EMA 平滑；暂停时重置并返回 null。 */
    private fun smoothPaceForDisplay(rawPace: Int?, paused: Boolean): Int? {
        if (paused || rawPace == null) {
            paceEma.reset()
            return null
        }
        return paceEma.update(rawPace.toDouble()).toInt()
    }

    /** 每 5 分钟整点播报一次跑步进度，由 stats 1Hz tick 驱动。 */
    private fun maybeAnnouncePeriodic(durationSec: Int, distanceMeters: Int) {
        if (durationSec <= 0) return
        val minute = durationSec / 60
        if (minute == 0 || minute % 5 != 0) return
        if (minute == lastAnnouncedMinuteBucket) return
        lastAnnouncedMinuteBucket = minute
        val km = distanceMeters / 1000.0
        suppressAndSpeak("已跑步${minute}分钟，距离${"%.1f".format(km)}公里")
    }

    private fun observePeerMetrics() {
        viewModelScope.launch {
            wsManager.peerMetrics.collect { metrics ->
                if (metrics.requestId != requestId) return@collect
                lastPeerMetricsTimeMs = System.currentTimeMillis()
                // 距离单调约束：peer 推送回退时不允许 UI 距离回退（避免 GPS 重算造成视觉跳变）
                val curDist = _uiState.value.totalDistanceMeters
                val safeDist = maxOf(curDist, metrics.totalDistanceMeters)
                // peer 协议未携带 isPaused，按 currentPaceSeconds 是否存在近似判断
                val display = smoothPaceForDisplay(metrics.currentPaceSeconds, paused = false)
                _uiState.update {
                    it.copy(
                        totalDistanceMeters = safeDist,
                        totalDurationSeconds = metrics.totalDurationSeconds,
                        currentPaceSeconds = metrics.currentPaceSeconds,
                        displayPaceSeconds = display,
                        avgPaceSeconds = metrics.avgPaceSeconds,
                    )
                }
                checkKmAnnouncement(safeDist)
                maybeAnnouncePeriodic(metrics.totalDurationSeconds, safeDist)
            }
        }
    }

    private fun checkKmAnnouncement(distanceMeters: Int) {
        val km = distanceMeters / 1000
        if (km > lastAnnouncedKm && km > 0) {
            lastAnnouncedKm = km
            val state = _uiState.value
            val durationMin = state.totalDurationSeconds / 60
            val durationSec = state.totalDurationSeconds % 60
            val paceText = state.currentPaceSeconds?.let { "配速${PaceCalculator.formatPace(it)}每公里" } ?: ""
            suppressAndSpeak("已跑${km}公里，用时${durationMin}分${durationSec}秒，$paceText")
        }
    }

    private fun observeWs() {
        viewModelScope.launch {
            wsManager.messages.collect { msg ->
                if (msg.requestId != requestId) return@collect
                when (msg.toStatus) {
                    RunRequestStatus.FINISHED.name -> {
                        stopTrackingService()
                        suppressAndSpeak("跑步已结束", TtsManager.Priority.HIGH)
                        hapticFeedback.confirm()
                        _navEvent.send(BlindRunningNavEvent.ToReview(requestId))
                    }
                    RunRequestStatus.ABORTED.name -> {
                        stopTrackingService()
                        suppressAndSpeak("请求已终止", TtsManager.Priority.HIGH)
                        hapticFeedback.error()
                        _navEvent.send(BlindRunningNavEvent.ToHome)
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
                    "志愿者申请结束跑步，长按屏幕3秒确认结束，或继续跑步忽略",
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

    /** 长按3秒触发结束跑步倒计时 */
    fun onEndRunPressed() {
        if (endCountdownJob?.isActive == true) {
            endCountdownJob?.cancel()
            endCountdownJob = null
            _uiState.update { it.copy(endCountdown = null) }
            suppressAndSpeak("已取消", TtsManager.Priority.HIGH)
            return
        }

        hapticFeedback.warning()
        endCountdownJob = viewModelScope.launch {
            suppressAndSpeak("5秒后结束跑步，再按一次可撤销", TtsManager.Priority.HIGH)

            for (i in 5 downTo 1) {
                ensureActive()
                _uiState.update { it.copy(endCountdown = i) }
                ttsManager.speakAndWait("$i", TtsManager.Priority.HIGH)
                suppressAnnounceUntil = System.currentTimeMillis() + 2_000L
            }

            ensureActive()
            _uiState.update { it.copy(endCountdown = null) }
            doEndRun()
        }
    }

    /** 长按达到 3 秒阈值瞬时反馈：震动 + 提示松开。 */
    fun onLongPressThresholdEndRun() {
        hapticFeedback.warning()
        suppressAndSpeak("松开结束跑步", TtsManager.Priority.HIGH)
    }

    fun executeEndRun() {
        hapticFeedback.confirm()
        suppressAndSpeak("正在结束跑步", TtsManager.Priority.HIGH)
        viewModelScope.launch { doEndRun() }
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
            suppressAndSpeak("跑步已结束", TtsManager.Priority.HIGH)
            hapticFeedback.confirm()
            _navEvent.send(BlindRunningNavEvent.ToReview(requestId))
        }.onFailure { e ->
            Timber.e(e, "endRun failed")
            suppressAndSpeak("结束失败：${e.message ?: "请重试"}", TtsManager.Priority.HIGH)
            hapticFeedback.error()
        }
    }

    fun onScreenResumed() {
        ttsManager.acquire()
    }

    fun onScreenPaused() {
        ttsManager.release()
        endCountdownJob?.cancel()
        endCountdownJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopTrackingService()
    }
}
