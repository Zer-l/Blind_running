package com.guiderun.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.guiderun.app.data.local.dao.RunSessionStatsDao
import com.guiderun.app.data.local.dao.RunTrackBufferDao
import com.guiderun.app.data.local.dao.UserDao
import com.guiderun.app.data.local.entity.RunSessionStatsEntity
import com.guiderun.app.data.local.entity.RunTrackBufferEntity
import com.guiderun.app.data.local.entity.UserEntity

/**
 * Room 数据库（v3）。
 *
 * 表职责：
 * - [UserEntity]：本地缓存当前登录用户，避免每次启动都调 /users/me API
 * - [RunTrackBufferEntity]：跑步中 GPS 轨迹点缓冲区，由 RunTrackingService 实时写入，
 *   跑步结束后批量上传到服务端，上传成功即删除
 * - [RunSessionStatsEntity]：跑步统计（距离/时长/配速/暂停状态），由 Service 写入，
 *   ViewModel 订阅 observe Flow 驱动 UI，实现 Service 和 UI 的解耦
 *
 * 版本历史：exportSchema = false（Demo 阶段），生产请改为 true 并配置 schemaDirectory。
 */
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
