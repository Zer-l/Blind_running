package com.guiderun.app.domain.model

data class Review(
    val id: String,
    val requestId: String,
    val reviewerId: String,
    val revieweeId: String,
    val rating: Int,
    val tags: List<String>,
    val comment: String?,
    val voiceUrl: String?,
    val createdAt: Long,
)

data class CreateReviewParams(
    val rating: Int,
    val tags: List<String> = emptyList(),
    val comment: String? = null,
    val voiceUrl: String? = null,
)
