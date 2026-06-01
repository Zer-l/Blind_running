package com.guiderun.server.config

import com.guiderun.server.websocket.GuideRunWebSocketHandler
import com.guiderun.server.websocket.WebSocketHandshakeInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

/**
 * WebSocket 端点装配：`/ws/v1` 走 [GuideRunWebSocketHandler]，
 * 握手阶段经 [WebSocketHandshakeInterceptor] 校验 JWT 并将 userId 注入会话属性。
 * `setAllowedOrigins("*")` 仅适用于开发环境，生产应收敛为白名单。
 */
@Configuration
@EnableWebSocket
class WebSocketConfig(
    private val handler: GuideRunWebSocketHandler,
    private val interceptor: WebSocketHandshakeInterceptor,
) : WebSocketConfigurer {

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(handler, "/ws/v1")
            .addInterceptors(interceptor)
            .setAllowedOrigins("*")
    }
}
