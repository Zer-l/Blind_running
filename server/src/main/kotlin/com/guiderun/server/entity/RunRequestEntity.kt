package com.guiderun.server.entity

import com.guiderun.server.common.AbortBy
import com.guiderun.server.common.RunRequestStatus
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * 跑步请求实体（`run_requests` 表，9 状态生命周期）。
 *
 * - `@Version` 乐观锁：多志愿者抢单时数据库层只放一个通过，其余 409
 * - 各阶段时间戳（matched/departed/met/runStarted/runEnded/closed）支撑历史回放与统计
 * - 实际跑步指标（actualDistanceMeters/avgPaceSeconds）由轨迹上传时回写
 * - volunteerLat/Lng + updatedAt 缓存志愿者最新位置，避免每次推送查 RunTrack
 */
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
