package com.guiderun.server.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.jwt")
data class JwtProperties(
    val secret: String,
    val accessTokenExpiryMs: Long = 604_800_000L,
    val refreshTokenExpiryDays: Long = 30L,
)
