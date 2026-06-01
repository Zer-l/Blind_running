package com.guiderun.server.entity

import com.guiderun.server.common.TriggeredRole
import jakarta.persistence.*
import java.time.Instant

/**
 * 订单状态变更审计事件（`run_request_events` 表）。
 * 每次状态转移落一条，便于回放排查与"志愿者放弃 3 次降级 ABORTED"等业务统计。
 */
@Entity
@Table(name = "run_request_events")
class RunRequestEventEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, length = 36)
    val requestId: String,

    @Column(length = 20)
    val fromStatus: String? = null,

    @Column(nullable = false, length = 20)
    val toStatus: String,

    @Column(length = 36)
    val triggeredBy: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val triggeredRole: TriggeredRole,

    @Column(length = 500)
    val reason: String? = null,

    @Column(nullable = false, updatable = false)
    val occurredAt: Instant = Instant.now(),
)
