package com.guiderun.app.domain.model

/**
 * 当前登录用户的完整信息（Domain 层）。
 *
 * 同一用户可同时持有 BLIND_RUNNER 和 VOLUNTEER 两个角色（roles 是集合）。
 * blindProfile / volunteerProfile 仅在对应角色存在时非 null。
 * rating 是该用户收到的所有评价均分，null 表示尚无评价记录。
 */
data class User(
    val id: String,
    val phone: String,
    val nickname: String,
    val avatarUrl: String?,
    val gender: Gender?,
    val roles: Set<UserRole>,
    val totalRuns: Int,
    val rating: Float?,
    val blindProfile: BlindProfile? = null,
    val volunteerProfile: VolunteerProfile? = null,
)

data class BlindProfile(
    val visionLevel: String? = null,
    val preferredPaceSeconds: Int? = null,
    val preferredDurationMinutes: Int? = null,
    val medicalNotes: String? = null,
    val visualDescription: String? = null,
)

data class VolunteerProfile(
    val averagePaceSeconds: Int? = null,
    val runningLevel: String? = null,
    val hasGuideExperience: Boolean = false,
)

enum class Gender { MALE, FEMALE, OTHER }

/**
 * 用户可持有的角色枚举。displayName 供 UI/TTS 使用中文展示，避免在各处硬编码中文字符串。
 */
enum class UserRole {
    BLIND_RUNNER,
    VOLUNTEER;

    fun displayName(): String = when (this) {
        BLIND_RUNNER -> "视障跑者"
        VOLUNTEER -> "志愿者"
    }
}

enum class ProvisioningStatus { PENDING_ROLE, ACTIVE }

/**
 * 登录接口返回值。isNewUser 用于埋点统计；provisioningStatus 决定登录后路由（新用户 → 角色选择页）。
 */
data class LoginResult(
    val user: User,
    val isNewUser: Boolean,
    val provisioningStatus: ProvisioningStatus,
)
