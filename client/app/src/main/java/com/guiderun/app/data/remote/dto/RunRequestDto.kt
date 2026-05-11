package com.guiderun.app.data.remote.dto

import kotlinx.serialization.Serializable

// ── 请求体 ────────────────────────────────────────────────────────────────

@Serializable
data class GeoPointRequestDto(
    val lat: Double,
    val lng: Double,
    val description: String,
)

@Serializable
data class CreateRunRequestRequestDto(
    val meetingLocation: GeoPointRequestDto,
    val expectedDurationMinutes: Int,
    val expectedDistanceMeters: Int? = null,
    val expectedPaceSeconds: Int? = null,
    val notes: String? = null,
)

@Serializable
data class AcceptRunRequestRequestDto(val version: Int)

@Serializable
data class EndRunRequestDto(
    val actualDistanceMeters: Int? = null,
    val actualDurationSeconds: Int? = null,
    val avgPaceSeconds: Int? = null,
)

@Serializable
data class CancelRequestDto(val reason: String? = null)

@Serializable
data class ReportPositionRequestDto(val lat: Double, val lng: Double)

@Serializable
data class CreateReviewRequestDto(
    val rating: Int,
    val tags: List<String> = emptyList(),
    val comment: String? = null,
)

@Serializable
data class EmergencyGeoRequestDto(val lat: Double, val lng: Double)

@Serializable
data class EmergencyRequestDto(
    val reason: String? = null,
    val currentLocation: EmergencyGeoRequestDto? = null,
    val timestamp: Long? = null,
)

@Serializable
data class TrackPointDto(
    val t: Long,
    val lat: Double,
    val lng: Double,
    val acc: Float = 0f,
    val spd: Float? = null,
)

@Serializable
data class UploadTracksDto(
    val role: String,
    val points: List<TrackPointDto>,
)

@Serializable
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

@Serializable
data class PeerMetricsDto(
    val totalDistanceMeters: Int,
    val totalDurationSeconds: Int,
    val currentPaceSeconds: Int? = null,
    val avgPaceSeconds: Int? = null,
)

@Serializable
data class ReviewResponseDto(
    val id: String,
    val requestId: String,
    val reviewerId: String,
    val revieweeId: String,
    val rating: Int,
    val tags: List<String>,
    val comment: String? = null,
    val createdAt: Long,
)

@Serializable
data class BadgeDto(val id: String, val name: String, val unlockedAt: Int)

@Serializable
data class VolunteerStatsDto(
    val totalRuns: Int,
    val totalHoursMinutes: Int,
    val totalDistanceMeters: Long,
    val currentMonthRuns: Int,
    val currentYearRuns: Int,
    val rating: Float?,
    val badges: List<BadgeDto>,
)

// ── 响应体 ────────────────────────────────────────────────────────────────

@Serializable
data class GeoPositionDto(val lat: Double, val lng: Double, val updatedAt: Long)

@Serializable
data class GeoPointDto(
    val lat: Double,
    val lng: Double,
    val description: String,
)

@Serializable
data class UserSummaryDto(
    val id: String,
    val nickname: String,
    val avatarUrl: String? = null,
    val gender: String? = null,
    val rating: Float? = null,
    val totalRuns: Int = 0,
    /** 仅在 ACCEPTED 之后由服务端下发；其他场景为 null。供视障端音量+键拨号 / 志愿者端电话按钮使用。 */
    val phone: String? = null,
)

@Serializable
data class BlindRunnerSummaryDto(
    val id: String,
    val nickname: String,
    val rating: Float? = null,
    val gender: String? = null,
    val visionLevel: String? = null,
    val totalRuns: Int = 0,
)

@Serializable
data class RunRequestResponseDto(
    val id: String,
    val status: String,
    val version: Int,
    val blindRunner: UserSummaryDto? = null,
    val volunteer: UserSummaryDto? = null,
    val meetingLocation: GeoPointDto,
    val expectedDurationMinutes: Int,
    val expectedDistanceMeters: Int? = null,
    val expectedPaceSeconds: Int? = null,
    val actualDistanceMeters: Int? = null,
    val actualDurationSeconds: Int? = null,
    val avgPaceSeconds: Int? = null,
    val isAbnormal: Boolean = false,
    val notes: String? = null,
    val abortReason: String? = null,
    val abortBy: String? = null,
    val createdAt: Long,
    val matchedAt: Long? = null,
    val departedAt: Long? = null,
    val metAt: Long? = null,
    val runStartedAt: Long? = null,
    val runEndedAt: Long? = null,
    val closedAt: Long? = null,
    val volunteerPosition: GeoPositionDto? = null,
    val myReviewSubmitted: Boolean? = null,
)

@Serializable
data class ListResponseDto<T>(
    val items: List<T>,
    val total: Long,
)

@Serializable
data class AvailableRequestItemDto(
    val id: String,
    val blindRunner: BlindRunnerSummaryDto,
    val meetingLocation: GeoPointDto,
    val expectedDurationMinutes: Int,
    val distanceMeters: Int,
    val createdAt: Long,
)
