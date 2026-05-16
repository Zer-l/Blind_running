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

data class TrackPlaybackUiState(
    val tracks: List<RunTrack> = emptyList(),
    val isLoading: Boolean = true,
    val mapState: GuideRunMapState = GuideRunMapState(),
    val errorMessage: String? = null,
    val isPlaying: Boolean = false,
    val speedMultiplier: Int = 5,
    val currentIndex: Int = 0,
    val totalPoints: Int = 0,
    val avgPaceSeconds: Int? = null,
    val maxSpeed: Float? = null,
)

@HiltViewModel
class TrackPlaybackViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val runRequestRepository: RunRequestRepository,
) : ViewModel() {

    private val requestId: String = checkNotNull(savedStateHandle["requestId"])

    private val _uiState = MutableStateFlow(TrackPlaybackUiState())
    val uiState: StateFlow<TrackPlaybackUiState> = _uiState.asStateFlow()

    private var playbackJob: Job? = null

    init {
        loadTracks()
    }

    private fun loadTracks() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            runRequestRepository.getTracks(requestId)
                .onSuccess { tracks ->
                    val allPoints = tracks.flatMap { it.points }.sortedBy { it.t }
                    val mapState = buildMapState(tracks, allPoints, currentIndex = 0)
                    val avgPace = computeAvgPace(tracks)
                    val maxSpd = tracks.maxOfOrNull { it.maxSpeed ?: 0f }?.takeIf { it > 0f }
                    _uiState.update {
                        it.copy(
                            tracks = tracks,
                            isLoading = false,
                            mapState = mapState,
                            totalPoints = allPoints.size,
                            avgPaceSeconds = avgPace,
                            maxSpeed = maxSpd,
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
                }
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
        if (state.tracks.isEmpty()) return
        val allPoints = state.tracks.flatMap { it.points }.sortedBy { it.t }
        if (allPoints.isEmpty()) return

        // 若上一轮已播放到末尾，再次点播放视为「从头开始」，但不动相机 —— 保留用户手势缩放后的视野。
        val startIdx = if (state.currentIndex >= allPoints.size - 1) 0 else state.currentIndex
        _uiState.update {
            it.copy(
                isPlaying = true,
                currentIndex = startIdx,
                mapState = it.mapState.copy(
                    animatedMarker = allPoints[startIdx].let { p -> Pair(p.lat, p.lng) },
                ),
            )
        }
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            var idx = startIdx
            while (isActive && idx < allPoints.size - 1) {
                val current = allPoints[idx]
                val next = allPoints[idx + 1]
                val intervalMs = ((next.t - current.t) / state.speedMultiplier).coerceIn(50L, 5000L)
                delay(intervalMs)
                idx++
                _uiState.update { s ->
                    val marker = Pair(next.lat, next.lng)
                    s.copy(
                        currentIndex = idx,
                        mapState = s.mapState.copy(animatedMarker = marker),
                    )
                }
            }
            if (idx >= allPoints.size - 1) {
                _uiState.update { it.copy(isPlaying = false) }
            }
        }
    }

    private fun pausePlayback() {
        playbackJob?.cancel()
        playbackJob = null
        _uiState.update { it.copy(isPlaying = false) }
    }

    fun seekToStart() {
        playbackJob?.cancel()
        val state = _uiState.value
        val allPoints = state.tracks.flatMap { it.points }.sortedBy { it.t }
        // 显式构造新的 mapState（含新 CameraTarget 实例），让地图把相机重新定位到起点；
        // 否则用户中途放大后调 seekToStart，相机不会复位。
        val mapState = buildMapState(state.tracks, allPoints, currentIndex = 0)
        _uiState.update {
            it.copy(isPlaying = false, currentIndex = 0, mapState = mapState)
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

        // 贝塞尔插值用于动画路径
        val smoothPath = interpolateBezier(allPoints)

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

    private fun computeAvgPace(tracks: List<RunTrack>): Int? {
        val totalDist = tracks.sumOf { it.totalDistanceMeters }
        val totalDur = tracks.sumOf { it.totalDurationSeconds }
        if (totalDist <= 0 || totalDur <= 0) return null
        return (totalDur * 1000.0 / totalDist).toInt()
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

    /**
     * Catmull-Rom 贝塞尔曲线插值，让回放路径更平滑。
     * 每两个原始点之间插入 [segments] 个中间点。
     */
    private fun interpolateBezier(points: List<TrackPoint>, segments: Int = 5): List<Pair<Double, Double>> {
        if (points.size < 2) return points.map { Pair(it.lat, it.lng) }

        val result = mutableListOf<Pair<Double, Double>>()
        for (i in 0 until points.size - 1) {
            val p0 = points[max(0, i - 1)]
            val p1 = points[i]
            val p2 = points[i + 1]
            val p3 = points[min(points.size - 1, i + 2)]

            for (t in 0 until segments) {
                val ratio = t.toDouble() / segments
                val lat = catmullRom(p0.lat, p1.lat, p2.lat, p3.lat, ratio)
                val lng = catmullRom(p0.lng, p1.lng, p2.lng, p3.lng, ratio)
                result.add(Pair(lat, lng))
            }
        }
        result.add(Pair(points.last().lat, points.last().lng))
        return result
    }

    /** Catmull-Rom 样条插值公式 */
    private fun catmullRom(p0: Double, p1: Double, p2: Double, p3: Double, t: Double): Double {
        val t2 = t * t
        val t3 = t2 * t
        return 0.5 * ((2 * p1) +
                (-p0 + p2) * t +
                (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 +
                (-p0 + 3 * p1 - 3 * p2 + p3) * t3)
    }

    fun onErrorShown() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        playbackJob?.cancel()
    }
}
