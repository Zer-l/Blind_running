package com.guiderun.server.dto.run

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

// ── 请求体 ──────────────────────────────────────────────────────────────

data class ReportPositionDto(
    @field:NotNull val lat: Double,
    @field:NotNull val lng: Double,
)

data class GeoPointDto(
    val lat: Double,
    val lng: Double,
    @field:NotBlank val description: String,
)

data class CreateRunRequestDto(
    @field:NotNull val meetingLocation: GeoPointDto,
    @field:Min(5) @field:Max(480) val expectedDurationMinutes: Int = 60,
    val expectedDistanceMeters: Int? = null,
    val expectedPaceSeconds: Int? = null,
    val notes: String? = null,
)

data class AbandonDto(
    val reason: String? = null,
)

data class EndRunDto(
    @field:Min(0) @field:Max(100_000) val actualDistanceMeters: Int? = null,
    @field:Min(0) @field:Max(86_400) val actualDurationSeconds: Int? = null,
    @field:Min(0) @field:Max(3600) val avgPaceSeconds: Int? = null,
)

data class CancelDto(
    val reason: String? = null,
)

data class CreateReviewDto(
    @field:Min(1) @field:Max(5) val rating: Int,
    val tags: List<String> = emptyList(),
    val comment: String? = null,
    val voiceUrl: String? = null,
)

// ── 响应体 ──────────────────────────────────────────────────────────────

data class UserSummaryDto(
    val id: String,
    val nickname: String,
    val avatarUrl: String?,
    val gender: String?,
    val rating: Float?,
    val totalRuns: Int,
    /** 仅在 ACCEPTED 之后下发给同订单参与方，用于跑前/跑中拨打电话；其他场景为 null。 */
    val phone: String?,
)

data class BlindRunnerSummaryDto(
    val id: String,
    val nickname: String,
    val rating: Float?,
    val gender: String?,
    val visionLevel: String?,
    val totalRuns: Int,
)

data class GeoPositionDto(val lat: Double, val lng: Double, val updatedAt: Long)

data class RunRequestResponse(
    val id: String,
    val status: String,
    val version: Int,
    val blindRunner: UserSummaryDto?,
    val volunteer: UserSummaryDto?,
    val meetingLocation: GeoPointDto,
    val expectedDurationMinutes: Int,
    val expectedDistanceMeters: Int?,
    val expectedPaceSeconds: Int?,
    val actualDistanceMeters: Int?,
    val actualDurationSeconds: Int?,
    val avgPaceSeconds: Int?,
    val isAbnormal: Boolean,
    val notes: String?,
    val abortReason: String?,
    val abortBy: String?,
    val createdAt: Long,
    val matchedAt: Long?,
    val departedAt: Long?,
    val metAt: Long?,
    val runStartedAt: Long?,
    val runEndedAt: Long?,
    val closedAt: Long?,
    val volunteerPosition: GeoPositionDto? = null,
)

data class AvailableRequestItemDto(
    val id: String,
    val blindRunner: BlindRunnerSummaryDto,
    val meetingLocation: GeoPointDto,
    val expectedDurationMinutes: Int,
    val distanceMeters: Int,
    val createdAt: Long,
)
