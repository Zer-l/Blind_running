package com.guiderun.server.dto.run

import jakarta.validation.constraints.NotNull

/** 跑步实时指标推送：用于双端互显距离/时长/配速，经 WebSocket 转发给对方。 */
data class PeerMetricsDto(
    @field:NotNull val totalDistanceMeters: Int,
    @field:NotNull val totalDurationSeconds: Int,
    val currentPaceSeconds: Int? = null,
    val avgPaceSeconds: Int? = null,
)
