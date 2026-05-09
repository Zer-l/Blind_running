package com.guiderun.app.data.local.entity

import androidx.room.Entity

@Entity(
    tableName = "run_session_stats",
    primaryKeys = ["requestId", "userId"],
)
data class RunSessionStatsEntity(
    val requestId: String,
    val userId: String,
    val totalDistanceMeters: Int = 0,
    val totalDurationSeconds: Int = 0,
    val currentPaceSeconds: Int? = null,
    val avgPaceSeconds: Int? = null,
    val maxSpeedMps: Float? = null,
    val lastUpdatedAt: Long = System.currentTimeMillis(),
)
