package com.guiderun.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.guiderun.app.data.local.entity.UserEntity

@Dao
interface UserDao {
    @Upsert
    suspend fun upsert(user: UserEntity)

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun findById(id: String): UserEntity?

    @Query("DELETE FROM users")
    suspend fun deleteAll()
}
