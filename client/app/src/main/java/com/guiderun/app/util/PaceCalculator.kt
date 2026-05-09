package com.guiderun.app.util

/**
 * 配速计算工具，使用5点滑动平均。
 * 配速单位：秒/公里。
 */
object PaceCalculator {

    /**
     * 根据距离和时间计算即时配速。
     * @param distanceMeters 距离（米）
     * @param durationSeconds 时间（秒）
     * @return 配速（秒/公里），无效时返回 null
     */
    fun currentPace(distanceMeters: Int, durationSeconds: Int): Int? {
        if (distanceMeters <= 0 || durationSeconds <= 0) return null
        val distanceKm = distanceMeters / 1000.0
        return (durationSeconds / distanceKm).toInt()
    }

    /**
     * 根据总距离和总时间计算平均配速。
     * @param totalDistanceMeters 总距离（米）
     * @param totalDurationSeconds 总时间（秒）
     * @return 配速（秒/公里），无效时返回 null
     */
    fun avgPace(totalDistanceMeters: Int, totalDurationSeconds: Int): Int? {
        if (totalDistanceMeters <= 0 || totalDurationSeconds <= 0) return null
        val distanceKm = totalDistanceMeters / 1000.0
        return (totalDurationSeconds / distanceKm).toInt()
    }

    /**
     * 5点滑动平均配速。
     * @param recentPaces 最近的配速样本（秒/公里），最多取最后5个
     * @return 平均配速（秒/公里），样本为空时返回 null
     */
    fun slidingAverage(recentPaces: List<Int>): Int? {
        if (recentPaces.isEmpty()) return null
        val window = recentPaces.takeLast(5)
        return window.average().toInt()
    }

    /**
     * 将配速格式化为 "分'秒"" 形式。
     * @param paceSeconds 配速（秒/公里）
     * @return 格式化字符串，如 "5'30""
     */
    fun formatPace(paceSeconds: Int): String {
        val min = paceSeconds / 60
        val sec = paceSeconds % 60
        return "%d'%02d\"".format(min, sec)
    }

    /**
     * Haversine 公式计算两点间距离（米）。
     */
    fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
        val r = 6_371_000.0 // 地球半径（米）
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = kotlin.math.sin(dLat / 2).let { it * it } +
                kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
                kotlin.math.sin(dLng / 2).let { it * it }
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return (r * c).toFloat()
    }
}
