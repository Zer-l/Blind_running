package com.guiderun.server.dto.user

data class BadgeDto(val id: String, val name: String, val unlockedAt: Int)

data class VolunteerStatsDto(
    val totalRuns: Int,
    val totalHoursMinutes: Int,
    val totalDistanceMeters: Long,
    val currentMonthRuns: Int,
    val currentYearRuns: Int,
    val rating: Float?,
    val badges: List<BadgeDto>,
)
