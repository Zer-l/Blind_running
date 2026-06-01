package com.guiderun.server.dto.run

/** 评价响应 DTO：reviewer 给 reviewee 的评分 + 标签 + 文字/语音留言。 */
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
