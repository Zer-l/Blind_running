package com.guiderun.server.dto.run

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class TrackPointDto(
    @field:NotNull val t: Long,
    @field:NotNull val lat: Double,
    @field:NotNull val lng: Double,
    val acc: Float = 0f,
    val spd: Float? = null,
)

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
