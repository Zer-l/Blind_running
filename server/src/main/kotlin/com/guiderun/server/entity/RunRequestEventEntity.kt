package com.guiderun.server.entity

import com.guiderun.server.common.TriggeredRole
import jakarta.persistence.*
import java.time.Instant

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
