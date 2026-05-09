package com.guiderun.server.entity

import com.guiderun.server.common.AbortBy
import com.guiderun.server.common.RunRequestStatus
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "run_requests")
class RunRequestEntity(
    @Id
    @Column(length = 36)
    val id: String = UUID.randomUUID().toString(),

    @Column(nullable = false, length = 36)
    val blindRunnerId: String,

    @Column(length = 36)
    var volunteerId: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: RunRequestStatus = RunRequestStatus.MATCHING,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column var matchedAt: Instant? = null,
    @Column var departedAt: Instant? = null,
    @Column var metAt: Instant? = null,
    @Column var runStartedAt: Instant? = null,
    @Column var runEndedAt: Instant? = null,
    @Column var closedAt: Instant? = null,

    @Column(nullable = false)
    val expectedDurationMinutes: Int,

    @Column val expectedDistanceMeters: Int? = null,
    @Column val expectedPaceSeconds: Int? = null,

    @Column(nullable = false)
    val meetingLat: Double,

    @Column(nullable = false)
    val meetingLng: Double,

    @Column(nullable = false, length = 200)
    val meetingLocationDesc: String,

    @Column var actualDistanceMeters: Int? = null,
    @Column var actualDurationSeconds: Int? = null,
    @Column var avgPaceSeconds: Int? = null,
    @Column(nullable = false) var isAbnormal: Boolean = false,

    @Column(columnDefinition = "TEXT")
    val notes: String? = null,

    @Column(length = 200)
    var abortReason: String? = null,

    @Enumerated(EnumType.STRING)
    @Column
    var abortBy: AbortBy? = null,

    @Column var volunteerLat: Double? = null,
    @Column var volunteerLng: Double? = null,
    @Column var volunteerPositionUpdatedAt: Instant? = null,

    @Version
    var version: Int = 0,

    @Column(nullable = false)
    var updatedAt: Instant = Instant.now(),
)
