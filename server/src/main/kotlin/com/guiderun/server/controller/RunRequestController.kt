package com.guiderun.server.controller

import com.guiderun.server.common.RunRequestStatus
import com.guiderun.server.dto.ApiResponse
import com.guiderun.server.dto.common.ListResponse
import com.guiderun.server.dto.run.*
import com.guiderun.server.exception.AppException
import com.guiderun.server.exception.ErrorCode
import com.guiderun.server.service.RunRequestService
import com.guiderun.server.service.RunTrackService
import jakarta.validation.Valid
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

/**
 * 跑步请求核心接口：覆盖 9 状态全生命周期 + 轨迹 + 评价 + 紧急事件。
 *
 * 路由组织：
 * - 列表/查询：`POST /` 创建、`GET /available` 附近订单、`GET /active` 当前活跃订单（冷启动恢复用）
 * - 状态推进：`/{id}/accept` `/depart` `/confirm-met` `/start-run` `/end-run` `/request-end-run`
 * - 终止：`/cancel` `/abandon` `/emergency`（含状态机校验）
 * - 实时上报：`/position` `/peer-metrics`
 * - 轨迹与评价：`/tracks` `/reviews`
 *
 * 注意：`/available` 必须在 `/{id}` 之前声明，Spring MVC 字面路径优先级高于路径变量。
 */
@RestController
@RequestMapping("/api/v1/run-requests")
class RunRequestController(
    private val service: RunRequestService,
    private val trackService: RunTrackService,
) {

    private val currentUserId: String
        get() = SecurityContextHolder.getContext().authentication.principal as String

    @PostMapping
    fun create(
        @RequestBody @Valid dto: CreateRunRequestDto,
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?,
    ) = ApiResponse.ok(service.create(currentUserId, dto, idempotencyKey))

    // /available 必须在 /{id} 之前声明，Spring MVC 字面路径优先级高于路径变量
    @GetMapping("/available")
    fun available(
        @RequestParam lat: Double,
        @RequestParam lng: Double,
        @RequestParam(defaultValue = "3000") radius: Double,
    ) = ApiResponse.ok(ListResponse.of(service.getAvailable(currentUserId, lat, lng, minOf(radius, 10000.0))))

    // 当前用户的活跃订单（用于客户端冷启动恢复），无活跃订单时 data=null
    @GetMapping("/active")
    fun active(@RequestParam role: String) = ApiResponse.ok(service.getActiveRequest(currentUserId, role))

    @GetMapping("/{id}")
    fun getById(@PathVariable id: String) = ApiResponse.ok(service.getById(id))

    @GetMapping
    fun list(
        @RequestParam role: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) status: String?,
    ) = ApiResponse.ok(
        ListResponse.of(
            service.getMyRequests(currentUserId, role, page, parseStatuses(status))
        )
    )

    private fun parseStatuses(raw: String?): Collection<RunRequestStatus>? {
        if (raw.isNullOrBlank()) return null
        return raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.map {
            runCatching { RunRequestStatus.valueOf(it.uppercase()) }
                .getOrElse { throw AppException(ErrorCode.INVALID_PARAM, "无效的 status: $it") }
        }
    }

    @PostMapping("/{id}/accept")
    fun accept(@PathVariable id: String) = ApiResponse.ok(service.accept(currentUserId, id))

    @PostMapping("/{id}/depart")
    fun depart(@PathVariable id: String) = ApiResponse.ok(service.depart(currentUserId, id))

    @PostMapping("/{id}/confirm-met")
    fun confirmMet(@PathVariable id: String) = ApiResponse.ok(service.confirmMet(currentUserId, id))

    @PostMapping("/{id}/start-run")
    fun startRun(@PathVariable id: String) = ApiResponse.ok(service.startRun(currentUserId, id))

    @PostMapping("/{id}/end-run")
    fun endRun(
        @PathVariable id: String,
        @RequestBody(required = false) @Valid dto: EndRunDto?,
    ) = ApiResponse.ok(service.endRun(currentUserId, id, dto))

    @PostMapping("/{id}/request-end-run")
    fun requestEndRun(@PathVariable id: String) = ApiResponse.ok(service.requestEndRun(currentUserId, id))

    @PostMapping("/{id}/cancel")
    fun cancel(
        @PathVariable id: String,
        @RequestBody(required = false) dto: CancelDto?,
    ) = ApiResponse.ok(service.cancel(currentUserId, id, dto?.reason))

    @PostMapping("/{id}/abandon")
    fun abandon(
        @PathVariable id: String,
        @RequestBody(required = false) dto: AbandonDto?,
    ) = ApiResponse.ok(service.abandon(currentUserId, id, dto?.reason))

    @PostMapping("/{id}/emergency")
    fun emergency(
        @PathVariable id: String,
        @RequestBody(required = false) dto: EmergencyDto?,
    ) = ApiResponse.ok(service.emergency(currentUserId, id, dto))

    @PostMapping("/{id}/position")
    fun reportPosition(
        @PathVariable id: String,
        @RequestBody @Valid dto: ReportPositionDto,
    ): ApiResponse<Unit> {
        service.reportPosition(currentUserId, id, dto)
        return ApiResponse.ok()
    }

    @PostMapping("/{id}/peer-metrics")
    fun pushPeerMetrics(
        @PathVariable id: String,
        @RequestBody @Valid dto: PeerMetricsDto,
    ): ApiResponse<Unit> {
        service.pushPeerMetrics(currentUserId, id, dto)
        return ApiResponse.ok()
    }

    @PostMapping("/{id}/reviews")
    fun createReview(
        @PathVariable id: String,
        @RequestBody @Valid dto: CreateReviewDto,
    ): ApiResponse<Unit> {
        service.createReview(currentUserId, id, dto)
        return ApiResponse.ok()
    }

    @GetMapping("/{id}/reviews")
    fun getReviews(@PathVariable id: String) =
        ApiResponse.ok(ListResponse.of(service.getReviews(id)))

    @PostMapping("/{id}/tracks")
    fun uploadTracks(
        @PathVariable id: String,
        @RequestBody @Valid dto: UploadTracksDto,
    ) = ApiResponse.ok(trackService.uploadTracks(currentUserId, id, dto))

    @GetMapping("/{id}/tracks")
    fun getTracks(@PathVariable id: String) =
        ApiResponse.ok(ListResponse.of(trackService.getTracks(id)))
}
