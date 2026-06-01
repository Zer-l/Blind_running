package com.guiderun.server.dto.run

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

/** 轨迹采样点 DTO：时间戳 + WGS-84 坐标 + 精度 + 瞬时速度。 */
data class TrackPointDto(
    @field:NotNull val t: Long,
    @field:NotNull val lat: Double,
    @field:NotNull val lng: Double,
    val acc: Float = 0f,
    val spd: Float? = null,
)

/**
 * 轨迹上传请求：单次最多 1000 点，可附"已扣暂停"的权威累计值。
 * 详细策略见 [com.guiderun.server.service.RunTrackService.applyClientAuthoritative]。
 */
data class UploadTracksDto(
    @field:NotNull val role: String,
    @field:NotEmpty @field:Size(max = 1000) val points: List<TrackPointDto>,
    // 客户端实时算好的权威累计值（运动时长/距离，已扣暂停）。提供时服务端按此存储，不再用墙钟跨度重算。
    // 增量上传每批带当前快照，结束 flush 带最终值；为空则回退服务端 Haversine 重算（向后兼容）。
    val totalDistanceMeters: Int? = null,
    val totalDurationSeconds: Int? = null,
    val avgPaceSeconds: Int? = null,
    val maxSpeed: Float? = null,
)

/** 轨迹查询响应：含完整轨迹点 + 汇总统计，用于历史回放页面。 */
data class RunTrackResponseDto(
    val requestId: String,
    val userId: String,
    val role: String,
    val points: List<TrackPointDto>,
    val pointCount: Int,
    val totalDistanceMeters: Int,
    val totalDurationSeconds: Int,
    val avgPaceSeconds: Int?,
    val maxSpeed: Float?,
)
