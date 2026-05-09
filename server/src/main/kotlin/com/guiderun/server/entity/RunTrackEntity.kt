package com.guiderun.server.entity

import io.hypersistence.utils.hibernate.type.json.JsonType
import jakarta.persistence.*
import org.hibernate.annotations.Type
import java.time.Instant
import java.util.UUID

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

enum class TrackRole { BLIND, VOLUNTEER }

data class TrackPointJson(
    val t: Long,
    val lat: Double,
    val lng: Double,
    val acc: Float,
    val spd: Float? = null,
)
