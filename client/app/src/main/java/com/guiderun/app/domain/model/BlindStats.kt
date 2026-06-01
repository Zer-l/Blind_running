package com.guiderun.app.domain.model

/**
 * 视障跑者统计数据（用于个人中心 BlindStats 页展示）。
 *
 * 数据由服务端聚合计算，客户端只做展示，无本地写入。
 * averageRunDurationMinutes 为 null 表示尚无完成记录（避免除零问题）。
 */
data class BlindStats(
    val totalRuns: Int,
    val totalDistanceMeters: Long,
    val totalDurationMinutes: Int,
    val currentMonthRuns: Int,
    val averageRunDurationMinutes: Int?,
)
