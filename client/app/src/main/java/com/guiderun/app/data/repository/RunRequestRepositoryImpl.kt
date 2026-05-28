package com.guiderun.app.data.repository

import com.guiderun.app.data.local.UserPreferences
import com.guiderun.app.data.mapper.toDomain
import com.guiderun.app.data.remote.WebSocketManager
import com.guiderun.app.data.remote.api.RunRequestApi
import com.guiderun.app.data.remote.dto.*
import com.guiderun.app.domain.exception.AlreadyReviewedException
import com.guiderun.app.domain.exception.ForbiddenActionException
import com.guiderun.app.domain.exception.InvalidStateTransitionException
import com.guiderun.app.domain.exception.NetworkException
import com.guiderun.app.domain.exception.ProvisioningIncompleteException
import com.guiderun.app.domain.exception.RequestConflictException
import com.guiderun.app.domain.exception.RequestNotFoundException
import com.guiderun.app.domain.exception.UnknownApiException
import com.guiderun.app.domain.model.*
import com.guiderun.app.domain.repository.RunRequestRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RunRequestRepositoryImpl @Inject constructor(
    private val api: RunRequestApi,
    private val json: Json,
    private val userPreferences: UserPreferences,
    private val webSocketManager: WebSocketManager,
) : RunRequestRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _activeRequest = MutableStateFlow<RunRequest?>(null)
    override val activeRequest: StateFlow<RunRequest?> = _activeRequest.asStateFlow()

    init {
        // WS 重连后若有活跃订单，主动同步一次最新状态
        scope.launch {
            webSocketManager.reconnected.collect {
                userPreferences.getActiveRequestId()?.let { id ->
                    runCatching { api.getById(id).requireData().toDomain() }
                        .onSuccess { trackActive(it) }
                }
            }
        }
        // WS 推送状态变化时，若是当前活跃订单，刷新本地缓存
        scope.launch {
            webSocketManager.messages.collect { msg ->
                val activeId = userPreferences.getActiveRequestId() ?: return@collect
                if (msg.requestId == activeId) {
                    runCatching { api.getById(activeId).requireData().toDomain() }
                        .onSuccess { trackActive(it) }
                }
            }
        }
    }

    private suspend fun trackActive(req: RunRequest) {
        // 仅当"我"是该订单的参与者（视障发起人或接单志愿者）时才写入 active 缓存。
        // 否则志愿者查看陌生人 MATCHING 订单详情时会被误标为"自己有活跃订单"，导致接单按钮被禁。
        val myUserId = userPreferences.getCurrentUserId()
        val isMine = myUserId != null &&
            (req.blindRunner?.id == myUserId || req.volunteer?.id == myUserId)
        if (!isMine) return

        if (req.status.isActive()) {
            userPreferences.saveActiveRequestId(req.id)
            _activeRequest.value = req
        } else {
            // 终态：仅当本地存的就是这条订单时才清理，避免误清其他订单的痕迹
            val storedId = userPreferences.getActiveRequestId()
            if (storedId == null || storedId == req.id) {
                userPreferences.clearActiveRequestId()
                _activeRequest.value = null
            }
        }
    }

    override suspend fun refreshActiveRequest(role: String): Result<RunRequest?> = execute {
        // 优先用本地保存的 id 拉取，命中则与状态同步；失败则降级用 /active 接口兜底
        val storedId = userPreferences.getActiveRequestId()
        val fromStored = storedId?.let {
            runCatching { api.getById(it).requireData().toDomain() }.getOrNull()
        }
        val fresh = fromStored ?: api.getActive(role).data?.toDomain()
        if (fresh != null) {
            trackActive(fresh)
            if (fresh.status.isActive()) fresh else null
        } else {
            // 服务端无活跃订单：清理本地脏数据
            userPreferences.clearActiveRequestId()
            _activeRequest.value = null
            null
        }
    }

    override suspend fun createRunRequest(params: CreateRunRequestParams, idempotencyKey: String?): Result<RunRequest> =
        execute(idempotencyKey = idempotencyKey) {
            api.createRunRequest(
                dto = CreateRunRequestRequestDto(
                    meetingLocation = GeoPointRequestDto(params.meetingLat, params.meetingLng, params.meetingDescription),
                    expectedDurationMinutes = params.expectedDurationMinutes,
                    expectedDistanceMeters = params.expectedDistanceMeters,
                    expectedPaceSeconds = params.expectedPaceSeconds,
                    notes = params.notes,
                ),
                idempotencyKey = idempotencyKey,
            ).requireData().toDomain().also { trackActive(it) }
        }

    override suspend fun getRunRequest(id: String): Result<RunRequest> =
        execute {
            api.getById(id).requireData().toDomain().also { req ->
                // 仅当查询的是当前活跃订单或本地无活跃订单且查询结果是活跃态时同步
                val storedId = userPreferences.getActiveRequestId()
                if (storedId == req.id || (storedId == null && req.status.isActive())) {
                    trackActive(req)
                }
            }
        }

    override suspend fun getMyRequests(role: String, page: Int): Result<List<RunRequest>> =
        execute { api.getMyRequests(role, page).requireData().items.map { it.toDomain() } }

    override suspend fun getAvailableRequests(lat: Double, lng: Double, radiusMeters: Double): Result<List<AvailableRunRequest>> =
        execute { api.getAvailable(lat, lng, radiusMeters).requireData().items.map { it.toDomain() } }

    override suspend fun accept(id: String): Result<RunRequest> =
        execute { api.accept(id).requireData().toDomain().also { trackActive(it) } }

    override suspend fun depart(id: String): Result<RunRequest> =
        execute { api.depart(id).requireData().toDomain().also { trackActive(it) } }

    override suspend fun confirmMet(id: String): Result<RunRequest> =
        execute { api.confirmMet(id).requireData().toDomain().also { trackActive(it) } }

    override suspend fun startRun(id: String): Result<RunRequest> =
        execute { api.startRun(id).requireData().toDomain().also { trackActive(it) } }

    override suspend fun endRun(id: String, actualDistanceMeters: Int?, actualDurationSeconds: Int?, avgPaceSeconds: Int?): Result<RunRequest> =
        execute {
            api.endRun(id, EndRunRequestDto(actualDistanceMeters, actualDurationSeconds, avgPaceSeconds))
                .requireData().toDomain().also { trackActive(it) }
        }

    override suspend fun requestEndRun(id: String): Result<RunRequest> =
        execute { api.requestEndRun(id).requireData().toDomain().also { trackActive(it) } }

    override suspend fun cancel(id: String, reason: String?): Result<RunRequest> =
        execute { api.cancel(id, CancelRequestDto(reason)).requireData().toDomain().also { trackActive(it) } }

    override suspend fun abandon(id: String): Result<RunRequest> =
        execute { api.abandon(id).requireData().toDomain().also { trackActive(it) } }

    override suspend fun emergency(id: String, reason: String?, lat: Double?, lng: Double?): Result<RunRequest> =
        execute {
            val dto = if (reason != null || lat != null || lng != null) {
                EmergencyRequestDto(
                    reason = reason,
                    currentLocation = if (lat != null && lng != null) EmergencyGeoRequestDto(lat, lng) else null,
                    timestamp = System.currentTimeMillis(),
                )
            } else null
            api.emergency(id, dto).requireData().toDomain().also { trackActive(it) }
        }

    override suspend fun reportPosition(id: String, lat: Double, lng: Double): Result<Unit> =
        execute { api.reportPosition(id, ReportPositionRequestDto(lat, lng)); Unit }

    override suspend fun getReviews(requestId: String): Result<List<Review>> =
        execute { api.getReviews(requestId).requireData().items.map { it.toDomain() } }

    override suspend fun uploadTracks(
        requestId: String,
        role: String,
        points: List<TrackPoint>,
        totalDistanceMeters: Int?,
        totalDurationSeconds: Int?,
        avgPaceSeconds: Int?,
        maxSpeed: Float?,
    ): Result<List<RunTrack>> =
        execute {
            val dto = UploadTracksDto(
                role = role,
                points = points.map { TrackPointDto(it.t, it.lat, it.lng, it.acc, it.spd) },
                totalDistanceMeters = totalDistanceMeters,
                totalDurationSeconds = totalDurationSeconds,
                avgPaceSeconds = avgPaceSeconds,
                maxSpeed = maxSpeed,
            )
            api.uploadTracks(requestId, dto).requireData().items.map { it.toDomain() }
        }

    override suspend fun getTracks(requestId: String): Result<List<RunTrack>> =
        execute { api.getTracks(requestId).requireData().items.map { it.toDomain() } }

    override suspend fun pushPeerMetrics(
        requestId: String,
        totalDistanceMeters: Int,
        totalDurationSeconds: Int,
        currentPaceSeconds: Int?,
        avgPaceSeconds: Int?,
    ): Result<Unit> =
        execute {
            api.pushPeerMetrics(requestId, PeerMetricsDto(totalDistanceMeters, totalDurationSeconds, currentPaceSeconds, avgPaceSeconds))
            Unit
        }

    override suspend fun createReview(requestId: String, params: CreateReviewParams): Result<Unit> =
        execute {
            api.createReview(requestId, CreateReviewRequestDto(params.rating, params.tags, params.comment))
            Unit
        }

    // ── 辅助方法 ──────────────────────────────────────────────────────────

    private inline fun <T> execute(idempotencyKey: String? = null, block: () -> T): Result<T> =
        runCatching { block() }.recoverCatching { mapException(it) }

    private fun mapException(e: Throwable): Nothing {
        throw when (e) {
            is HttpException -> when (e.code()) {
                400 -> when (e.parseErrorCode(json)) {
                    "INVALID_STATE_TRANSITION" -> InvalidStateTransitionException(e.parseMessage(json))
                    "ALREADY_REVIEWED"         -> AlreadyReviewedException()
                    else                       -> InvalidStateTransitionException(e.parseMessage(json))
                }
                403 -> when (e.parseErrorCode(json)) {
                    "PROVISIONING_INCOMPLETE" -> ProvisioningIncompleteException()
                    else                      -> ForbiddenActionException(e.parseMessage(json))
                }
                404 -> RequestNotFoundException()
                409 -> RequestConflictException(e.parseMessage(json))
                else -> UnknownApiException(e.code(), e.parseMessage(json))
            }
            is IOException -> NetworkException("网络请求失败：${e.message}", e)
            else           -> NetworkException(e.message ?: "未知错误", e)
        }
    }
}

private fun <T> ApiResponse<T>.requireData(): T =
    data ?: throw UnknownApiException(code, message)

private fun HttpException.parseErrorBody(json: Json): ApiResponse<kotlinx.serialization.json.JsonElement>? =
    runCatching {
        response()?.errorBody()?.string()
            ?.let { json.decodeFromString<ApiResponse<kotlinx.serialization.json.JsonElement>>(it) }
    }.getOrNull()

private fun HttpException.parseMessage(json: Json): String =
    parseErrorBody(json)?.message ?: message()

private fun HttpException.parseErrorCode(json: Json): String? =
    parseErrorBody(json)?.errorCode
