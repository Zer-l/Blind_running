package com.guiderun.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 本地用户缓存实体。
 *
 * roles 以逗号分隔字符串存储（Room 不支持 List 原生映射），
 * 由 [com.guiderun.app.data.mapper.UserMapper] 负责与 domain 层的 Set<UserRole> 互转。
 */
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val phone: String,
    val nickname: String,
    val avatarUrl: String?,
    val gender: String?,
    val roles: String, // comma-separated: "BLIND_RUNNER" or "BLIND_RUNNER,VOLUNTEER"
    val totalRuns: Int,
    val ratingSum: Int,
    val ratingCount: Int,
)
