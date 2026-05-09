package com.guiderun.app.domain.model

data class RunRequest(
    val id: String,
    val status: RunRequestStatus,
    val version: Int,
    val blindRunner: UserSummary?,
    val volunteer: UserSummary?,
    val meetingLocation: GeoPoint,
    val expectedDurationMinutes: Int,
    val expectedDistanceMeters: Int?,
    val expectedPaceSeconds: Int?,
    val actualDistanceMeters: Int?,
    val actualDurationSeconds: Int?,
    val avgPaceSeconds: Int?,
    val isAbnormal: Boolean = false,
    val notes: String?,
    val abortReason: String?,
    val abortBy: AbortBy?,
    val createdAt: Long,
    val matchedAt: Long?,
    val departedAt: Long?,
    val metAt: Long?,
    val runStartedAt: Long?,
    val runEndedAt: Long?,
    val closedAt: Long?,
    val volunteerPosition: GeoPosition? = null,
)

data class GeoPosition(val lat: Double, val lng: Double, val updatedAt: Long)

data class AvailableRunRequest(
    val id: String,
    val blindRunner: BlindRunnerSummary,
    val meetingLocation: GeoPoint,
    val expectedDurationMinutes: Int,
    val distanceMeters: Int,
    val createdAt: Long,
)

data class GeoPoint(val lat: Double, val lng: Double, val description: String, val accuracy: Float = 0f)

data class UserSummary(
    val id: String,
    val nickname: String,
    val avatarUrl: String?,
    val gender: String?,
    val rating: Float?,
    val totalRuns: Int,
)

data class BlindRunnerSummary(
    val id: String,
    val nickname: String,
    val rating: Float?,
    val gender: String?,
    val visionLevel: String?,
    val totalRuns: Int,
)

enum class RunRequestStatus {
    // CREATED 为内部中间态，API 永远不会返回此值，仅供本地枚举完整性
    CREATED,
    MATCHING, ACCEPTED, EN_ROUTE, MET, RUNNING, FINISHED, CLOSED, ABORTED;

    fun isTerminal() = this == CLOSED || this == ABORTED
    fun isActive() = !isTerminal()
}

enum class AbortBy { BLIND, VOLUNTEER, SYSTEM, ADMIN }

// 事件触发方角色（与 UserRole 语义不同，含 SYSTEM/ADMIN，使用短名 BLIND）
enum class TriggeredRole { BLIND, VOLUNTEER, SYSTEM, ADMIN }

data class CreateRunRequestParams(
    val meetingLat: Double,
    val meetingLng: Double,
    val meetingDescription: String,
    val expectedDurationMinutes: Int = 60,
    val expectedDistanceMeters: Int? = null,
    val expectedPaceSeconds: Int? = null,
    val notes: String? = null,
)
