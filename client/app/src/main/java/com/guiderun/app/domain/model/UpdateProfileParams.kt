package com.guiderun.app.domain.model

/**
 * 更新用户资料的入参（Domain 层）。
 *
 * 所有字段均可 null（仅更新非 null 字段），服务端做 PATCH 语义处理。
 * blindProfile / volunteerProfile 是各角色专属扩展信息，由对应页面单独更新。
 */
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
