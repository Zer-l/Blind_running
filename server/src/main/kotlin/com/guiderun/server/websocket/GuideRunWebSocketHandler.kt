package com.guiderun.server.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import com.guiderun.server.common.RunRequestStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.ConcurrentHashMap

@Component
class GuideRunWebSocketHandler(
    private val objectMapper: ObjectMapper,
) : TextWebSocketHandler() {

    private val log = LoggerFactory.getLogger(javaClass)
    private val sessions = ConcurrentHashMap<String, WebSocketSession>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val userId = session.attributes["userId"] as? String ?: return
        sessions[userId] = session
        log.debug("WS connected: userId={}", userId)
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val userId = session.attributes["userId"] as? String ?: return
        sessions.remove(userId, session)
        log.debug("WS disconnected: userId={}, status={}", userId, status)
    }

    fun pushStatusChanged(
        requestId: String,
        toStatus: RunRequestStatus,
        version: Int,
        userIds: List<String>,
        triggeredRole: String,
        isReminder: Boolean = false,
    ) {
        val payload = buildMap<String, Any?> {
            put("type", "status_changed")
            put("requestId", requestId)
            put("toStatus", toStatus.name)
            put("version", version)
            put("triggeredRole", triggeredRole)
            if (isReminder) put("reminder", true)
        }
        push(payload, userIds)
    }

    fun pushEmergency(
        requestId: String,
        triggeredRole: String,
        reason: String?,
        lat: Double?,
        lng: Double?,
        recipientIds: List<String>,
    ) {
        val payload = buildMap<String, Any?> {
            put("type", "emergency")
            put("requestId", requestId)
            put("triggeredRole", triggeredRole)
            if (reason != null) put("reason", reason)
            if (lat != null && lng != null) put("location", mapOf("lat" to lat, "lng" to lng))
        }
        push(payload, recipientIds)
    }

    fun pushReviewReceived(
        requestId: String,
        toUserId: String,
        rating: Int,
    ) {
        val payload = mapOf(
            "type"      to "review_received",
            "requestId" to requestId,
            "rating"    to rating,
        )
        push(payload, listOf(toUserId))
    }

    fun pushPeerMetrics(
        requestId: String,
        toUserId: String,
        totalDistanceMeters: Int,
        totalDurationSeconds: Int,
        currentPaceSeconds: Int?,
        avgPaceSeconds: Int?,
    ) {
        val payload = buildMap<String, Any?> {
            put("type", "peer_metrics")
            put("requestId", requestId)
            put("totalDistanceMeters", totalDistanceMeters)
            put("totalDurationSeconds", totalDurationSeconds)
            if (currentPaceSeconds != null) put("currentPaceSeconds", currentPaceSeconds)
            if (avgPaceSeconds != null) put("avgPaceSeconds", avgPaceSeconds)
        }
        push(payload, listOf(toUserId))
    }

    /** 志愿者申请结束跑步：推送给视障端，等待视障端确认。 */
    fun pushEndRunRequested(requestId: String, toUserId: String) {
        val payload = mapOf(
            "type"      to "end_run_requested",
            "requestId" to requestId,
        )
        push(payload, listOf(toUserId))
    }

    private fun push(payload: Map<String, Any?>, userIds: List<String>) {
        val json = objectMapper.writeValueAsString(payload)
        userIds.forEach { userId ->
            sessions[userId]?.takeIf { it.isOpen }?.let { session ->
                try {
                    session.sendMessage(TextMessage(json))
                } catch (e: Exception) {
                    log.warn("WS push failed: userId={}, error={}", userId, e.message)
                }
            }
        }
    }
}
