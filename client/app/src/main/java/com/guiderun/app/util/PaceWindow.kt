package com.guiderun.app.util

import kotlin.math.max
import kotlin.math.min

/**
 * 基于"最近 N 秒 或 N 米"双约束的瞬时配速窗口（先满足者为准）。
 *
 * 设计目的：
 * - 配速 = 最近一段路程的"秒/公里"，窗口语义不随采样间隔变化。
 * - 采样间隔从 2s 抖动到 5s 时，传统的"固定点数滑动平均"窗口长度会从 20s 漂到 50s，
 *   PaceWindow 以"距离/时间"两个绝对量约束窗口，避免该问题。
 *
 * 用法：
 *   val pw = PaceWindow()
 *   pw.addSegment(distMeters = 12.3f, dtMs = 2000L)
 *   val pace = pw.currentPaceSecondsPerKm() // 秒/公里，或 null
 */
class PaceWindow(
    /** 时间窗口上限（毫秒），默认 30s */
    private val maxAgeMs: Long = DEFAULT_MAX_AGE_MS,
    /** 距离窗口上限（米），默认 100m；累计距离超过该值后从队头丢弃 */
    private val maxDistanceM: Double = DEFAULT_MAX_DISTANCE_M,
    /** 起算最小有效距离（米），低于该值不入窗，避免静止漂移污染配速 */
    private val minSegmentDistanceM: Float = DEFAULT_MIN_SEGMENT_M,
    /** 起算最小有效速度（米/秒），低于该值不入窗 */
    private val minSegmentSpeedMps: Float = DEFAULT_MIN_SEGMENT_SPEED,
    /** 最大有效速度（米/秒），高于该值视为离群点丢弃 */
    private val maxSegmentSpeedMps: Float = DEFAULT_MAX_SEGMENT_SPEED,
) {

    private data class Segment(val distM: Double, val dtMs: Long, val tEndMs: Long)

    private val segments = ArrayDeque<Segment>()
    private var totalDistM: Double = 0.0
    private var totalDtMs: Long = 0L

    /**
     * 加入一段位移。返回是否成功入窗。
     *
     * @param distMeters 该段位移（米）
     * @param dtMs 该段持续时间（毫秒）
     * @param nowRealtimeMs 该段终点对应的 elapsedRealtime 时间戳（毫秒）
     */
    fun addSegment(distMeters: Float, dtMs: Long, nowRealtimeMs: Long): Boolean {
        if (dtMs <= 0L || distMeters < minSegmentDistanceM) return false
        val speed = distMeters / (dtMs / 1000f)
        if (speed < minSegmentSpeedMps || speed > maxSegmentSpeedMps) return false

        segments.addLast(Segment(distMeters.toDouble(), dtMs, nowRealtimeMs))
        totalDistM += distMeters
        totalDtMs += dtMs

        evictByAge(nowRealtimeMs)
        evictByDistance()
        return true
    }

    /**
     * 当前瞬时配速（秒/公里），窗口内样本不足时返回 null。
     *
     * @param nowRealtimeMs 当前 elapsedRealtime 时间戳（ms）。传入时会先按该时间清理过期段，
     *                      避免"长时间无新段进入"时旧值被永久挂着（暂停 / 采集间隔被拉长场景）。
     *                      传 -1L 则不做读时清理（兼容旧调用，仅依赖 addSegment 触发的清理）。
     */
    fun currentPaceSecondsPerKm(nowRealtimeMs: Long = -1L): Int? {
        if (nowRealtimeMs > 0L) evictByAge(nowRealtimeMs)
        if (totalDistM < MIN_TOTAL_DIST_FOR_PACE_M || totalDtMs <= 0L) return null
        val secPerKm = (totalDtMs / 1000.0) / (totalDistM / 1000.0)
        // 上下界保护：避免极值（站立/瞬移）穿透
        val clamped = secPerKm.coerceIn(MIN_PACE_SEC_PER_KM, MAX_PACE_SEC_PER_KM)
        return clamped.toInt()
    }

    fun reset() {
        segments.clear()
        totalDistM = 0.0
        totalDtMs = 0L
    }

    private fun evictByAge(nowRealtimeMs: Long) {
        val cutoff = nowRealtimeMs - maxAgeMs
        while (segments.isNotEmpty() && segments.first().tEndMs < cutoff) {
            val s = segments.removeFirst()
            totalDistM = max(0.0, totalDistM - s.distM)
            totalDtMs = max(0L, totalDtMs - s.dtMs)
        }
    }

    private fun evictByDistance() {
        while (segments.size > 1 && totalDistM > maxDistanceM) {
            val s = segments.removeFirst()
            totalDistM = max(0.0, totalDistM - s.distM)
            totalDtMs = max(0L, totalDtMs - s.dtMs)
        }
    }

    companion object {
        private const val DEFAULT_MAX_AGE_MS = 30_000L
        private const val DEFAULT_MAX_DISTANCE_M = 100.0
        private const val DEFAULT_MIN_SEGMENT_M = 1.5f
        private const val DEFAULT_MIN_SEGMENT_SPEED = 0.6f
        private const val DEFAULT_MAX_SEGMENT_SPEED = 12f
        private const val MIN_TOTAL_DIST_FOR_PACE_M = 5.0
        private const val MIN_PACE_SEC_PER_KM = 120.0   // 2'00"/km，逼近世界纪录
        private const val MAX_PACE_SEC_PER_KM = 1_500.0 // 25'00"/km，慢走极限
    }
}

/**
 * 用于显示侧的指数移动平均（EMA）柔化数据曲线。
 * 不影响底层 stats，仅作为 UI 平滑显示。
 */
class Ema(private val alpha: Double = 0.3) {
    private var value: Double? = null

    fun update(sample: Double): Double {
        val prev = value
        val next = if (prev == null) sample else prev + alpha * (sample - prev)
        value = next
        return next
    }

    fun snapshot(): Double? = value

    fun reset() {
        value = null
    }
}

/** 工具：把 Float/Double 限制在合法范围。 */
internal fun Double.clampPositive(min: Double, max: Double): Double =
    min(max, max(min, this))
