package com.guiderun.server.config

import com.guiderun.server.websocket.GuideRunWebSocketHandler
import com.guiderun.server.websocket.WebSocketHandshakeInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

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
