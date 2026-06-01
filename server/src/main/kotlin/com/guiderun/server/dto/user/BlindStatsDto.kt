package com.guiderun.server.dto.user

/** 视障端跑步统计响应：服务端聚合后下发，客户端零计算直接展示。 */
data class BlindStatsDto(
    val totalRuns: Int,
    val totalDistanceMeters: Long,
    val totalDurationMinutes: Int,
    val currentMonthRuns: Int,
    val averageRunDurationMinutes: Int?,
)
