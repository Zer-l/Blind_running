package com.guiderun.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

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
