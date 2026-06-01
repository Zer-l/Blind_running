package com.guiderun.server.entity

import io.hypersistence.utils.hibernate.type.json.JsonType
import jakarta.persistence.*
import org.hibernate.annotations.Type
import java.time.Instant
import java.util.UUID

/**
 * 评价实体（`reviews` 表）。reviewer/reviewee 区分双向评价，tags 用 JSON 列保存标签集合。
 * 每个 (requestId, reviewerId) 唯一，重复提交由 Service 层 [ALREADY_REVIEWED] 拦截。
 */
@Entity
@Table(name = "reviews")
class ReviewEntity(
    @Id
    @Column(length = 36)
    val id: String = UUID.randomUUID().toString(),

    @Column(nullable = false, length = 36)
    val requestId: String,

    @Column(nullable = false, length = 36)
    val reviewerId: String,

    @Column(nullable = false, length = 36)
    val revieweeId: String,

    @Column(nullable = false)
    val rating: Int,

    @Type(JsonType::class)
    @Column(columnDefinition = "JSON", nullable = false)
    val tags: List<String> = emptyList(),

    @Column(columnDefinition = "TEXT")
    val comment: String? = null,

    @Column(length = 500)
    val voiceUrl: String? = null,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
