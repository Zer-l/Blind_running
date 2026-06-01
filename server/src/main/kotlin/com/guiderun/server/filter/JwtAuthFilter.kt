package com.guiderun.server.filter

import com.guiderun.server.util.JwtUtil
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * JWT 鉴权过滤器：每个请求执行一次。
 *
 * 从 `Authorization: Bearer <token>` 头解析 Claims，将 userId（subject）和 roles 填入
 * SecurityContext。token 缺失或解析失败时静默放行，由下游 `authorizeHttpRequests` 决定是否 401。
 */
@Component
class JwtAuthFilter(private val jwtUtil: JwtUtil) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val header = request.getHeader("Authorization")
        if (header != null && header.startsWith("Bearer ")) {
            val token = header.removePrefix("Bearer ")
            runCatching { jwtUtil.parseToken(token) }
                .onSuccess { claims ->
                    @Suppress("UNCHECKED_CAST")
                    val roles = (claims["roles"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    val auth = UsernamePasswordAuthenticationToken(
                        claims.subject,
                        null,
                        roles.map { SimpleGrantedAuthority("ROLE_$it") },
                    )
                    SecurityContextHolder.getContext().authentication = auth
                }
        }
        chain.doFilter(request, response)
    }
}
