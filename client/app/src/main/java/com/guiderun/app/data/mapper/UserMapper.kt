package com.guiderun.app.data.mapper

import com.guiderun.app.data.local.entity.UserEntity
import com.guiderun.app.data.remote.dto.UserDto
import com.guiderun.app.domain.model.BlindProfile
import com.guiderun.app.domain.model.Gender
import com.guiderun.app.domain.model.User
import com.guiderun.app.domain.model.UserRole
import com.guiderun.app.domain.model.VolunteerProfile

fun UserDto.toDomain() = User(
    id = id,
    phone = phone,
    nickname = nickname,
    avatarUrl = avatarUrl,
    gender = gender?.let { runCatching { Gender.valueOf(it) }.getOrNull() },
    roles = roles.mapNotNull { runCatching { UserRole.valueOf(it) }.getOrNull() }.toSet(),
    totalRuns = totalRuns,
    rating = rating,
    blindProfile = blindProfile?.let {
        BlindProfile(
            visionLevel = it.visionLevel,
            preferredPaceSeconds = it.preferredPaceSeconds,
            preferredDurationMinutes = it.preferredDurationMinutes,
            medicalNotes = it.medicalNotes,
            visualDescription = it.visualDescription,
        )
    },
    volunteerProfile = volunteerProfile?.let {
        VolunteerProfile(
            averagePaceSeconds = it.averagePaceSeconds,
            runningLevel = it.runningLevel,
            hasGuideExperience = it.hasGuideExperience,
        )
    },
)

fun User.toEntity() = UserEntity(
    id = id,
    phone = phone,
    nickname = nickname,
    avatarUrl = avatarUrl,
    gender = gender?.name,
    roles = roles.joinToString(",") { it.name },
    totalRuns = totalRuns,
    ratingSum = 0,
    ratingCount = 0,
)

fun UserEntity.toDomain() = User(
    id = id,
    phone = phone,
    nickname = nickname,
    avatarUrl = avatarUrl,
    gender = gender?.let { runCatching { Gender.valueOf(it) }.getOrNull() },
    roles = roles.split(",").filter { it.isNotBlank() }
        .mapNotNull { runCatching { UserRole.valueOf(it) }.getOrNull() }.toSet(),
    totalRuns = totalRuns,
    rating = if (ratingCount > 0) ratingSum.toFloat() / ratingCount else null,
)
