package com.guiderun.app.domain.model

data class RunRequest(
    val id: String,
    val status: RunRequestStatus,
    val version: Int,
    val blindRunner: UserSummary?,
    val volunteer: UserSummary?,
    val meetingLocation: GeoPoint,
    val expectedDurationMinutes: Int,
    val expectedDistanceMeters: Int?,
    val expectedPaceSeconds: Int?,
    val actualDistanceMeters: Int?,
    val actualDurationSeconds: Int?,
    val avgPaceSeconds: Int?,
    val isAbnormal: Boolean = false,
    val notes: String?,
    val abortReason: String?,
    val abortBy: AbortBy?,
    val createdAt: Long,
    val matchedAt: Long?,
    val departedAt: Long?,
    val metAt: Long?,
    val runStartedAt: Long?,
    val runEndedAt: Long?,
    val closedAt: Long?,
    val volunteerPosition: GeoPosition? = null,
    /**
     * 当前用户是否已对该订单提交过评价。
     * 仅在带身份上下文的接口（getMyRequests / getById）由服务端填充，其他场景为 null。
     * UI 据此显示历史页"补评"按钮：仅当 status.isCompleted() && myReviewSubmitted == false 时显示。
     */
    val myReviewSubmitted: Boolean? = null,
)

data class GeoPosition(val lat: Double, val lng: Double, val updatedAt: Long)

data class AvailableRunRequest(
    val id: String,
    val blindRunner: BlindRunnerSummary,
    val meetingLocation: GeoPoint,
    val expectedDurationMinutes: Int,
    val distanceMeters: Int,
    val createdAt: Long,
)

data class GeoPoint(
    val lat: Double,
    val lng: Double,
    val description: String,
    val accuracy: Float = 0f,
    // 单调时钟时间戳（ms，SystemClock.elapsedRealtime 域），仅用于跑步采集，
    // 来自 LocationProvider 的位置流；其他来源（如手动构造、Mapper）默认 0。
    val realtimeMs: Long = 0L,
    // GPS 多普勒速度（m/s），来自 Location.getSpeed()。比位置差分准得多、噪声小。
    // null 表示该定位不含速度（老设备 / 无 GNSS 速度），由采集端 fallback 位置差分。
    val speedMps: Float? = null,
    // 速度精度（m/s，1σ），来自 Location.getSpeedAccuracyMetersPerSecond()（API 26+）。
    // null 表示精度未知，此时仍信任 speedMps。
    val speedAccuracyMps: Float? = null,
)

data class UserSummary(
    val id: String,
    val nickname: String,
    val avatarUrl: String?,
    val gender: String?,
    val rating: Float?,
    val totalRuns: Int,
    /** 对方手机号；仅在订单 ACCEPTED 之后服务端下发，其他场景为 null。 */
    val phone: String? = null,
)

data class BlindRunnerSummary(
    val id: String,
    val nickname: String,
    val rating: Float?,
    val gender: String?,
    val visionLevel: String?,
    val totalRuns: Int,
)

enum class RunRequestStatus {
    // CREATED 为内部中间态，API 永远不会返回此值，仅供本地枚举完整性
    CREATED,
    MATCHING, ACCEPTED, EN_ROUTE, MET, RUNNING, FINISHED, CLOSED, ABORTED;

    /**
     * 终态包含 FINISHED：跑步结束即视为订单完成。
     * 评价是独立行为，不再影响订单"活跃"状态，不再阻塞首页横幅 / 冷启动恢复。
     * 服务端 FINISHED→CLOSED 仍由 24h 定时器 / 双方评价完成触发，用于落 totalRuns/rating 统计。
     */
    fun isTerminal() = this == CLOSED || this == ABORTED || this == FINISHED
    fun isActive() = !isTerminal()

    /**
     * 成功完成（用于历史页的"已完成"分类与累计统计），区别于 ABORTED 异常终止。
     * FINISHED 视为已完成 —— 只是双方还未评价完，等 24h 定时器或双方评价完成才推进 CLOSED。
     */
    fun isCompleted() = this == CLOSED || this == FINISHED
}

enum class AbortBy { BLIND, VOLUNTEER, SYSTEM, ADMIN }

// 事件触发方角色（与 UserRole 语义不同，含 SYSTEM/ADMIN，使用短名 BLIND）
enum class TriggeredRole { BLIND, VOLUNTEER, SYSTEM, ADMIN }

data class CreateRunRequestParams(
    val meetingLat: Double,
    val meetingLng: Double,
    val meetingDescription: String,
    val expectedDurationMinutes: Int = 60,
    val expectedDistanceMeters: Int? = null,
    val expectedPaceSeconds: Int? = null,
    val notes: String? = null,
)
