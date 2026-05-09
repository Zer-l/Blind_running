package com.guiderun.app.domain.model

data class TrackPoint(
    val t: Long,
    val lat: Double,
    val lng: Double,
    val acc: Float = 0f,
    val spd: Float? = null,
)

data class RunTrack(
    val requestId: String,
    val userId: String,
    val role: String,
    val points: List<TrackPoint>,
    val pointCount: Int,
    val totalDistanceMeters: Int,
    val totalDurationSeconds: Int,
    val avgPaceSeconds: Int?,
    val maxSpeed: Float?,
)
