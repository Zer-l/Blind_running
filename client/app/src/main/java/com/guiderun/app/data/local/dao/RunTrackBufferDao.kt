package com.guiderun.app.data.local.dao

import androidx.room.*
import com.guiderun.app.data.local.entity.RunTrackBufferEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RunTrackBufferDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(point: RunTrackBufferEntity): Long

    @Query("SELECT * FROM run_track_buffer WHERE requestId = :requestId ORDER BY timestamp ASC")
    fun observeByRequest(requestId: String): Flow<List<RunTrackBufferEntity>>

    @Query("SELECT * FROM run_track_buffer WHERE requestId = :requestId AND uploadedAt IS NULL ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getPendingPoints(requestId: String, limit: Int = 100): List<RunTrackBufferEntity>

    @Query("SELECT COUNT(*) FROM run_track_buffer WHERE requestId = :requestId AND uploadedAt IS NULL")
    suspend fun countPending(requestId: String): Int

    @Query("UPDATE run_track_buffer SET uploadedAt = :now WHERE id IN (:ids)")
    suspend fun markUploaded(ids: List<Long>, now: Long = System.currentTimeMillis())

    @Query("SELECT MAX(timestamp) FROM run_track_buffer WHERE requestId = :requestId")
    suspend fun getLastTimestamp(requestId: String): Long?

    @Query("DELETE FROM run_track_buffer WHERE requestId = :requestId AND uploadedAt IS NOT NULL")
    suspend fun deleteUploaded(requestId: String)
}
