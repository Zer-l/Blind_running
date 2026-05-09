package com.guiderun.server.repository

import com.guiderun.server.entity.RunTrackEntity
import com.guiderun.server.entity.TrackRole
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface RunTrackJpaRepository : JpaRepository<RunTrackEntity, String> {
    fun findByRequestId(requestId: String): List<RunTrackEntity>
    fun findByRequestIdAndUserId(requestId: String, userId: String): RunTrackEntity?
    fun findByRequestIdAndRole(requestId: String, role: TrackRole): RunTrackEntity?

    @Query("SELECT COALESCE(SUM(t.totalDistanceMeters), 0) FROM RunTrackEntity t WHERE t.userId = :userId")
    fun sumTotalDistanceMetersByUserId(userId: String): Long
}
