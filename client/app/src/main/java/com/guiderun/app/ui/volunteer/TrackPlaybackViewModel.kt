package com.guiderun.app.ui.volunteer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guiderun.app.domain.model.RunTrack
import com.guiderun.app.domain.model.TrackPoint
import com.guiderun.app.domain.repository.RunRequestRepository
import com.guiderun.app.ui.shared.map.CameraTarget
import com.guiderun.app.ui.shared.map.GuideRunMapState
import com.guiderun.app.ui.shared.map.PolylineConfig
import com.guiderun.app.util.PaceCalculator
import com.guiderun.app.util.PaceWindow
import kotlin.math.max
import kotlin.math.min
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 回放页 UI 状态。
 *
 * `currentDistanceMeters / currentDurationSeconds / currentPaceSeconds` 是"当前 marker 位置"的累计数据，
 * 跟随播放进度同步变化；暂停回放时数值冻结；从头播放时归零。
 */
data class TrackPlaybackUiState(
    val tracks: List<RunTrack> = emptyList(),
    val isLoading: Boolean = true,
    val mapState: GuideRunMapState = GuideRunMapState(),
    val errorMessage: String? = null,
    val isPlaying: Boolean = false,
    val speedMultiplier: Int = 1,
    val currentIndex: Int = 0,
    val totalPoints: Int = 0,
    /** 当前 marker 位置的累计距离（米） */
    val currentDistanceMeters: Int = 0,
    /** 当前 marker 位置的已用时长（秒），= points[currentIndex].t - points[0].t */
    val currentDurationSeconds: Int = 0,
    /** 当前 marker 位置的滑窗瞬时配速（秒/公里），无效时为 null */
    val currentPaceSeconds: Int? = null,
    /** 最终距离（米），用于初始显示和重放时显示 */
    val finalDistanceMeters: Int = 0,
    /** 最终时长（秒），用于初始显示和重放时显示 */
    val finalDurationSeconds: Int = 0,
)

