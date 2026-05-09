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
    val currentPaceSeconds: Int? = null,
    val avgPaceSeconds: Int? = null,
    val statusText: String = "",
    val endCountdown: Int? = null,
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

    init {
        viewModelScope.launch {
            userId = userPreferences.getCurrentUserId() ?: ""
            try {
                startTrackingService()
            } catch (e: Exception) {
                Timber.e(e, "Failed to start tracking service")
            }
            observeSessionStats()
            observePeerMetrics()
            observeWs()
            startDurationTicker()
            suppressAndSpeak("跑步开始，正在录制轨迹", TtsManager.Priority.HIGH)
        }
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
                    // 仅在 peer metrics 超过 15 秒未更新时使用本地数据作为 fallback
                    val now = System.currentTimeMillis()
                    if (now - lastPeerMetricsTimeMs > 15_000L) {
                        _uiState.update {
                            it.copy(
                                totalDistanceMeters = stats.totalDistanceMeters,
                                totalDurationSeconds = stats.totalDurationSeconds,
                                currentPaceSeconds = stats.currentPaceSeconds,
                                avgPaceSeconds = stats.avgPaceSeconds,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun observePeerMetrics() {
        viewModelScope.launch {
            wsManager.peerMetrics.collect { metrics ->
                if (metrics.requestId != requestId) return@collect
                lastPeerMetricsTimeMs = System.currentTimeMillis()
                _uiState.update {
                    it.copy(
                        totalDistanceMeters = metrics.totalDistanceMeters,
                        totalDurationSeconds = metrics.totalDurationSeconds,
                        currentPaceSeconds = metrics.currentPaceSeconds,
                        avgPaceSeconds = metrics.avgPaceSeconds,
                    )
                }
                checkKmAnnouncement(metrics.totalDistanceMeters)
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

    private fun startDurationTicker() {
        viewModelScope.launch {
            val req = runRequestRepository.getRunRequest(requestId).getOrNull() ?: return@launch
            val startTimeSec = (req.runStartedAt ?: return@launch) / 1000
            while (true) {
                delay(1_000)
                val elapsed = ((System.currentTimeMillis() / 1000) - startTimeSec).toInt()
                _uiState.update { it.copy(totalDurationSeconds = elapsed) }
                // 每5分钟播报一次状态
                if (elapsed > 0 && elapsed % 300 == 0) {
                    val dist = _uiState.value.totalDistanceMeters
                    val km = dist / 1000.0
                    suppressAndSpeak("已跑步${elapsed / 60}分钟，距离${"%.1f".format(km)}公里")
                }
            }
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
            wsManager.reconnected.collect {
                runRequestRepository.getRunRequest(requestId)
            }
        }
    }

    /** 语音通话功能不可用时的提示 */
    fun speakCallUnavailable() {
        suppressAndSpeak("语音通话功能即将上线，请通过其他方式联系志愿者")
    }

    /** 发起语音通话 */
    fun initiateCall(voiceCallManager: com.guiderun.app.service.VoiceCallManager) {
        viewModelScope.launch {
            runRequestRepository.initiateVoiceCall(requestId)
                .onSuccess { callInfo ->
                    callInfo.otherPartyPhone?.let { phone ->
                        voiceCallManager.initiateCall(requestId, phone)
                    }
                }
                .onFailure { e ->
                    suppressAndSpeak("发起通话失败：${e.message}")
                }
        }
    }

    /** 播报当前跑步状态（距离、时长、配速） */
    fun announceCurrentStatus() {
        if (System.currentTimeMillis() < suppressAnnounceUntil) return
        val state = _uiState.value
        val km = state.totalDistanceMeters / 1000.0
        val durationMin = state.totalDurationSeconds / 60
        val durationSec = state.totalDurationSeconds % 60
        val paceText = state.currentPaceSeconds?.let { "，配速${PaceCalculator.formatPace(it)}每公里" } ?: ""
        suppressAndSpeak("已跑${"%.2f".format(km)}公里，用时${durationMin}分${durationSec}秒$paceText")
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
            suppressAndSpeak("5秒后结束跑步，摇动手机可撤销", TtsManager.Priority.HIGH)

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

    fun tryHandleShakeCancel(): Boolean {
        if (endCountdownJob?.isActive == true) {
            endCountdownJob?.cancel()
            endCountdownJob = null
            _uiState.update { it.copy(endCountdown = null) }
            suppressAndSpeak("已取消", TtsManager.Priority.HIGH)
            return true
        }
        return false
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
