package com.guiderun.server.dto.user

data class BlindStatsDto(
    val totalRuns: Int,
    val totalDistanceMeters: Long,
    val totalDurationMinutes: Int,
    val currentMonthRuns: Int,
    val averageRunDurationMinutes: Int?,
)
