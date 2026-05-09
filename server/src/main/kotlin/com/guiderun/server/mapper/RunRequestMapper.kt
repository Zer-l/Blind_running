package com.guiderun.server.mapper

import com.guiderun.server.dto.run.*
import com.guiderun.server.entity.RunRequestEntity
import com.guiderun.server.entity.UserEntity

fun RunRequestEntity.toResponse(blindRunner: UserEntity?, volunteer: UserEntity?) = RunRequestResponse(
    id = id,
    status = status.name,
    version = version,
    blindRunner = blindRunner?.toSummary(),
    volunteer = volunteer?.toSummary(),
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
)

fun UserEntity.toSummary() = UserSummaryDto(
    id = id,
    nickname = nickname,
    avatarUrl = avatarUrl,
    gender = gender?.name,
    rating = if (ratingCount > 0) ratingSum.toFloat() / ratingCount else null,
    totalRuns = totalRuns,
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
