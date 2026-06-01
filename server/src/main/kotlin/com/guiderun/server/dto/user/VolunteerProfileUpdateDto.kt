package com.guiderun.server.dto.user

/** 志愿者端资料 PATCH 子结构，所有字段可选。 */
data class VolunteerProfileUpdateDto(
    val averagePaceSeconds: Int? = null,
    val runningLevel: String? = null,
    val hasGuideExperience: Boolean? = null,
)
