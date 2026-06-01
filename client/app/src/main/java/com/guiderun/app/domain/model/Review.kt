package com.guiderun.app.domain.model

/**
 * 跑步订单评价记录（已提交的评价，用于历史页展示）。
 *
 * reviewer 评价 reviewee（双向评价：视障跑者评志愿者，志愿者评视障跑者）。
 * rating 1-5 星；tags 为预设标签列表（如"耐心""专业"）；comment 可选。
 */
data class Review(
    val id: String,
    val requestId: String,
    val reviewerId: String,
    val revieweeId: String,
    val rating: Int,
    val tags: List<String>,
    val comment: String?,
    val createdAt: Long,
)

/** 提交评价时的入参，由 UI 层构造后传入 Repository。 */
data class CreateReviewParams(
    val rating: Int,
    val tags: List<String> = emptyList(),
    val comment: String? = null,
)
