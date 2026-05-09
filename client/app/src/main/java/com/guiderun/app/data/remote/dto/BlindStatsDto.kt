package com.guiderun.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class BlindStatsDto(
    val totalRuns: Int,
    val totalDistanceMeters: Long,
    val totalDurationMinutes: Int,
    val currentMonthRuns: Int,
    val averageRunDurationMinutes: Int? = null,
)
