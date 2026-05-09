package com.guiderun.server.repository

import com.guiderun.server.entity.ReviewEntity
import org.springframework.data.jpa.repository.JpaRepository

interface ReviewJpaRepository : JpaRepository<ReviewEntity, String> {

    fun findByRequestId(requestId: String): List<ReviewEntity>

    fun existsByRequestIdAndReviewerId(requestId: String, reviewerId: String): Boolean

    fun countByRequestId(requestId: String): Long

    fun findByRevieweeIdOrderByCreatedAtDesc(revieweeId: String, pageable: org.springframework.data.domain.Pageable): List<ReviewEntity>
}
