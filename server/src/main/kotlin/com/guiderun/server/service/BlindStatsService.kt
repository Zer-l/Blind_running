package com.guiderun.server.service

import com.guiderun.server.common.RunRequestStatus
import com.guiderun.server.dto.user.BlindStatsDto
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

@Service
@Transactional(readOnly = true)
class BlindStatsService(
    private val userRepo: UserJpaRepository,
    private val requestRepo: RunRequestJpaRepository,
    private val trackRepo: RunTrackJpaRepository,
) {

    fun getStats(userId: String): BlindStatsDto {
        val user = userRepo.findById(userId).orElseThrow {
            AppException(ErrorCode.USER_NOT_FOUND, "用户不存在", HttpStatus.NOT_FOUND)
        }

        val now = ZonedDateTime.now(ZoneOffset.UTC)
        val monthStart = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).toInstant()

        val closedByMe = requestRepo.findByBlindRunnerIdAndStatus(userId, RunRequestStatus.CLOSED)
        val currentMonthRuns = closedByMe.count { it.closedAt != null && it.closedAt!! >= monthStart }
        val totalDistanceMeters = trackRepo.sumTotalDistanceMetersByUserId(userId)

        return BlindStatsDto(
            totalRuns = user.totalRuns,
            totalDistanceMeters = totalDistanceMeters,
            totalDurationMinutes = user.totalHoursMinutes,
            currentMonthRuns = currentMonthRuns,
            averageRunDurationMinutes = if (user.totalRuns > 0) user.totalHoursMinutes / user.totalRuns else null,
        )
    }
}
