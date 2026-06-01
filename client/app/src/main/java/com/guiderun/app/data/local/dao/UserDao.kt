package com.guiderun.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.guiderun.app.data.local.entity.UserEntity

/**
 * 本地用户缓存 DAO。
 *
 * 仅缓存当前登录用户，避免每次冷启动都调 /users/me API。
 * 登录成功后 [upsert]，登出时 [deleteAll] 清除，保证数据不跨账号泄露。
 */
@Dao
interface UserDao {
    @Upsert
    suspend fun upsert(user: UserEntity)

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun findById(id: String): UserEntity?

    @Query("DELETE FROM users")
    suspend fun deleteAll()
}
