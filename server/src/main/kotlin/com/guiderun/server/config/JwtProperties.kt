package com.guiderun.server.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * JWT 配置项，从 `application.yml` 的 `app.jwt.*` 注入。
 *
 * - [secret] 通过环境变量 `JWT_SECRET` 覆盖（生产必须显式设置）
 * - [accessTokenExpiryMs] 默认 7 天，过期后客户端凭 refreshToken 换新
 * - [refreshTokenExpiryDays] 默认 30 天，过期后强制重新登录
 */
@ConfigurationProperties(prefix = "app.jwt")
data class JwtProperties(
    val secret: String,
    val accessTokenExpiryMs: Long = 604_800_000L,
    val refreshTokenExpiryDays: Long = 30L,
)
