package com.guiderun.server.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "refresh_tokens")
class RefreshTokenEntity(
    @Id
    @Column(length = 36)
    val id: String = UUID.randomUUID().toString(),

    @Column(length = 36, nullable = false)
    val userId: String,

    @Column(length = 64, nullable = false, unique = true)
    val tokenHash: String,

    @Column(nullable = false)
    val expiresAt: Instant,

    @Column(nullable = false)
    var revoked: Boolean = false,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column
    var lastUsedAt: Instant? = null,

    @Column(length = 200)
    val userAgent: String? = null,
)
