package com.guiderun.server.dto.run

data class ReviewResponseDto(
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
