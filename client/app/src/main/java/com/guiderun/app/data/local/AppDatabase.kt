package com.guiderun.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.guiderun.app.data.local.dao.RunSessionStatsDao
import com.guiderun.app.data.local.dao.RunTrackBufferDao
import com.guiderun.app.data.local.dao.UserDao
import com.guiderun.app.data.local.entity.RunSessionStatsEntity
import com.guiderun.app.data.local.entity.RunTrackBufferEntity
import com.guiderun.app.data.local.entity.UserEntity

@Database(
    entities = [
        UserEntity::class,
        RunTrackBufferEntity::class,
        RunSessionStatsEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun runTrackBufferDao(): RunTrackBufferDao
    abstract fun runSessionStatsDao(): RunSessionStatsDao
}
