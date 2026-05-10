package com.guiderun.app.data.remote.dto

import kotlinx.serialization.Serializable

// status_changed（原有，保持不变）
@Serializable
data class WsStatusChangeMessage(
    val type: String,
    val requestId: String,
    val toStatus: String,
    val version: Int,
    val triggeredRole: String? = null,
    val reminder: Boolean = false,
)

// peer_metrics：志愿者每5秒推送给视障端
@Serializable
data class WsPeerMetricsMessage(
    val type: String,
    val requestId: String,
    val totalDistanceMeters: Int,
    val totalDurationSeconds: Int,
    val currentPaceSeconds: Int? = null,
    val avgPaceSeconds: Int? = null,
)

// review_received：评价提交后通知被评价方
@Serializable
data class WsReviewReceivedMessage(
    val type: String,
    val requestId: String,
    val rating: Int,
)

// emergency：紧急求助通知对方
@Serializable
data class WsEmergencyMessage(
    val type: String,
    val requestId: String,
    val triggeredRole: String,
    val reason: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
)

// end_run_requested：志愿者申请结束跑步，推送给视障端等待确认
@Serializable
data class WsEndRunRequestedMessage(
    val type: String,
    val requestId: String,
)
