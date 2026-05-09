package com.guiderun.server.service

import com.guiderun.server.dto.run.ReviewResponseDto
import com.guiderun.server.entity.ReviewEntity
import com.guiderun.server.repository.ReviewJpaRepository
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

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
