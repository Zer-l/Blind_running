package com.guiderun.server.service

import com.guiderun.server.common.RunRequestStatus
import com.guiderun.server.dto.user.BadgeDto
import com.guiderun.server.dto.user.VolunteerStatsDto
import com.guiderun.server.entity.UserEntity
import com.guiderun.server.exception.AppException
import com.guiderun.server.exception.ErrorCode
import com.guiderun.server.repository.RunRequestJpaRepository
import com.guiderun.server.repository.RunTrackJpaRepository
import com.guiderun.server.repository.UserJpaRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * 志愿者跑步统计聚合：总次数 / 总距离 / 总时长 / 本月/本年次数 / 评分 / 徽章列表。
 * 徽章规则见 [buildBadges]：次数里程碑 + 距离里程碑 + 夜间陪跑（21:00-06:00）。
 */
@Service
@Transactional(readOnly = true)
class VolunteerStatsService(
    private val userRepo: UserJpaRepository,
    private val requestRepo: RunRequestJpaRepository,
    private val trackRepo: RunTrackJpaRepository,
) {

    fun getStats(userId: String): VolunteerStatsDto {
        val user = userRepo.findById(userId).orElseThrow {
            AppException(ErrorCode.USER_NOT_FOUND, "用户不存在", HttpStatus.NOT_FOUND)
        }

        val now = ZonedDateTime.now(ZoneOffset.UTC)
        val monthStart = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).toInstant()
        val yearStart = now.withDayOfYear(1).withHour(0).withMinute(0).withSecond(0).toInstant()

        val closedByMe = requestRepo.findByVolunteerIdAndStatus(userId, RunRequestStatus.CLOSED)
        val currentMonthRuns = closedByMe.count { it.closedAt != null && it.closedAt!! >= monthStart }
        val currentYearRuns = closedByMe.count { it.closedAt != null && it.closedAt!! >= yearStart }
        val totalDistanceMeters = trackRepo.sumTotalDistanceMetersByUserId(userId)
        val rating = if (user.ratingCount > 0) user.ratingSum.toFloat() / user.ratingCount else null

        return VolunteerStatsDto(
            totalRuns = user.totalRuns,
            totalHoursMinutes = user.totalHoursMinutes,
            totalDistanceMeters = totalDistanceMeters,
            currentMonthRuns = currentMonthRuns,
            currentYearRuns = currentYearRuns,
            rating = rating,
            badges = buildBadges(user.totalRuns, totalDistanceMeters, closedByMe),
        )
    }

    private fun buildBadges(totalRuns: Int, totalDistanceMeters: Long, closedRequests: List<*>): List<BadgeDto> {
        val badges = mutableListOf<BadgeDto>()

        // 次数里程碑
        val runMilestones = listOf(1 to "初次陪伴", 5 to "初探者", 10 to "陪跑达人", 30 to "坚持者", 50 to "资深向导", 100 to "陪跑英雄")
        runMilestones.filter { totalRuns >= it.first }.forEach { (threshold, name) ->
            badges += BadgeDto(id = "runs_$threshold", name = name, unlockedAt = threshold)
        }

        // 距离里程碑（米）
        val distMilestones = listOf(
            5_000L to "五公里达人",
            21_097L to "半马里程",
            42_195L to "全马里程",
            100_000L to "百公里传奇",
            500_000L to "五百公里征途",
        )
        distMilestones.filter { totalDistanceMeters >= it.first }.forEach { (threshold, name) ->
            badges += BadgeDto(id = "dist_$threshold", name = name, unlockedAt = threshold.toInt())
        }

        // 夜间陪跑（跑步开始时间在 21:00-06:00 之间）
        val nightRuns = closedRequests.filterIsInstance<com.guiderun.server.entity.RunRequestEntity>()
            .count { req ->
                val start = req.runStartedAt ?: req.createdAt
                val hour = start.atZone(ZoneOffset.systemDefault()).hour
                hour >= 21 || hour < 6
            }
        if (nightRuns >= 1) badges += BadgeDto(id = "night_1", name = "夜间守护", unlockedAt = 1)
        if (nightRuns >= 10) badges += BadgeDto(id = "night_10", name = "夜行者", unlockedAt = 10)

        return badges
    }
}
