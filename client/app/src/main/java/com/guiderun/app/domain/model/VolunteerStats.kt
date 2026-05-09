package com.guiderun.app.domain.model

data class Badge(
    val id: String,
    val name: String,
    val unlockedAt: Int,
)

data class VolunteerStats(
    val totalRuns: Int,
    val totalHoursMinutes: Int,
    val totalDistanceMeters: Long,
    val currentMonthRuns: Int,
    val currentYearRuns: Int,
    val rating: Float?,
    val badges: List<Badge>,
)
