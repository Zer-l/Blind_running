package com.guiderun.server.mapper

import com.guiderun.server.common.RunRequestStatus
import com.guiderun.server.dto.run.*
import com.guiderun.server.entity.RunRequestEntity
import com.guiderun.server.entity.UserEntity

fun RunRequestEntity.toResponse(
    blindRunner: UserEntity?,
    volunteer: UserEntity?,
    myReviewSubmitted: Boolean? = null,
): RunRequestResponse {
    // 仅在双方已匹配（ACCEPTED 及之后的非终止状态）时下发对方手机号，
    // 用于跑前/跑中拨打电话；MATCHING/CLOSED/ABORTED 不下发，避免泄露隐私。
    val includePhone = status in PEER_PHONE_VISIBLE_STATUSES
    return RunRequestResponse(
        id = id,
        status = status.name,
        version = version,
        blindRunner = blindRunner?.toSummary(includePhone = includePhone),
        volunteer = volunteer?.toSummary(includePhone = includePhone),
        meetingLocation = GeoPointDto(meetingLat, meetingLng, meetingLocationDesc),
        expectedDurationMinutes = expectedDurationMinutes,
        expectedDistanceMeters = expectedDistanceMeters,
        expectedPaceSeconds = expectedPaceSeconds,
        actualDistanceMeters = actualDistanceMeters,
        actualDurationSeconds = actualDurationSeconds,
        avgPaceSeconds = avgPaceSeconds,
        isAbnormal = isAbnormal,
        notes = notes,
        abortReason = abortReason,
        abortBy = abortBy?.name,
        createdAt = createdAt.toEpochMilli(),
        matchedAt = matchedAt?.toEpochMilli(),
        departedAt = departedAt?.toEpochMilli(),
        metAt = metAt?.toEpochMilli(),
        runStartedAt = runStartedAt?.toEpochMilli(),
        runEndedAt = runEndedAt?.toEpochMilli(),
        closedAt = closedAt?.toEpochMilli(),
        volunteerPosition = if (volunteerLat != null && volunteerLng != null)
            GeoPositionDto(volunteerLat!!, volunteerLng!!, volunteerPositionUpdatedAt?.toEpochMilli() ?: 0L)
        else null,
        myReviewSubmitted = myReviewSubmitted,
    )
}

private val PEER_PHONE_VISIBLE_STATUSES = setOf(
    RunRequestStatus.ACCEPTED,
    RunRequestStatus.EN_ROUTE,
    RunRequestStatus.MET,
    RunRequestStatus.RUNNING,
    RunRequestStatus.FINISHED,
)

fun UserEntity.toSummary(includePhone: Boolean = false) = UserSummaryDto(
    id = id,
    nickname = nickname,
    avatarUrl = avatarUrl,
    gender = gender?.name,
    rating = if (ratingCount > 0) ratingSum.toFloat() / ratingCount else null,
    totalRuns = totalRuns,
    phone = if (includePhone) phone else null,
)

fun RunRequestEntity.toAvailableItem(user: UserEntity, distanceMeters: Int) = AvailableRequestItemDto(
    id = id,
    blindRunner = BlindRunnerSummaryDto(
        id = user.id,
        nickname = user.nickname,
        rating = if (user.ratingCount > 0) user.ratingSum.toFloat() / user.ratingCount else null,
        gender = user.gender?.name,
        visionLevel = user.blindProfile?.visionLevel,
        totalRuns = user.totalRuns,
    ),
    meetingLocation = GeoPointDto(meetingLat, meetingLng, meetingLocationDesc),
    expectedDurationMinutes = expectedDurationMinutes,
    distanceMeters = distanceMeters,
    createdAt = createdAt.toEpochMilli(),
)
