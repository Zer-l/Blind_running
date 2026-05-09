package com.guiderun.server.dto.run

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class TrackPointDto(
    @field:NotNull val t: Long,
    @field:NotNull val lat: Double,
    @field:NotNull val lng: Double,
    val acc: Float = 0f,
    val spd: Float? = null,
)

data class UploadTracksDto(
    @field:NotNull val role: String,
    @field:NotEmpty @field:Size(max = 1000) val points: List<TrackPointDto>,
)

data class RunTrackResponseDto(
    val requestId: String,
    val userId: String,
    val role: String,
    val points: List<TrackPointDto>,
    val pointCount: Int,
    val totalDistanceMeters: Int,
    val totalDurationSeconds: Int,
    val avgPaceSeconds: Int?,
    val maxSpeed: Float?,
)
