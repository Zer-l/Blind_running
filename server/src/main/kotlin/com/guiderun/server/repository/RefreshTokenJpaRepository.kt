package com.guiderun.server.repository

import com.guiderun.server.entity.RefreshTokenEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface RefreshTokenJpaRepository : JpaRepository<RefreshTokenEntity, String> {
    fun findByTokenHash(tokenHash: String): RefreshTokenEntity?

    @Modifying
    @Query("UPDATE RefreshTokenEntity r SET r.revoked = true WHERE r.userId = :userId")
    fun revokeAllByUserId(userId: String)
}
