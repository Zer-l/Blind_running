package com.guiderun.app.domain.model

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

enum class UserRole {
    BLIND_RUNNER,
    VOLUNTEER;

    fun displayName(): String = when (this) {
        BLIND_RUNNER -> "视障跑者"
        VOLUNTEER -> "志愿者"
    }
}

enum class ProvisioningStatus { PENDING_ROLE, ACTIVE }

data class LoginResult(
    val user: User,
    val isNewUser: Boolean,
    val provisioningStatus: ProvisioningStatus,
)
