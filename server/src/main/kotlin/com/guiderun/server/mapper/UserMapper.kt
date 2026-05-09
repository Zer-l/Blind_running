package com.guiderun.server.mapper

import com.guiderun.server.dto.user.BlindProfileDto
import com.guiderun.server.dto.user.UserDto
import com.guiderun.server.dto.user.VolunteerProfileDto
import com.guiderun.server.entity.UserEntity

fun UserEntity.toDto() = UserDto(
    id = id,
    phone = phone,
    nickname = nickname,
    avatarUrl = avatarUrl,
    gender = gender?.name,
    roles = roles,
    provisioningStatus = provisioningStatus.name,
    totalRuns = totalRuns,
    rating = if (ratingCount > 0) ratingSum.toFloat() / ratingCount else null,
    blindProfile = blindProfile?.let {
        BlindProfileDto(
            visionLevel = it.visionLevel,
            preferredPaceSeconds = it.preferredPaceSeconds,
            preferredDurationMinutes = it.preferredDurationMinutes,
            medicalNotes = it.medicalNotes,
            visualDescription = it.visualDescription,
        )
    },
    volunteerProfile = volunteerProfile?.let {
        VolunteerProfileDto(
            averagePaceSeconds = it.averagePaceSeconds,
            runningLevel = it.runningLevel,
            hasGuideExperience = it.hasGuideExperience,
        )
    },
)
