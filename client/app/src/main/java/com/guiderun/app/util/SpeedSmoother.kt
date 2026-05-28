package com.guiderun.app.util

import kotlin.math.max

/**
 * 基于时间戳的速度滑动平均：对最近 [windowMs] 毫秒内的速度样本取均值。
 *
 * 设计目的：
 * - 配速直接由 GPS 多普勒速度（Location.getSpeed）驱动，多普勒本身噪声已很低（~0.1 m/s），
 *   只需一层短窗滑动平均即可得到平滑且跟手的瞬时配速，避免"30s 窗口 + EMA"双层平滑的滞后。
 * - 窗口按真实时间（5s）约束，与采样频率解耦；1Hz 采样下即最近 5 个样本。
 *
 * 用法：
 *   val s = SpeedSmoother()
 *   s.add(speedMps = 2.6f, nowRealtimeMs = ...)
 *   val pace = s.currentPaceSecondsPerKm(nowRealtimeMs)  // 秒/公里，或 null
 */
class SpeedSmoother(
    /** 平滑窗口（毫秒），默认 5s */
    private val windowMs: Long = DEFAULT_WINDOW_MS,
) {

    private data class Sample(val speedMps: Float, val tMs: Long)

    private val samples = ArrayDeque<Sample>()
    private var sumSpeed: Double = 0.0

    /** 加入一个速度样本（m/s）。负值按 0 处理。 */
    fun add(speedMps: Float, nowRealtimeMs: Long) {
        val v = max(0f, speedMps)
        samples.addLast(Sample(v, nowRealtimeMs))
        sumSpeed += v
        evictByAge(nowRealtimeMs)
    }

    /** 当前平滑速度（m/s）；窗口为空返回 null。 */
    fun currentSpeedMps(nowRealtimeMs: Long = -1L): Float? {
        if (nowRealtimeMs > 0L) evictByAge(nowRealtimeMs)
        if (samples.isEmpty()) return null
        return (sumSpeed / samples.size).toFloat()
    }

    /**
     * 当前瞬时配速（秒/公里）。
     * 平滑速度低于 [MIN_VALID_SPEED_MPS]（近似静止）时返回 null，避免配速冲到极大值。
     */
    fun currentPaceSecondsPerKm(nowRealtimeMs: Long = -1L): Int? {
        val v = currentSpeedMps(nowRealtimeMs) ?: return null
        if (v < MIN_VALID_SPEED_MPS) return null
        val secPerKm = 1000.0 / v
        return secPerKm.coerceIn(MIN_PACE_SEC_PER_KM, MAX_PACE_SEC_PER_KM).toInt()
    }

    fun reset() {
        samples.clear()
        sumSpeed = 0.0
    }

    private fun evictByAge(nowRealtimeMs: Long) {
        val cutoff = nowRealtimeMs - windowMs
        while (samples.isNotEmpty() && samples.first().tMs < cutoff) {
            val s = samples.removeFirst()
            sumSpeed = max(0.0, sumSpeed - s.speedMps)
        }
    }

    companion object {
        private const val DEFAULT_WINDOW_MS = 5_000L
        // 低于该速度（1.8 km/h）视为静止，不给配速。与采集端移动门控 MOVING_SPEED_MPS 一致。
        private const val MIN_VALID_SPEED_MPS = 0.5f
        // 配速上下界：最快 2'30"/km（24km/h，业余绝不可能持续 → GPS 尖刺护栏）；
        // 最慢 25'00"/km（2.4km/h，覆盖助盲跑常见慢走段）。
        private const val MIN_PACE_SEC_PER_KM = 150.0
        private const val MAX_PACE_SEC_PER_KM = 1_500.0
    }
}
