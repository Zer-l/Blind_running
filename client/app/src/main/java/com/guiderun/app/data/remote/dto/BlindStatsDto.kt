package com.guiderun.app.data.remote.dto

import kotlinx.serialization.Serializable

/** 视障端跑步统计响应 DTO，由服务端聚合计算后下发，客户端只展示不本地计算。 */
@Serializable
data class BlindStatsDto(
    val totalRuns: Int,
    val totalDistanceMeters: Long,
    val totalDurationMinutes: Int,
    val currentMonthRuns: Int,
    val averageRunDurationMinutes: Int? = null,
)
