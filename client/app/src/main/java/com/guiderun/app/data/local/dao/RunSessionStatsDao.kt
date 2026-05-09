package com.guiderun.app.data.local.dao

import androidx.room.*
import com.guiderun.app.data.local.entity.RunSessionStatsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RunSessionStatsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stats: RunSessionStatsEntity)

    @Query("SELECT * FROM run_session_stats WHERE requestId = :requestId AND userId = :userId")
    fun observe(requestId: String, userId: String): Flow<RunSessionStatsEntity?>

    @Query("SELECT * FROM run_session_stats WHERE requestId = :requestId AND userId = :userId")
    suspend fun get(requestId: String, userId: String): RunSessionStatsEntity?

    @Query("DELETE FROM run_session_stats WHERE requestId = :requestId")
    suspend fun deleteByRequest(requestId: String)
}
