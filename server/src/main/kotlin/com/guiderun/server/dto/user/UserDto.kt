package com.guiderun.server.dto.user

/** 用户完整信息：基础字段 + 双端资料子结构 + 跑步统计快照。 */
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

/** 视障端资料展示用 DTO。 */
data class BlindProfileDto(
    val visionLevel: String? = null,
    val preferredPaceSeconds: Int? = null,
    val preferredDurationMinutes: Int? = null,
    val medicalNotes: String? = null,
    val visualDescription: String? = null,
)

/** 志愿者端资料展示用 DTO。 */
data class VolunteerProfileDto(
    val averagePaceSeconds: Int? = null,
    val runningLevel: String? = null,
    val hasGuideExperience: Boolean = false,
)
