package com.guiderun.app.domain.model

/** 志愿者成就徽章（unlockedAt 为解锁时所需的累计次数阈值）。 */
data class Badge(
    val id: String,
    val name: String,
    val unlockedAt: Int,
)

/**
 * 志愿者统计数据（用于个人中心 VolunteerStats 页展示）。
 *
 * 服务端聚合计算，客户端只读展示。badges 列表由服务端根据 totalRuns 阈值自动颁发。
 */
data class VolunteerStats(
    val totalRuns: Int,
    val totalHoursMinutes: Int,
    val totalDistanceMeters: Long,
    val currentMonthRuns: Int,
    val currentYearRuns: Int,
    val rating: Float?,
    val badges: List<Badge>,
)
