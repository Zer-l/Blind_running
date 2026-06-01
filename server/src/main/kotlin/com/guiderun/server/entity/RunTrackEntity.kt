package com.guiderun.server.entity

import io.hypersistence.utils.hibernate.type.json.JsonType
import jakarta.persistence.*
import org.hibernate.annotations.Type
import java.time.Instant
import java.util.UUID

/**
 * 跑步轨迹实体（`run_tracks` 表，按 requestId+userId 区分双方各自的轨迹流）。
 *
 * - 轨迹点存 JSON 列（[TrackPointJson]），避免一对多明细表带来的 N+1
 * - 统计字段（distance/duration/pace/maxSpeed）由 [com.guiderun.server.service.RunTrackService] 重算 + 客户端权威值覆盖
 */
@Entity
@Table(name = "run_tracks")
class RunTrackEntity(
    @Id
    @Column(length = 36)
    val id: String = UUID.randomUUID().toString(),

    @Column(nullable = false, length = 36)
    val requestId: String,

    @Column(nullable = false, length = 36)
    val userId: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "ENUM('BLIND','VOLUNTEER')")
    val role: TrackRole,

    @Type(JsonType::class)
    @Column(columnDefinition = "JSON", nullable = false)
    var points: List<TrackPointJson> = emptyList(),

    @Column(nullable = false)
    var pointCount: Int = 0,

    @Column(nullable = false)
    var totalDistanceMeters: Int = 0,

    @Column(nullable = false)
    var totalDurationSeconds: Int = 0,

    var avgPaceSeconds: Int? = null,

    var maxSpeed: Float? = null,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)

/** 轨迹归属角色：同一订单两端各持一条 RunTrackEntity。 */
enum class TrackRole { BLIND, VOLUNTEER }

/** 单个轨迹采样点：时间戳 + 经纬度 + 精度 + 瞬时速度。 */
data class TrackPointJson(
    val t: Long,
    val lat: Double,
    val lng: Double,
    val acc: Float,
    val spd: Float? = null,
)
