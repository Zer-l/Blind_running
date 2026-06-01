package com.guiderun.server.dto.user

/** 志愿者徽章：id 唯一，name 展示文案，unlockedAt 记录解锁阈值（次数或距离值）。 */
data class BadgeDto(val id: String, val name: String, val unlockedAt: Int)

/** 志愿者跑步统计响应：含本月/本年次数 + 评分 + 徽章列表。 */
data class VolunteerStatsDto(
    val totalRuns: Int,
    val totalHoursMinutes: Int,
    val totalDistanceMeters: Long,
    val currentMonthRuns: Int,
    val currentYearRuns: Int,
    val rating: Float?,
    val badges: List<BadgeDto>,
)
