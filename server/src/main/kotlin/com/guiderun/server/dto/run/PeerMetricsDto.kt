package com.guiderun.server.dto.run

import jakarta.validation.constraints.NotNull

data class PeerMetricsDto(
    @field:NotNull val totalDistanceMeters: Int,
    @field:NotNull val totalDurationSeconds: Int,
    val currentPaceSeconds: Int? = null,
    val avgPaceSeconds: Int? = null,
)
