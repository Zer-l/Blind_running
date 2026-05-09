package com.guiderun.server.util

import com.guiderun.server.config.JwtProperties
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Component
import java.util.Date
import javax.crypto.SecretKey

@Component
class JwtUtil(private val props: JwtProperties) {

    private val key: SecretKey by lazy {
        Keys.hmacShaKeyFor(props.secret.toByteArray(Charsets.UTF_8))
    }

    fun generateAccessToken(userId: String, roles: List<String>): String =
        Jwts.builder()
            .subject(userId)
            .claim("roles", roles)
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + props.accessTokenExpiryMs))
            .signWith(key)
            .compact()

    fun parseToken(token: String): Claims =
        Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
}