@HiltViewModel
class TrackPlaybackViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val runRequestRepository: RunRequestRepository,
) : ViewModel() {

    private val requestId: String = checkNotNull(savedStateHandle["requestId"])

    /**
     * 调用方（视障 Fragment / 志愿者 NavGraph）必填 role=BLIND/VOLUNTEER；
     * 双端各自只看自己采集的轨迹，互不串扰。兜底用 "VOLUNTEER"。
     */
    private val role: String = savedStateHandle["role"] ?: "VOLUNTEER"

    private val _uiState = MutableStateFlow(TrackPlaybackUiState())
    val uiState: StateFlow<TrackPlaybackUiState> = _uiState.asStateFlow()

    private var playbackJob: Job? = null

    /**
     * 预计算的累积表（按 role 过滤后排序的 points 顺序）。
     * 放成员字段而非 UiState：避免每帧 Flow 写入庞大数组造成重组开销。
     */
    private var orderedPoints: List<TrackPoint> = emptyList()
    private var cumDistanceM: DoubleArray = DoubleArray(0)
    private var cumDurationMs: LongArray = LongArray(0)

    init {
        loadTracks()
    }

    private fun loadTracks() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            runRequestRepository.getTracks(requestId)
                .onSuccess { allTracks ->
                    // 双端独立：只取自己角色的轨迹，不再 flatMap 两边
                    val tracks = allTracks.filter { it.role == role }
                    val points = tracks.flatMap { it.points }.sortedBy { it.t }
                    precomputeCumulatives(points)
                    val mapState = buildMapState(tracks, points, currentIndex = 0)
                    // 计算最终距离和时长
                    val finalDist = if (points.isNotEmpty()) cumDistanceM.last().toInt() else 0
                    val finalDur = if (points.isNotEmpty()) (cumDurationMs.last() / 1000L).toInt() else 0
                    _uiState.update {
                        it.copy(
                            tracks = tracks,
                            isLoading = false,
                            mapState = mapState,
                            totalPoints = points.size,
                            currentIndex = 0,
                            // 初始显示最终距离和时长
                            currentDistanceMeters = finalDist,
                            currentDurationSeconds = finalDur,
                            currentPaceSeconds = null,
                            finalDistanceMeters = finalDist,
                            finalDurationSeconds = finalDur,
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
                }
        }
    }

    /**
     * 预计算每个轨迹点的累积距离（Haversine）和累积时长（基于首点时间戳）。
     * 播放循环里直接查表，避免每帧重算。
     */
    private fun precomputeCumulatives(points: List<TrackPoint>) {
        orderedPoints = points
        if (points.isEmpty()) {
            cumDistanceM = DoubleArray(0)
            cumDurationMs = LongArray(0)
            return
        }
        cumDistanceM = DoubleArray(points.size)
        cumDurationMs = LongArray(points.size)
        cumDistanceM[0] = 0.0
        cumDurationMs[0] = 0L
        for (i in 1 until points.size) {
            val seg = PaceCalculator.distanceMeters(
                points[i - 1].lat, points[i - 1].lng,
                points[i].lat, points[i].lng,
            ).toDouble()
            cumDistanceM[i] = cumDistanceM[i - 1] + seg
            cumDurationMs[i] = points[i].t - points[0].t
        }
    }

    fun togglePlayback() {
        if (_uiState.value.isPlaying) {
            pausePlayback()
        } else {
            startPlayback()
        }
    }

    fun setSpeed(multiplier: Int) {
        _uiState.update { it.copy(speedMultiplier = multiplier) }
        if (_uiState.value.isPlaying) {
            playbackJob?.cancel()
            startPlayback()
        }
    }

    private fun startPlayback() {
        val state = _uiState.value
        val points = orderedPoints
        if (points.isEmpty()) return

        // 若上一轮已播放到末尾，再次点播放视为「从头开始」，但不动相机 —— 保留用户手势缩放后的视野。
        val startIdx = if (state.currentIndex >= points.size - 1) 0 else state.currentIndex

        // 重建 PaceWindow，把已经走过的最近 30s 或 100m 段喂进去，让续播时瞬时配速立刻可读
        val paceWindow = PaceWindow().apply { primeForIndex(points, startIdx) }

        _uiState.update {
            it.copy(
                isPlaying = true,
                currentIndex = startIdx,
                currentDistanceMeters = cumDistanceM[startIdx].toInt(),
                currentDurationSeconds = (cumDurationMs[startIdx] / 1000L).toInt(),
                currentPaceSeconds = paceWindow.currentPaceSecondsPerKm(points[startIdx].t),
                mapState = it.mapState.copy(
                    animatedMarker = points[startIdx].let { p -> Pair(p.lat, p.lng) },
                ),
            )
        }

        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            var idx = startIdx
            while (isActive && idx < points.size - 1) {
                val current = points[idx]
                val next = points[idx + 1]
                val intervalMs = ((next.t - current.t) / state.speedMultiplier).coerceIn(50L, 5000L)
                delay(intervalMs)
                idx++

                // PaceWindow 同步推进一段（用真实时间戳，与跑步采集端口径一致）
                val segM = PaceCalculator.distanceMeters(current.lat, current.lng, next.lat, next.lng)
                val segDt = (next.t - current.t).coerceAtLeast(0L)
                paceWindow.addSegment(segM, segDt, next.t)

                val nowDist = cumDistanceM[idx].toInt()
                val nowDur = (cumDurationMs[idx] / 1000L).toInt()
                val nowPace = paceWindow.currentPaceSecondsPerKm(next.t)
                _uiState.update { s ->
                    s.copy(
                        currentIndex = idx,
                        currentDistanceMeters = nowDist,
                        currentDurationSeconds = nowDur,
                        currentPaceSeconds = nowPace,
                        mapState = s.mapState.copy(animatedMarker = Pair(next.lat, next.lng)),
                    )
                }
            }
            if (idx >= points.size - 1) {
                _uiState.update { it.copy(isPlaying = false) }
            }
        }
    }

    private fun pausePlayback() {
        playbackJob?.cancel()
        playbackJob = null
        // 仅停止播放循环；currentDistance/Duration/Pace 保持在当前 marker 位置不动
        _uiState.update { it.copy(isPlaying = false) }
    }

    fun seekToStart() {
        playbackJob?.cancel()
        val tracks = _uiState.value.tracks
        val points = orderedPoints
        // 显式构造新的 mapState（含新 CameraTarget 实例），让地图把相机重新定位到起点；
        // 否则用户中途放大后调 seekToStart，相机不会复位。
        val mapState = buildMapState(tracks, points, currentIndex = 0)
        _uiState.update {
            it.copy(
                isPlaying = false,
                currentIndex = 0,
                // 重放时显示最终距离和时长
                currentDistanceMeters = it.finalDistanceMeters,
                currentDurationSeconds = it.finalDurationSeconds,
                currentPaceSeconds = null,
                mapState = mapState,
            )
        }
    }

    /**
     * 给续播场景的 PaceWindow 预热：把 startIdx 之前最近的若干段喂进窗口，
     * 让 currentPaceSecondsPerKm 立刻能返回非 null 值（PaceWindow 内部会按 30s/100m 自动 evict 老段）。
     */
    private fun PaceWindow.primeForIndex(points: List<TrackPoint>, startIdx: Int) {
        if (startIdx <= 0) return
        // 倒推最多 30 段（PaceWindow 自身会再按时间/距离窗口收敛）
        val from = (startIdx - 30).coerceAtLeast(1)
        for (i in from..startIdx) {
            val a = points[i - 1]
            val b = points[i]
            val seg = PaceCalculator.distanceMeters(a.lat, a.lng, b.lat, b.lng)
            addSegment(seg, (b.t - a.t).coerceAtLeast(0L), b.t)
        }
    }

    private fun buildMapState(
        tracks: List<RunTrack>,
        allPoints: List<TrackPoint>,
        currentIndex: Int,
    ): GuideRunMapState {
        val polylines = tracks.flatMap { track ->
            buildPaceColoredSegments(track.points.sortedBy { it.t })
        }

        val currentPoint = allPoints.getOrNull(currentIndex)
        val center = currentPoint ?: allPoints.firstOrNull()

        // 注意：每次调用 buildMapState 都会产出一个新的 CameraTarget 实例，触发地图重新定位。
        // 因此只在「初次加载」「seekToStart」等需要主动重定位的场景调用本方法；
        // 播放过程中的逐点刷新走 startPlayback 内的局部 copy，不要走这里。
        val cameraTarget = center?.let { CameraTarget(lat = it.lat, lng = it.lng, zoom = 16f) }

        return GuideRunMapState(
            cameraTarget = cameraTarget,
            polylines = polylines,
            animatedMarker = currentPoint?.let { Pair(it.lat, it.lng) },
        )
    }

    /**
     * 将轨迹点按相邻点配速切成多段，每段着不同颜色：快→绿、慢→红。
     */
    private fun buildPaceColoredSegments(points: List<TrackPoint>): List<PolylineConfig> {
        if (points.size < 2) return emptyList()
        val segments = mutableListOf<PolylineConfig>()
        var segStart = 0
        var prevPaceSecKm = paceBetween(points[0], points[1])
        for (i in 1 until points.size - 1) {
            val pace = paceBetween(points[i], points[i + 1])
            // 配速变化超过 60s/km 或距离超过 200m 时切段
            val distDelta = PaceCalculator.distanceMeters(
                points[i].lat, points[i].lng,
                points[segStart].lat, points[segStart].lng
            )
            if (kotlin.math.abs(pace - prevPaceSecKm) > 60 || distDelta > 200) {
                segments += PolylineConfig(
                    points = points.subList(segStart, i + 1).map { Pair(it.lat, it.lng) },
                    colorHex = paceToColor(prevPaceSecKm),
                    width = 10f,
                )
                segStart = i
            }
            prevPaceSecKm = pace
        }
        // 最后一段
        segments += PolylineConfig(
            points = points.subList(segStart, points.size).map { Pair(it.lat, it.lng) },
            colorHex = paceToColor(prevPaceSecKm),
            width = 10f,
        )
        return segments
    }

    /** 计算两点之间的配速（秒/公里），无效时返回 999 */
    private fun paceBetween(a: TrackPoint, b: TrackPoint): Int {
        val distM = PaceCalculator.distanceMeters(a.lat, a.lng, b.lat, b.lng)
        val timeS = ((b.t - a.t) / 1000.0).coerceAtLeast(1.0)
        if (distM < 1) return 999 // 几乎没移动
        return (timeS / distM * 1000).toInt()
    }

    /** 配速 → 颜色：快(红) → 慢(绿) */
    private fun paceToColor(paceSecKm: Int): String = when {
        paceSecKm < 240 -> "#E53935"  // < 4:00/km 红
        paceSecKm < 300 -> "#FB8C00"  // 4:00-5:00 橙
        paceSecKm < 360 -> "#FDD835"  // 5:00-6:00 黄
        paceSecKm < 480 -> "#7CB342"  // 6:00-8:00 草绿
        else -> "#00C853"             // > 8:00/km 翠绿
    }

    fun onErrorShown() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        playbackJob?.cancel()
    }
}
