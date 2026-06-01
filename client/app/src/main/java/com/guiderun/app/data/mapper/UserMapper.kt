package com.guiderun.app.data.mapper

import com.guiderun.app.data.local.entity.UserEntity
import com.guiderun.app.data.remote.dto.UserDto
import com.guiderun.app.domain.model.BlindProfile
import com.guiderun.app.domain.model.Gender
import com.guiderun.app.domain.model.User
import com.guiderun.app.domain.model.UserRole
import com.guiderun.app.domain.model.VolunteerProfile

/**
 * UserDto（网络层）→ User（domain 层）映射。
 *
 * gender / roles 字段以字符串从服务端传输，
 * 用 runCatching valueOf 容错：旧客户端不会因新增枚举值而崩溃（降级为 null / 跳过）。
 */
fun UserDto.toDomain() = User(
    id = id,
    phone = phone,
    nickname = nickname,
    avatarUrl = avatarUrl,
    gender = gender?.let { runCatching { Gender.valueOf(it) }.getOrNull() },
    roles = roles.mapNotNull { runCatching { UserRole.valueOf(it) }.getOrNull() }.toSet(),
    totalRuns = totalRuns,
    rating = rating,
    blindProfile = blindProfile?.let {
        BlindProfile(
            visionLevel = it.visionLevel,
            preferredPaceSeconds = it.preferredPaceSeconds,
            preferredDurationMinutes = it.preferredDurationMinutes,
            medicalNotes = it.medicalNotes,
            visualDescription = it.visualDescription,
        )
    },
    volunteerProfile = volunteerProfile?.let {
        VolunteerProfile(
            averagePaceSeconds = it.averagePaceSeconds,
            runningLevel = it.runningLevel,
            hasGuideExperience = it.hasGuideExperience,
        )
    },
)

/**
 * User（domain）→ UserEntity（Room 缓存层）映射。
 *
 * ratingSum / ratingCount 在本地缓存中暂无聚合数据来源，写 0 占位；
 * 实际评分由 [UserDto.rating] 字段（服务端预聚合）驱动，不依赖本地两字段计算。
 */
fun User.toEntity() = UserEntity(
    id = id,
    phone = phone,
    nickname = nickname,
    avatarUrl = avatarUrl,
    gender = gender?.name,
    roles = roles.joinToString(",") { it.name },
    totalRuns = totalRuns,
    ratingSum = 0,
    ratingCount = 0,
)

/** UserEntity（Room 缓存）→ User（domain 层）映射，用于冷启动离线读取本地缓存。 */
fun UserEntity.toDomain() = User(
    id = id,
    phone = phone,
    nickname = nickname,
    avatarUrl = avatarUrl,
    gender = gender?.let { runCatching { Gender.valueOf(it) }.getOrNull() },
    roles = roles.split(",").filter { it.isNotBlank() }
        .mapNotNull { runCatching { UserRole.valueOf(it) }.getOrNull() }.toSet(),
    totalRuns = totalRuns,
    rating = if (ratingCount > 0) ratingSum.toFloat() / ratingCount else null,
)
