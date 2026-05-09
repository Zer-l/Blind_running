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
