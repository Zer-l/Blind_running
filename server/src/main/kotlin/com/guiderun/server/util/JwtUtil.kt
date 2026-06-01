package com.guiderun.server.util

import com.guiderun.server.config.JwtProperties
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Component
import java.util.Date
import javax.crypto.SecretKey

/**
 * JWT 签发与解析工具。
 *
 * - HS256 对称签名，密钥由 [JwtProperties.secret] 提供（生产建议 ≥ 256 bit）
 * - 仅签发 accessToken（短期），refreshToken 由 [com.guiderun.server.service.AuthService] 自管随机串入库
 * - subject = userId；自定义 claim `roles` 携带角色列表，用于 [JwtAuthFilter] 注入 SecurityContext
 */
@Component
class JwtUtil(private val props: JwtProperties) {

    private val key: SecretKey by lazy {
        Keys.hmacShaKeyFor(props.secret.toByteArray(Charsets.UTF_8))
    }

    /** 签发 accessToken（subject=userId, roles=自定义 claim, 过期=now+[JwtProperties.accessTokenExpiryMs]）。 */
    fun generateAccessToken(userId: String, roles: List<String>): String =
        Jwts.builder()
            .subject(userId)
            .claim("roles", roles)
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + props.accessTokenExpiryMs))
            .signWith(key)
            .compact()

    /** 校验签名 + 过期时间，解析后返回 Claims；异常由调用方 `runCatching` 包装。 */
    fun parseToken(token: String): Claims =
        Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
}
