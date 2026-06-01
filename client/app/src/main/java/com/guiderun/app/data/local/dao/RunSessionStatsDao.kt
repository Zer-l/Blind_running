package com.guiderun.app.data.local.dao

import androidx.room.*
import com.guiderun.app.data.local.entity.RunSessionStatsEntity
import kotlinx.coroutines.flow.Flow

/**
 * 跑步会话实时统计 DAO。
 *
 * 数据由 [com.guiderun.app.service.BlindRunTrackingService] /
 * [com.guiderun.app.service.VolunteerRunTrackingService] 定期写入，
 * ViewModel 通过 [observe] 订阅 Flow 驱动 UI 刷新，
 * 实现 Service（计算端）与 Activity/Fragment（展示端）的完全解耦。
 * 跑步结束并上传轨迹后，调 [deleteByRequest] 清理本地数据。
 */
@Dao
interface RunSessionStatsDao {

    /** 插入或整行覆盖更新（主键 requestId+userId 唯一）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stats: RunSessionStatsEntity)

    /**
     * 订阅指定跑步会话的统计快照。
     * Flow 在 Service 每次写入后自动推送新值，是 Service→UI 单向数据流的核心通道。
     */
    @Query("SELECT * FROM run_session_stats WHERE requestId = :requestId AND userId = :userId")
    fun observe(requestId: String, userId: String): Flow<RunSessionStatsEntity?>

    /** 一次性读取，供跑步结束时提取最终统计值上传服务端。 */
    @Query("SELECT * FROM run_session_stats WHERE requestId = :requestId AND userId = :userId")
    suspend fun get(requestId: String, userId: String): RunSessionStatsEntity?

    /** 跑步结束后清理，避免历史数据干扰下次跑步的 UI 展示。 */
    @Query("DELETE FROM run_session_stats WHERE requestId = :requestId")
    suspend fun deleteByRequest(requestId: String)
}
