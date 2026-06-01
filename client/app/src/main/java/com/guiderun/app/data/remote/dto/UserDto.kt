package com.guiderun.app.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * 用户完整信息响应 DTO（GET /users/me 返回）。
 *
 * [roles] 以字符串列表传输，mapper 层用 runCatching valueOf 容错旧客户端。
 * [blindProfile] / [volunteerProfile] 按角色选填，角色不符时为 null。
 */
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

/**
 * 更新用户资料请求体，所有字段可选（PATCH 语义）。
 * 仅传入需要修改的字段，服务端忽略 null 字段。
 */
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
