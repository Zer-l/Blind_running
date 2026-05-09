package com.guiderun.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "run_track_buffer",
    indices = [Index("requestId"), Index("uploadedAt")],
)
data class RunTrackBufferEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val requestId: String,
    val userId: String,
    val role: String,           // "BLIND" or "VOLUNTEER"
    val timestamp: Long,        // millis
    val lat: Double,
    val lng: Double,
    val accuracy: Float,
    val speed: Float?,
    val uploadedAt: Long? = null,
)
