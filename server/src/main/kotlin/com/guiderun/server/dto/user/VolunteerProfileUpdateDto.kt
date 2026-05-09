package com.guiderun.server.dto.user

data class VolunteerProfileUpdateDto(
    val averagePaceSeconds: Int? = null,
    val runningLevel: String? = null,
    val hasGuideExperience: Boolean? = null,
)
