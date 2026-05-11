package com.guiderun.app.data.mapper

import com.guiderun.app.data.remote.dto.*
import com.guiderun.app.domain.model.*

fun RunRequestResponseDto.toDomain() = RunRequest(
    id = id,
    status = runCatching { RunRequestStatus.valueOf(status) }.getOrDefault(RunRequestStatus.MATCHING),
    version = version,
    blindRunner = blindRunner?.toDomain(),
    volunteer = volunteer?.toDomain(),
    meetingLocation = meetingLocation.toDomain(),
    expectedDurationMinutes = expectedDurationMinutes,
    expectedDistanceMeters = expectedDistanceMeters,
    expectedPaceSeconds = expectedPaceSeconds,
    actualDistanceMeters = actualDistanceMeters,
    actualDurationSeconds = actualDurationSeconds,
    avgPaceSeconds = avgPaceSeconds,
    isAbnormal = isAbnormal,
    notes = notes,
    abortReason = abortReason,
    abortBy = abortBy?.let { runCatching { AbortBy.valueOf(it) }.getOrNull() },
    createdAt = createdAt,
    matchedAt = matchedAt,
    departedAt = departedAt,
    metAt = metAt,
    runStartedAt = runStartedAt,
    runEndedAt = runEndedAt,
    closedAt = closedAt,
    volunteerPosition = volunteerPosition?.let { GeoPosition(it.lat, it.lng, it.updatedAt) },
    myReviewSubmitted = myReviewSubmitted,
)

fun UserSummaryDto.toDomain() = UserSummary(
    id = id,
    nickname = nickname,
    avatarUrl = avatarUrl,
    gender = gender,
    rating = rating,
    totalRuns = totalRuns,
    phone = phone,
)

fun GeoPointDto.toDomain() = GeoPoint(lat = lat, lng = lng, description = description)

fun AvailableRequestItemDto.toDomain() = AvailableRunRequest(
    id = id,
    blindRunner = blindRunner.toDomain(),
    meetingLocation = meetingLocation.toDomain(),
    expectedDurationMinutes = expectedDurationMinutes,
    distanceMeters = distanceMeters,
    createdAt = createdAt,
)

fun BlindRunnerSummaryDto.toDomain() = BlindRunnerSummary(
    id = id,
    nickname = nickname,
    rating = rating,
    gender = gender,
    visionLevel = visionLevel,
    totalRuns = totalRuns,
)

fun ReviewResponseDto.toDomain() = Review(
    id = id,
    requestId = requestId,
    reviewerId = reviewerId,
    revieweeId = revieweeId,
    rating = rating,
    tags = tags,
    comment = comment,
    createdAt = createdAt,
)

fun RunTrackResponseDto.toDomain() = RunTrack(
    requestId = requestId,
    userId = userId,
    role = role,
    points = points.map { TrackPoint(it.t, it.lat, it.lng, it.acc, it.spd) },
    pointCount = pointCount,
    totalDistanceMeters = totalDistanceMeters,
    totalDurationSeconds = totalDurationSeconds,
    avgPaceSeconds = avgPaceSeconds,
    maxSpeed = maxSpeed,
)

fun VolunteerStatsDto.toDomain() = VolunteerStats(
    totalRuns = totalRuns,
    totalHoursMinutes = totalHoursMinutes,
    totalDistanceMeters = totalDistanceMeters,
    currentMonthRuns = currentMonthRuns,
    currentYearRuns = currentYearRuns,
    rating = rating,
    badges = badges.map { Badge(it.id, it.name, it.unlockedAt) },
)
