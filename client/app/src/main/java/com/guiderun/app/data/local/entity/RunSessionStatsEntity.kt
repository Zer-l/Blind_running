package com.guiderun.app.data.local.entity

import androidx.room.Entity

/**
 * 跑步会话统计快照实体。
 *
 * 联合主键（requestId + userId）支持双端（视障/志愿者）在同一次跑步中各存一行。
 * 由前台 Service 定期 upsert，ViewModel 通过 Flow 观察驱动 UI 实时刷新，
 * 不通过网络，避免每帧都产生 HTTP 请求。
 */
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
    /** 本机采集端是否处于自动暂停（仅本地设备语义，不参与协议）。 */
    val isPaused: Boolean = false,
    val lastUpdatedAt: Long = System.currentTimeMillis(),
)
