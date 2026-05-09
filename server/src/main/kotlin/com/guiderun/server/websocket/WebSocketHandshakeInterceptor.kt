package com.guiderun.server.websocket

import com.guiderun.server.common.ProvisioningStatus
import com.guiderun.server.repository.UserJpaRepository
import com.guiderun.server.util.JwtUtil
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.http.server.ServletServerHttpRequest
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.server.HandshakeInterceptor

@Component
class WebSocketHandshakeInterceptor(
    private val jwtUtil: JwtUtil,
    private val userRepo: UserJpaRepository,
) : HandshakeInterceptor {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun beforeHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>,
    ): Boolean {
        val token = (request as? ServletServerHttpRequest)
            ?.servletRequest?.getParameter("token")
            ?: run {
                response.setStatusCode(HttpStatus.UNAUTHORIZED)
                return false
            }

        return try {
            val claims = jwtUtil.parseToken(token)
            val userId = claims.subject
            val user = userRepo.findById(userId).orElse(null) ?: run {
                response.setStatusCode(HttpStatus.UNAUTHORIZED)
                return false
            }
            // D8: PENDING_ROLE check — reject before upgrade
            if (user.provisioningStatus == ProvisioningStatus.PENDING_ROLE) {
                response.setStatusCode(HttpStatus.FORBIDDEN)
                return false
            }
            attributes["userId"] = userId
            true
        } catch (e: Exception) {
            log.warn("WS handshake rejected: {}", e.message)
            response.setStatusCode(HttpStatus.UNAUTHORIZED)
            false
        }
    }

    override fun afterHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        exception: Exception?,
    ) {}
}
