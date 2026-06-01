package com.guiderun.app.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * WebSocket 消息 DTO 集合。
 *
 * 服务端推送统一格式：`{ "type": "xxx", ... }`，客户端先读 type 再按类型反序列化。
 * 各 DTO 由 [com.guiderun.app.data.remote.WebSocketManager.dispatchMessage] 分发到对应 SharedFlow。
 */

/** 订单状态变更通知，触发 Repository 主动拉取最新订单详情。 */
@Serializable
data class WsStatusChangeMessage(
    val type: String,
    val requestId: String,
    val toStatus: String,
    val version: Int,
    val triggeredRole: String? = null,
    val reminder: Boolean = false,
)

/** 志愿者配速数据实时推送（每 5 秒一次），供视障端展示对方距离/配速。 */
@Serializable
data class WsPeerMetricsMessage(
    val type: String,
    val requestId: String,
    val totalDistanceMeters: Int,
    val totalDurationSeconds: Int,
    val currentPaceSeconds: Int? = null,
    val avgPaceSeconds: Int? = null,
)

/** 评价提交后服务端推送给被评价方，触发 UI 显示"收到评价"提示。 */
@Serializable
data class WsReviewReceivedMessage(
    val type: String,
    val requestId: String,
    val rating: Int,
)

/** SOS 紧急求助通知，包含触发方角色和可选 GPS 位置，推送给对方做应急响应。 */
@Serializable
data class WsEmergencyMessage(
    val type: String,
    val requestId: String,
    val triggeredRole: String,
    val reason: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
)

/**
 * 协商式结束通知：志愿者调 [com.guiderun.app.data.remote.api.RunRequestApi.requestEndRun] 后，
 * 服务端推送给视障端，提示其长按确认结束跑步。
 */
@Serializable
data class WsEndRunRequestedMessage(
    val type: String,
    val requestId: String,
)
