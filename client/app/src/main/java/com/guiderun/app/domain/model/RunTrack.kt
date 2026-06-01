package com.guiderun.app.domain.model

/**
 * 单个轨迹采样点（对应 RunTrackingService 每隔约 1s 采集一次 GPS 位置）。
 *
 * spd 来自 Location.getSpeed()（GPS 多普勒速度，m/s），比位置差分更准确。
 * acc 是水平精度半径（m），精度差（acc > 20m）的点在回放时应降权或跳过。
 */
data class TrackPoint(
    val t: Long,
    val lat: Double,
    val lng: Double,
    val acc: Float = 0f,
    val spd: Float? = null,
)

/**
 * 一次完整跑步的轨迹记录（服务端聚合后的结果）。
 *
 * role 区分 BLIND / VOLUNTEER，双端各自上传自己的采集轨迹，
 * 回放页（TrackPlayback）按 role 取对应轨迹渲染。
 */
data class RunTrack(
    val requestId: String,
    val userId: String,
    val role: String,
    val points: List<TrackPoint>,
    val pointCount: Int,
    val totalDistanceMeters: Int,
    val totalDurationSeconds: Int,
    val avgPaceSeconds: Int?,
    val maxSpeed: Float?,
)
