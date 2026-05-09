package com.guiderun.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    val id: String,
    val phone: String,
    val nickname: String,
    val avatarUrl: String? = null,
    val gender: String? = null,
    val roles: List<String>,
    val provisioningStatus: String = "ACTIVE",
    val totalRuns: Int,
    val rating: Float? = null,
    val blindProfile: BlindProfileDto? = null,
    val volunteerProfile: VolunteerProfileDto? = null,
)

@Serializable
data class BlindProfileDto(
    val visionLevel: String? = null,
    val preferredPaceSeconds: Int? = null,
    val preferredDurationMinutes: Int? = null,
    val medicalNotes: String? = null,
    val visualDescription: String? = null,
)

@Serializable
data class VolunteerProfileDto(
    val averagePaceSeconds: Int? = null,
    val runningLevel: String? = null,
    val hasGuideExperience: Boolean = false,
)

@Serializable
data class UpdateUserRequestDto(
    val nickname: String? = null,
    val gender: String? = null,
    val roles: List<String>? = null,
    val blindProfile: BlindProfileUpdateDto? = null,
    val volunteerProfile: VolunteerProfileUpdateDto? = null,
)

@Serializable
data class BlindProfileUpdateDto(
    val visionLevel: String? = null,
    val preferredPaceSeconds: Int? = null,
    val preferredDurationMinutes: Int? = null,
    val medicalNotes: String? = null,
    val visualDescription: String? = null,
)

@Serializable
data class VolunteerProfileUpdateDto(
    val averagePaceSeconds: Int? = null,
    val runningLevel: String? = null,
    val hasGuideExperience: Boolean? = null,
)
