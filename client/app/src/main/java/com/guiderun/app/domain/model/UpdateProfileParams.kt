package com.guiderun.app.domain.model

data class UpdateProfileParams(
    val nickname: String? = null,
    val gender: Gender? = null,
    val blindProfile: BlindProfileUpdate? = null,
    val volunteerProfile: VolunteerProfileUpdate? = null,
)

data class BlindProfileUpdate(
    val visionLevel: String? = null,
    val preferredPaceSeconds: Int? = null,
    val preferredDurationMinutes: Int? = null,
    val medicalNotes: String? = null,
    val visualDescription: String? = null,
)

data class VolunteerProfileUpdate(
    val averagePaceSeconds: Int? = null,
    val runningLevel: String? = null,
    val hasGuideExperience: Boolean? = null,
)
