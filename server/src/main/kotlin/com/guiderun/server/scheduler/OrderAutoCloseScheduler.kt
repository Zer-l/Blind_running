package com.guiderun.server.scheduler

import com.guiderun.server.common.RunRequestStatus
import com.guiderun.server.common.TriggeredRole
import com.guiderun.server.entity.RunRequestEventEntity
import com.guiderun.server.repository.ReviewJpaRepository
import com.guiderun.server.repository.RunRequestEventJpaRepository
import com.guiderun.server.repository.RunRequestJpaRepository
import com.guiderun.server.websocket.GuideRunWebSocketHandler
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * 订单自动关闭定时任务：每小时整点扫描超时 24h 仍处于 FINISHED 的订单，
 * 推进至 CLOSED 终态并通过 WebSocket 广播 status_changed。
 *
 * 设计要点：
 * - 23h 提醒：剩余 1 小时窗口内向双方推一次"待评价提醒"（isReminder=true）
 * - 不重复累加跑步统计：totalRuns / totalHoursMinutes 已在 FINISHED 时计入
 * - rating 仅由实际评价提交时累加，超时未评不补
 *
 * dev profile 下可通过 [com.guiderun.server.controller.AdminController.closeFinishedOrders]
 * 调用 [triggerManually] 立即触发用于本地验证。
 */
@Component
class OrderAutoCloseScheduler(
    private val requestRepo: RunRequestJpaRepository,
    private val eventRepo: RunRequestEventJpaRepository,
    private val reviewRepo: ReviewJpaRepository,
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

            // totalRuns/totalHoursMinutes 已在 FINISHED 时（RunRequestService.applyFinishStats）计入，
            // 24h 兜底关单不再重复累加；超时未评价同样不补 rating。

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
