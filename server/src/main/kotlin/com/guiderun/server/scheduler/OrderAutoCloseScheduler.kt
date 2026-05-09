package com.guiderun.server.scheduler

import com.guiderun.server.common.RunRequestStatus
import com.guiderun.server.common.TriggeredRole
import com.guiderun.server.entity.RunRequestEventEntity
import com.guiderun.server.repository.ReviewJpaRepository
import com.guiderun.server.repository.RunRequestEventJpaRepository
import com.guiderun.server.repository.RunRequestJpaRepository
import com.guiderun.server.repository.UserJpaRepository
import com.guiderun.server.websocket.GuideRunWebSocketHandler
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

@Component
class OrderAutoCloseScheduler(
    private val requestRepo: RunRequestJpaRepository,
    private val eventRepo: RunRequestEventJpaRepository,
    private val reviewRepo: ReviewJpaRepository,
    private val userRepo: UserJpaRepository,
    private val wsHandler: GuideRunWebSocketHandler,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 * * * *") // 每小时整点
    @Transactional
    fun autoCloseExpiredOrders() {
        val now = Instant.now()
        val threshold24h = now.minus(24, ChronoUnit.HOURS)
        val threshold23h = now.minus(23, ChronoUnit.HOURS)

        val expiredOrders = requestRepo.findByStatusAndRunEndedAtBefore(RunRequestStatus.FINISHED, threshold24h)
        if (expiredOrders.isEmpty()) return

        log.info("autoClose: {} FINISHED orders expired", expiredOrders.size)
        expiredOrders.forEach { entity ->
            entity.status = RunRequestStatus.CLOSED
            entity.closedAt = now
            requestRepo.save(entity)

            eventRepo.save(
                RunRequestEventEntity(
                    requestId = entity.id,
                    fromStatus = RunRequestStatus.FINISHED.name,
                    toStatus = RunRequestStatus.CLOSED.name,
                    triggeredRole = TriggeredRole.SYSTEM,
                    triggeredBy = null,
                    reason = "FINISHED状态超时未评价，系统自动关闭",
                )
            )

            val recipients = listOfNotNull(entity.blindRunnerId, entity.volunteerId)
            wsHandler.pushStatusChanged(entity.id, RunRequestStatus.CLOSED, entity.version, recipients, TriggeredRole.SYSTEM.name)

            // 仍更新 totalRuns，但不更新 rating（超时未评价不补）
            val blindRunner = userRepo.findById(entity.blindRunnerId).orElse(null) ?: return@forEach
            val volunteer = entity.volunteerId?.let { userRepo.findById(it).orElse(null) } ?: return@forEach
            blindRunner.totalRuns++
            volunteer.totalRuns++
            val runMinutes = (entity.actualDurationSeconds ?: 0) / 60
            blindRunner.totalHoursMinutes += runMinutes
            volunteer.totalHoursMinutes += runMinutes
            userRepo.saveAll(listOf(blindRunner, volunteer))

            log.info("autoClose: closed requestId={}", entity.id)
        }

        // 23小时提醒：距离超时还剩1小时的订单
        val reminder23h = requestRepo.findByStatusAndRunEndedAtBefore(RunRequestStatus.FINISHED, threshold23h)
            .filter { it.runEndedAt != null && it.runEndedAt!! >= threshold24h }
        reminder23h.forEach { entity ->
            val recipients = listOfNotNull(entity.blindRunnerId, entity.volunteerId)
            wsHandler.pushStatusChanged(
                requestId = entity.id,
                toStatus = RunRequestStatus.FINISHED,
                version = entity.version,
                userIds = recipients,
                triggeredRole = TriggeredRole.SYSTEM.name,
                isReminder = true,
            )
            log.debug("autoClose: 23h reminder sent for requestId={}", entity.id)
        }
    }

    // dev profile 专用：手动触发自动关闭，用于测试
    @Transactional
    fun triggerManually() = autoCloseExpiredOrders()
}
