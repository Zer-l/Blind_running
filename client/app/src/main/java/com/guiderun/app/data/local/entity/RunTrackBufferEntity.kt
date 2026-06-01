package com.guiderun.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * GPS 轨迹缓冲点实体。
 *
 * 前台 Service 采集到 GPS 点后立即写入此表，后台批量上传线程
 * 按 [uploadedAt] IS NULL 条件读取待传点（最多 100 条一批），
 * 上传成功后回写 uploadedAt 时间戳，再由 [com.guiderun.app.data.local.dao.RunTrackBufferDao.deleteUploaded] 清理。
 * 双索引（requestId / uploadedAt）加速 getPendingPoints 和 deleteUploaded 查询。
 */
@Entity(
    tableName = "run_track_buffer",
    indices = [Index("requestId"), Index("uploadedAt")],
)
data class RunTrackBufferEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val requestId: String,
    val userId: String,
    val role: String,           // "BLIND" or "VOLUNTEER"
    val timestamp: Long,        // millis
    val lat: Double,
    val lng: Double,
    val accuracy: Float,
    val speed: Float?,
    val uploadedAt: Long? = null,
)
