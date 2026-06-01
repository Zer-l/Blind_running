package com.guiderun.server.service

import com.guiderun.server.dto.run.ReviewResponseDto
import com.guiderun.server.entity.ReviewEntity
import com.guiderun.server.repository.ReviewJpaRepository
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 评价查询业务：分页拉取某用户收到的所有评价（按时间倒序）。
 * 评价的写入由 [RunRequestService.createReview] 处理（涉及订单状态/双方评价完成判定）。
 */
@Service
@Transactional(readOnly = true)
class ReviewService(private val reviewRepo: ReviewJpaRepository) {

    fun getReviewsForUser(userId: String, pageable: Pageable): List<ReviewResponseDto> =
        reviewRepo.findByRevieweeIdOrderByCreatedAtDesc(userId, pageable).map { it.toDto() }

    private fun ReviewEntity.toDto() = ReviewResponseDto(
        id = id,
        requestId = requestId,
        reviewerId = reviewerId,
        revieweeId = revieweeId,
        rating = rating,
        tags = tags,
        comment = comment,
        voiceUrl = voiceUrl,
        createdAt = createdAt.toEpochMilli(),
    )
}
