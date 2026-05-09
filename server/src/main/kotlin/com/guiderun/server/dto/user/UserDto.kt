package com.guiderun.server.dto.user

data class UserDto(
    val id: String,
    val phone: String,
    val nickname: String,
    val avatarUrl: String?,
    val gender: String?,
    val roles: List<String>,
    val provisioningStatus: String,
    val totalRuns: Int,
    val rating: Float?,
    val blindProfile: BlindProfileDto? = null,
    val volunteerProfile: VolunteerProfileDto? = null,
)

data class BlindProfileDto(
    val visionLevel: String? = null,
    val preferredPaceSeconds: Int? = null,
    val preferredDurationMinutes: Int? = null,
    val medicalNotes: String? = null,
    val visualDescription: String? = null,
)

data class VolunteerProfileDto(
    val averagePaceSeconds: Int? = null,
    val runningLevel: String? = null,
    val hasGuideExperience: Boolean = false,
)
