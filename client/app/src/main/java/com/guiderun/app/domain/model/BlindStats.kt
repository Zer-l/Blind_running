package com.guiderun.app.domain.model

data class BlindStats(
    val totalRuns: Int,
    val totalDistanceMeters: Long,
    val totalDurationMinutes: Int,
    val currentMonthRuns: Int,
    val averageRunDurationMinutes: Int?,
)
