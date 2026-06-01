package com.guiderun.server.dto.run

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

// ── 请求体 ──────────────────────────────────────────────────────────────

/** 位置上报：志愿者实时位置（WGS-84），由服务端转换 GCJ-02 后下发视障端。 */
data class ReportPositionDto(
    @field:NotNull val lat: Double,
    @field:NotNull val lng: Double,
)

/** 地点 DTO：坐标 + 文字描述（集合点必带 description，便于 TTS 朗读）。 */
data class GeoPointDto(
    val lat: Double,
    val lng: Double,
    @field:NotBlank val description: String,
)

/** 发起跑步请求体：集合点必填，预期时长 5-480 分钟，其余可选。 */
data class CreateRunRequestDto(
    @field:NotNull val meetingLocation: GeoPointDto,
    @field:Min(5) @field:Max(480) val expectedDurationMinutes: Int = 60,
    val expectedDistanceMeters: Int? = null,
    val expectedPaceSeconds: Int? = null,
    val notes: String? = null,
)

/** 志愿者放弃接单请求体，可选填原因（连续 3 次将降级为 ABORTED 终态）。 */
data class AbandonDto(
    val reason: String? = null,
)

/** 结束跑步请求体（视障端确认）：携带实际距离/时长/配速，超量值由 Bean Validation 拦截。 */
data class EndRunDto(
    @field:Min(0) @field:Max(100_000) val actualDistanceMeters: Int? = null,
    @field:Min(0) @field:Max(86_400) val actualDurationSeconds: Int? = null,
    @field:Min(0) @field:Max(3600) val avgPaceSeconds: Int? = null,
)

/** 取消订单请求体（视障端唯一退出路径，志愿者端不可调用）。 */
data class CancelDto(
    val reason: String? = null,
)

/** 评价提交请求体：评分 1-5 必填，标签/文字/语音 URL 可选。 */
data class CreateReviewDto(
    @field:Min(1) @field:Max(5) val rating: Int,
    val tags: List<String> = emptyList(),
    val comment: String? = null,
    val voiceUrl: String? = null,
)

// ── 响应体 ──────────────────────────────────────────────────────────────

/** 用户摘要：在订单详情/列表中作为对方信息卡片，phone 受隐私规则控制（详见字段注释）。 */
data class UserSummaryDto(
    val id: String,
    val nickname: String,
    val avatarUrl: String?,
    val gender: String?,
    val rating: Float?,
    val totalRuns: Int,
    /** 仅在 ACCEPTED 之后下发给同订单参与方，用于跑前/跑中拨打电话；其他场景为 null。 */
    val phone: String?,
)

/** 视障用户摘要（接单列表专用）：相比 UserSummaryDto 额外携带视力等级供志愿者判断。 */
data class BlindRunnerSummaryDto(
    val id: String,
    val nickname: String,
    val rating: Float?,
    val gender: String?,
    val visionLevel: String?,
    val totalRuns: Int,
)

/** 实时位置 DTO：经纬度 + 最后更新时间戳，用于"志愿者前往中"地图展示。 */
data class GeoPositionDto(val lat: Double, val lng: Double, val updatedAt: Long)

/**
 * 订单完整响应：覆盖 9 状态所有阶段的时间戳与数据快照，按状态裁剪可见字段。
 * `myReviewSubmitted` 仅在带身份上下文的接口下发，用于历史页"补评"入口判断。
 */
data class RunRequestResponse(
    val id: String,
    val status: String,
    val version: Int,
    val blindRunner: UserSummaryDto?,
    val volunteer: UserSummaryDto?,
    val meetingLocation: GeoPointDto,
    val expectedDurationMinutes: Int,
    val expectedDistanceMeters: Int?,
    val expectedPaceSeconds: Int?,
    val actualDistanceMeters: Int?,
    val actualDurationSeconds: Int?,
    val avgPaceSeconds: Int?,
    val isAbnormal: Boolean,
    val notes: String?,
    val abortReason: String?,
    val abortBy: String?,
    val createdAt: Long,
    val matchedAt: Long?,
    val departedAt: Long?,
    val metAt: Long?,
    val runStartedAt: Long?,
    val runEndedAt: Long?,
    val closedAt: Long?,
    val volunteerPosition: GeoPositionDto? = null,
    /**
     * 当前请求者是否已对该订单提交过评价。
     * 仅在带身份上下文的接口（getMyRequests / getById）填充，其他场景为 null。
     * 客户端依此决定历史列表是否显示"补评"按钮。
     */
    val myReviewSubmitted: Boolean? = null,
)

/** 附近可接单订单项：携带视障用户摘要 + 集合点 + 与当前志愿者的实时距离。 */
data class AvailableRequestItemDto(
    val id: String,
    val blindRunner: BlindRunnerSummaryDto,
    val meetingLocation: GeoPointDto,
    val expectedDurationMinutes: Int,
    val distanceMeters: Int,
    val createdAt: Long,
)
