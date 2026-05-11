package com.guiderun.server.repository

import com.guiderun.server.entity.ReviewEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ReviewJpaRepository : JpaRepository<ReviewEntity, String> {

    fun findByRequestId(requestId: String): List<ReviewEntity>

    fun existsByRequestIdAndReviewerId(requestId: String, reviewerId: String): Boolean

    fun countByRequestId(requestId: String): Long

    fun findByRevieweeIdOrderByCreatedAtDesc(revieweeId: String, pageable: org.springframework.data.domain.Pageable): List<ReviewEntity>

    /** 给一批 requestId，返回其中"该用户已经评价过的" requestId 集合。用于历史列表批量标记。 */
    @Query("SELECT r.requestId FROM ReviewEntity r WHERE r.requestId IN :requestIds AND r.reviewerId = :reviewerId")
    fun findReviewedRequestIds(
        @Param("requestIds") requestIds: Collection<String>,
        @Param("reviewerId") reviewerId: String,
    ): List<String>
}
