package com.guiderun.server.entity

import io.hypersistence.utils.hibernate.type.json.JsonType
import jakarta.persistence.*
import org.hibernate.annotations.Type
import java.time.Instant
import java.util.UUID

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
