package com.guiderun.server.service

import com.guiderun.server.common.*
import com.guiderun.server.domain.RunRequestStateMachine
import com.guiderun.server.dto.run.*
import com.guiderun.server.dto.run.EmergencyDto
import com.guiderun.server.dto.run.PeerMetricsDto
import com.guiderun.server.entity.ReviewEntity
import com.guiderun.server.entity.RunRequestEntity
import com.guiderun.server.entity.RunRequestEventEntity
import com.guiderun.server.entity.UserEntity
import com.guiderun.server.exception.AppException
import com.guiderun.server.exception.ErrorCode
import com.guiderun.server.mapper.toAvailableItem
import com.guiderun.server.mapper.toResponse
import com.guiderun.server.repository.ReviewJpaRepository
import com.guiderun.server.repository.RunRequestEventJpaRepository
import com.guiderun.server.repository.RunRequestJpaRepository
import com.guiderun.server.repository.UserJpaRepository
import com.guiderun.server.util.IdempotencyStore
import com.guiderun.server.websocket.GuideRunWebSocketHandler
import org.slf4j.LoggerFactory
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sqrt

@Service
@Transactional
class RunRequestService(
    private val runRequestRepo: RunRequestJpaRepository,
    private val eventRepo: RunRequestEventJpaRepository,
    private val reviewRepo: ReviewJpaRepository,
    private val userRepo: UserJpaRepository,
    private val stateMachine: RunRequestStateMachine,
    private val idempotencyStore: IdempotencyStore,
    private val wsHandler: GuideRunWebSocketHandler,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    // ── 发起请求 ──────────────────────────────────────────────────────────

    fun create(
        blindUserId: String,
        dto: CreateRunRequestDto,
        idempotencyKey: String?,
    ): RunRequestResponse {
        val doCreate = {
            val user = loadAndRequireRole(blindUserId, UserRole.BLIND_RUNNER, "只有视障用户可以发起跑步请求")
            // 已有进行中订单（MATCHING/ACCEPTED/EN_ROUTE/MET/RUNNING）则禁止重复发单
            requireNoActiveOrderForBlind(blindUserId)
            val entity = RunRequestEntity(
                blindRunnerId = blindUserId,
                status = RunRequestStatus.MATCHING,
                expectedDurationMinutes = dto.expectedDurationMinutes,
                expectedDistanceMeters = dto.expectedDistanceMeters,
                expectedPaceSeconds = dto.expectedPaceSeconds,
                meetingLat = dto.meetingLocation.lat,
                meetingLng = dto.meetingLocation.lng,
                meetingLocationDesc = dto.meetingLocation.description,
                notes = dto.notes,
            )
            runRequestRepo.save(entity)
            // 写两条 events 保留完整审计轨迹：内部中间态 CREATED 不对外暴露
            writeEvent(entity.id, null, RunRequestStatus.CREATED, TriggeredRole.SYSTEM, null, "创建订单")
            writeEvent(entity.id, RunRequestStatus.CREATED, RunRequestStatus.MATCHING, TriggeredRole.SYSTEM, null, "立即匹配")
            entity.toResponse(user, null)
        }
        return if (idempotencyKey != null) idempotencyStore.getOrPut(idempotencyKey, doCreate)
        else doCreate()
    }

    // ── 接单 ─────────────────────────────────────────────────────────────

    fun accept(volunteerId: String, requestId: String): RunRequestResponse {
        log.info("accept: requestId={}, volunteerId={}", requestId, volunteerId)
        val entity = loadRequest(requestId)
        loadAndRequireRole(volunteerId, UserRole.VOLUNTEER, "只有志愿者可以接单")
        // 已有进行中订单（含本人接的或别人接的进行中订单）则禁止再接新单
        requireNoActiveOrderForVolunteer(volunteerId)
        val toStatus = stateMachine.validate(entity.status, RunRequestAction.ACCEPT, TriggeredRole.VOLUNTEER)
        log.info("accept: {} → {}, requestId={}", entity.status, toStatus, requestId)

        entity.status = toStatus
        entity.volunteerId = volunteerId
        entity.matchedAt = Instant.now()
        saveOrConflict(entity, "订单已被其他志愿者接受")

        writeEvent(requestId, RunRequestStatus.MATCHING, toStatus, TriggeredRole.VOLUNTEER, volunteerId)
        pushStatusChange(entity, TriggeredRole.VOLUNTEER)
        return buildResponse(entity)
    }

    // ── 确认出发 ─────────────────────────────────────────────────────────

    fun depart(volunteerId: String, requestId: String): RunRequestResponse {
        log.info("depart: requestId={}, volunteerId={}", requestId, volunteerId)
        val entity = loadRequest(requestId)
        requireAssignedVolunteer(volunteerId, entity)
        val toStatus = stateMachine.validate(entity.status, RunRequestAction.DEPART, TriggeredRole.VOLUNTEER)
        log.info("depart: {} → {}, requestId={}", entity.status, toStatus, requestId)

        val prevStatus = entity.status
        entity.status = toStatus
        entity.departedAt = Instant.now()
        saveOrConflict(entity, "订单状态已被更新")

        writeEvent(requestId, prevStatus, toStatus, TriggeredRole.VOLUNTEER, volunteerId)
        pushStatusChange(entity, TriggeredRole.VOLUNTEER)
        return buildResponse(entity)
    }

    // ── 确认汇合（任一方触发即转移，重复调用幂等返回） ──────────────────────

    fun confirmMet(userId: String, requestId: String): RunRequestResponse {
        log.info("confirmMet: requestId={}, userId={}", requestId, userId)
        val entity = loadRequest(requestId)
        // 幂等路径：此特殊分支仅限 CONFIRM_MET，其他动作的重复调用不走此处
        if (entity.status == RunRequestStatus.MET) {
            log.info("confirmMet: already MET (idempotent), requestId={}", requestId)
            return buildResponse(entity)
        }

        val actorRole = requireParticipant(userId, entity)
        val toStatus = stateMachine.validate(entity.status, RunRequestAction.CONFIRM_MET, actorRole)
        log.info("confirmMet: {} → {}, requestId={}", entity.status, toStatus, requestId)

        val prevStatus = entity.status
        entity.status = toStatus
        entity.metAt = Instant.now()
        saveOrConflict(entity, "订单状态已被更新")

        writeEvent(requestId, prevStatus, toStatus, actorRole, userId)
        pushStatusChange(entity, actorRole)
        return buildResponse(entity)
    }

    // ── 开始跑步（视障确认汇合 或 志愿者主动开始） ────────────────────────

    fun startRun(userId: String, requestId: String): RunRequestResponse {
        log.info("startRun: requestId={}, userId={}", requestId, userId)
        val entity = loadRequest(requestId)
        val actorRole = requireParticipant(userId, entity)
        val toStatus = stateMachine.validate(entity.status, RunRequestAction.START_RUN, actorRole)
        log.info("startRun: {} → {}, requestId={}", entity.status, toStatus, requestId)

        val prevStatus = entity.status
        entity.status = toStatus
        entity.runStartedAt = Instant.now()
        saveOrConflict(entity, "订单状态已被更新")

        writeEvent(requestId, prevStatus, toStatus, actorRole, userId)
        pushStatusChange(entity, actorRole)
        return buildResponse(entity)
    }

    // ── 结束跑步 ─────────────────────────────────────────────────────────

    fun endRun(userId: String, requestId: String, dto: EndRunDto?): RunRequestResponse {
        log.info("endRun: requestId={}, userId={}", requestId, userId)
        val entity = loadRequest(requestId)
        val triggeredRole = requireParticipant(userId, entity)
        val toStatus = stateMachine.validate(entity.status, RunRequestAction.END_RUN, triggeredRole)
        log.info("endRun: {} → {}, requestId={}", entity.status, toStatus, requestId)

        val prevStatus = entity.status
        entity.status = toStatus
        entity.runEndedAt = Instant.now()
        var abnormal = false
        dto?.actualDistanceMeters?.let { dist ->
            if (dist > 50_000) {
                log.warn("endRun: abnormal distance {}m for requestId={}", dist, requestId)
                abnormal = true
            }
            entity.actualDistanceMeters = dist
        }
        dto?.actualDurationSeconds?.let { dur ->
            if (dur > 18_000) {
                log.warn("endRun: abnormal duration {}s for requestId={}", dur, requestId)
                abnormal = true
            }
            entity.actualDurationSeconds = dur
        }
        dto?.avgPaceSeconds?.let { entity.avgPaceSeconds = it }
        entity.isAbnormal = abnormal
        saveOrConflict(entity, "订单状态已被更新")

        writeEvent(requestId, prevStatus, toStatus, triggeredRole, userId)
        pushStatusChange(entity, triggeredRole)
        return buildResponse(entity)
    }

    // ── 志愿者申请结束跑步（不改状态，仅记录事件并推送给视障端） ──────────

    fun requestEndRun(volunteerId: String, requestId: String): RunRequestResponse {
        log.info("requestEndRun: requestId={}, volunteerId={}", requestId, volunteerId)
        val entity = loadRequest(requestId)
        requireAssignedVolunteer(volunteerId, entity)
        if (entity.status != RunRequestStatus.RUNNING)
            throw AppException(
                ErrorCode.INVALID_STATE_TRANSITION,
                "只有跑步中才能申请结束",
                HttpStatus.BAD_REQUEST,
            )

        writeEvent(
            requestId = requestId,
            from = entity.status,
            to = entity.status,
            role = TriggeredRole.VOLUNTEER,
            userId = volunteerId,
            reason = "志愿者申请结束跑步",
        )
        wsHandler.pushEndRunRequested(requestId, entity.blindRunnerId)
        return buildResponse(entity)
    }

    // ── 取消（视障用户专用，从 MATCHING / EN_ROUTE / RUNNING 取消） ─────────

    fun cancel(userId: String, requestId: String, reason: String?): RunRequestResponse {
        log.info("cancel: requestId={}, userId={}", requestId, userId)
        val entity = loadRequest(requestId)
        val actorRole = requireParticipantForCancel(userId, entity)
        val toStatus = stateMachine.validate(entity.status, RunRequestAction.CANCEL, actorRole)
        log.info("cancel: {} → {}, requestId={}", entity.status, toStatus, requestId)

        val prevStatus = entity.status
        entity.status = toStatus
        entity.abortBy = if (actorRole == TriggeredRole.BLIND) AbortBy.BLIND else AbortBy.VOLUNTEER
        entity.abortReason = reason
        saveOrConflict(entity, "订单状态已被更新")

        writeEvent(requestId, prevStatus, toStatus, actorRole, userId, reason)
        pushStatusChange(entity, actorRole)
        return buildResponse(entity)
    }

    // ── 放弃任务（志愿者专用，从 ACCEPTED 放弃，超 3 次直接 ABORTED） ──────

    fun abandon(volunteerId: String, requestId: String, reason: String? = null): RunRequestResponse {
        log.info("abandon: requestId={}, volunteerId={}", requestId, volunteerId)
        val entity = loadRequest(requestId)
        requireAssignedVolunteer(volunteerId, entity)

        val abandonCount = eventRepo.countAbandonsByRequestId(requestId)
        val toStatus = stateMachine.validate(entity.status, RunRequestAction.ABANDON, TriggeredRole.VOLUNTEER, abandonCount)
        log.info("abandon: {} → {} (abandonCount={}), requestId={}", entity.status, toStatus, abandonCount, requestId)

        val prevStatus = entity.status
        val prevVolunteerId = entity.volunteerId
        entity.status = toStatus
        if (toStatus == RunRequestStatus.MATCHING) {
            entity.volunteerId = null
        } else {
            entity.abortBy = AbortBy.VOLUNTEER
            entity.abortReason = reason ?: "志愿者放弃任务（本订单累计${abandonCount + 1}次）"
        }
        saveOrConflict(entity, "订单状态已被更新")

        writeEvent(requestId, prevStatus, toStatus, TriggeredRole.VOLUNTEER, volunteerId, "志愿者放弃")
        pushStatusChange(entity, TriggeredRole.VOLUNTEER, notifyVolunteerId = prevVolunteerId)
        return buildResponse(entity)
    }

    // ── 释放志愿者（视障用户，ACCEPTED → MATCHING，无次数限制） ────────────

    fun releaseVolunteer(blindUserId: String, requestId: String): RunRequestResponse {
        log.info("releaseVolunteer: requestId={}, blindUserId={}", requestId, blindUserId)
        val entity = loadRequest(requestId)
        if (entity.blindRunnerId != blindUserId)
            throw AppException(ErrorCode.FORBIDDEN_ACTION, "只有订单发起人可以更换志愿者", HttpStatus.FORBIDDEN)

        val toStatus = stateMachine.validate(entity.status, RunRequestAction.RELEASE, TriggeredRole.BLIND)
        log.info("releaseVolunteer: {} → {}, requestId={}", entity.status, toStatus, requestId)

        val prevStatus = entity.status
        val prevVolunteerId = entity.volunteerId
        entity.status = toStatus
        entity.volunteerId = null
        entity.matchedAt = null
        saveOrConflict(entity, "订单状态已被更新")

        writeEvent(requestId, prevStatus, toStatus, TriggeredRole.BLIND, blindUserId, "视障用户更换志愿者")
        pushStatusChange(entity, TriggeredRole.BLIND, notifyVolunteerId = prevVolunteerId)
        return buildResponse(entity)
    }

    // ── 紧急求助（任一方，RUNNING → ABORTED） ────────────────────────────

    // 阶段5：emergency 不再转移状态，仅记录事件并通知对方，用户可继续选择结束或中止
    fun emergency(userId: String, requestId: String, dto: EmergencyDto? = null): RunRequestResponse {
        log.info("emergency: requestId={}, userId={}", requestId, userId)
        val entity = loadRequest(requestId)
        val actorRole = requireParticipant(userId, entity)
        if (entity.status != RunRequestStatus.RUNNING)
            throw AppException(ErrorCode.INVALID_STATE_TRANSITION, "紧急求助只能在跑步中触发", HttpStatus.BAD_REQUEST)

        writeEvent(requestId, entity.status, entity.status, actorRole, userId, dto?.reason ?: "紧急求助")
        val otherUserId = if (actorRole == TriggeredRole.BLIND) entity.volunteerId else entity.blindRunnerId
        val recipients = listOfNotNull(otherUserId)
        wsHandler.pushEmergency(
            requestId = requestId,
            triggeredRole = actorRole.name,
            reason = dto?.reason,
            lat = dto?.currentLocation?.lat,
            lng = dto?.currentLocation?.lng,
            recipientIds = recipients,
        )
        return buildResponse(entity)
    }

    // ── 评价（双方都评完 → CLOSED，更新冗余统计字段） ──────────────────────

    fun createReview(reviewerId: String, requestId: String, dto: CreateReviewDto) {
        val entity = loadRequest(requestId)

        // FINISHED 与 CLOSED 都允许补评：CLOSED 是另一方评后自动关单/24h 兜底关单，
        // 仍允许该方在历史页发起补评（影响 rating 累加但不再触发状态推进）
        if (entity.status != RunRequestStatus.FINISHED && entity.status != RunRequestStatus.CLOSED)
            throw AppException(ErrorCode.INVALID_STATE_TRANSITION, "只能在订单完成后评价", HttpStatus.BAD_REQUEST)

        val revieweeId = when (reviewerId) {
            entity.blindRunnerId -> entity.volunteerId
                ?: throw AppException(ErrorCode.FORBIDDEN_ACTION, "订单尚无志愿者", HttpStatus.BAD_REQUEST)
            entity.volunteerId   -> entity.blindRunnerId
            else                 -> throw AppException(ErrorCode.FORBIDDEN_ACTION, "无权对此订单评价", HttpStatus.FORBIDDEN)
        }

        if (reviewRepo.existsByRequestIdAndReviewerId(requestId, reviewerId))
            throw AppException(ErrorCode.ALREADY_REVIEWED, "已经评价过此订单", HttpStatus.BAD_REQUEST)

        reviewRepo.save(
            ReviewEntity(
                requestId = requestId,
                reviewerId = reviewerId,
                revieweeId = revieweeId,
                rating = dto.rating,
                tags = dto.tags,
                comment = dto.comment,
                voiceUrl = dto.voiceUrl,
            )
        )

        // 通知被评价方
        wsHandler.pushReviewReceived(requestId, revieweeId, dto.rating)

        when (entity.status) {
            RunRequestStatus.FINISHED -> {
                // 双方都已评价：触发 FINISHED → CLOSED 并更新冗余统计 + rating
                if (reviewRepo.countByRequestId(requestId) >= 2L) {
                    closeAndUpdateStats(entity)
                }
            }
            RunRequestStatus.CLOSED -> {
                // 补评：订单已被定时器 / 对方提前关单，仅增量累加被评方 rating，
                // 不再走 closeAndUpdateStats（否则会重复累加 totalRuns/totalHoursMinutes）
                accumulateRating(revieweeId, dto.rating)
            }
            else -> Unit
        }
    }

    // ── peer-metrics 转发（志愿者 → 视障端） ─────────────────────────────────

    fun pushPeerMetrics(volunteerId: String, requestId: String, dto: PeerMetricsDto) {
        val entity = loadRequest(requestId)
        requireAssignedVolunteer(volunteerId, entity)
        if (entity.status != RunRequestStatus.RUNNING)
            throw AppException(ErrorCode.INVALID_STATE_TRANSITION, "只有跑步中才能推送实时数据", HttpStatus.BAD_REQUEST)
        wsHandler.pushPeerMetrics(
            requestId = requestId,
            toUserId = entity.blindRunnerId,
            totalDistanceMeters = dto.totalDistanceMeters,
            totalDurationSeconds = dto.totalDurationSeconds,
            currentPaceSeconds = dto.currentPaceSeconds,
            avgPaceSeconds = dto.avgPaceSeconds,
        )
    }

    // ── 获取某订单的评价列表 ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    fun getReviews(requestId: String): List<ReviewResponseDto> =
        reviewRepo.findByRequestId(requestId).map { it.toDto() }

    // ── 查询 ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    fun getById(requestId: String): RunRequestResponse = buildResponse(loadRequest(requestId))

    @Transactional(readOnly = true)
    fun getMyRequests(
        userId: String,
        role: String,
        page: Int,
        statuses: Collection<RunRequestStatus>? = null,
    ): List<RunRequestResponse> {
        val pageable = PageRequest.of(page, 20)
        val normalizedRole = role.uppercase()
        val entities = if (statuses.isNullOrEmpty()) {
            when (normalizedRole) {
                "BLIND"     -> runRequestRepo.findByBlindRunnerIdOrderByCreatedAtDesc(userId, pageable)
                "VOLUNTEER" -> runRequestRepo.findByVolunteerIdOrderByCreatedAtDesc(userId, pageable)
                else        -> throw AppException(ErrorCode.INVALID_PARAM, "role 参数必须为 blind 或 volunteer")
            }
        } else {
            when (normalizedRole) {
                "BLIND"     -> runRequestRepo.findByBlindRunnerIdAndStatusInOrderByCreatedAtDesc(userId, statuses, pageable)
                "VOLUNTEER" -> runRequestRepo.findByVolunteerIdAndStatusInOrderByCreatedAtDesc(userId, statuses, pageable)
                else        -> throw AppException(ErrorCode.INVALID_PARAM, "role 参数必须为 blind 或 volunteer")
            }
        }
        // 批量查"自己已评价"标记，O(N) → O(1) 一次性查出所有该用户已评的 requestId
        val ids = entities.map { it.id }
        val reviewedIds = if (ids.isEmpty()) emptySet()
        else reviewRepo.findReviewedRequestIds(ids, userId).toSet()
        return entities.map { entity ->
            val isCompleted = entity.status == RunRequestStatus.FINISHED ||
                entity.status == RunRequestStatus.CLOSED
            buildResponse(
                entity = entity,
                myReviewSubmitted = if (isCompleted) entity.id in reviewedIds else null,
            )
        }
    }

    @Transactional(readOnly = true)
    fun getActiveRequest(userId: String, role: String): RunRequestResponse? {
        val activeStatuses = RunRequestStatus.values().filter { it.isActive() && it != RunRequestStatus.CREATED }
        val entity = when (role.uppercase()) {
            "BLIND"     -> runRequestRepo.findFirstByBlindRunnerIdAndStatusInOrderByCreatedAtDesc(userId, activeStatuses)
            "VOLUNTEER" -> runRequestRepo.findFirstByVolunteerIdAndStatusInOrderByCreatedAtDesc(userId, activeStatuses)
            else        -> throw AppException(ErrorCode.INVALID_PARAM, "role 参数必须为 blind 或 volunteer")
        }
        return entity?.let { buildResponse(it) }
    }

    @Transactional(readOnly = true)
    fun getAvailable(volunteerId: String, lat: Double, lng: Double, radiusMeters: Double): List<AvailableRequestItemDto> {
        loadAndRequireRole(volunteerId, UserRole.VOLUNTEER, "只有志愿者可以查看可接单列表")

        val matching = runRequestRepo.findByStatus(RunRequestStatus.MATCHING)
        if (matching.isEmpty()) return emptyList()

        val users = userRepo.findAllById(matching.map { it.blindRunnerId }.distinct()).associateBy { it.id }

        return matching.mapNotNull { entity ->
            val dist = approximateDistanceMeters(lat, lng, entity.meetingLat, entity.meetingLng)
            if (dist > radiusMeters) return@mapNotNull null
            val user = users[entity.blindRunnerId] ?: return@mapNotNull null
            entity.toAvailableItem(user, dist.toInt())
        }.sortedBy { it.distanceMeters }.take(10)
    }

    // ── 私有辅助方法 ──────────────────────────────────────────────────────

    private fun loadRequest(id: String): RunRequestEntity =
        runRequestRepo.findById(id).orElseThrow {
            AppException(ErrorCode.REQUEST_NOT_FOUND, "订单不存在", HttpStatus.NOT_FOUND)
        }

    private fun loadUser(id: String): UserEntity =
        userRepo.findById(id).orElseThrow {
            AppException(ErrorCode.USER_NOT_FOUND, "用户不存在", HttpStatus.NOT_FOUND)
        }

    private fun loadAndRequireRole(userId: String, role: UserRole, errMsg: String): UserEntity {
        val user = loadUser(userId)
        if (!user.roles.contains(role.name))
            throw AppException(ErrorCode.FORBIDDEN_ACTION, errMsg, HttpStatus.FORBIDDEN)
        return user
    }

    private fun requireParticipant(userId: String, entity: RunRequestEntity): TriggeredRole {
        log.info("requireParticipant: userId={}, blindRunnerId={}, volunteerId={}", userId, entity.blindRunnerId, entity.volunteerId)
        return when (userId) {
            entity.blindRunnerId -> TriggeredRole.BLIND
            entity.volunteerId   -> TriggeredRole.VOLUNTEER
            else -> throw AppException(ErrorCode.FORBIDDEN_ACTION, "无权操作此订单", HttpStatus.FORBIDDEN)
        }
    }

    /** CANCEL 允许视障用户在所有活跃状态下取消，允许志愿者在 EN_ROUTE/RUNNING 时取消。 */
    private fun requireParticipantForCancel(userId: String, entity: RunRequestEntity): TriggeredRole =
        when {
            userId == entity.blindRunnerId -> TriggeredRole.BLIND
            userId == entity.volunteerId   -> TriggeredRole.VOLUNTEER
            else -> throw AppException(ErrorCode.FORBIDDEN_ACTION, "无权取消此订单", HttpStatus.FORBIDDEN)
        }

    private fun requireAssignedVolunteer(volunteerId: String, entity: RunRequestEntity) {
        if (entity.volunteerId != volunteerId)
            throw AppException(ErrorCode.FORBIDDEN_ACTION, "只有当前接单志愿者可以执行此操作", HttpStatus.FORBIDDEN)
    }

    private fun saveOrConflict(entity: RunRequestEntity, msg: String): RunRequestEntity {
        return try {
            runRequestRepo.saveAndFlush(entity)
        } catch (e: OptimisticLockingFailureException) {
            throw AppException(ErrorCode.REQUEST_ALREADY_MATCHED, msg, HttpStatus.CONFLICT)
        }
    }

    private fun conflict(msg: String): Nothing =
        throw AppException(ErrorCode.REQUEST_ALREADY_MATCHED, msg, HttpStatus.CONFLICT)

    private fun writeEvent(
        requestId: String,
        from: RunRequestStatus?,
        to: RunRequestStatus,
        role: TriggeredRole,
        userId: String?,
        reason: String? = null,
    ) {
        log.debug("event: requestId={}, {}→{}, role={}, userId={}", requestId, from, to, role, userId)
        eventRepo.save(
            RunRequestEventEntity(
                requestId = requestId,
                fromStatus = from?.name,
                toStatus = to.name,
                triggeredRole = role,
                triggeredBy = userId,
                reason = reason,
            )
        )
    }

    private fun buildResponse(
        entity: RunRequestEntity,
        myReviewSubmitted: Boolean? = null,
    ): RunRequestResponse {
        val blindRunner = runCatching { loadUser(entity.blindRunnerId) }.getOrNull()
        val volunteer = entity.volunteerId?.let { runCatching { loadUser(it) }.getOrNull() }
        return entity.toResponse(blindRunner, volunteer, myReviewSubmitted)
    }

    /** 视障端发单前置校验：已有进行中订单则拒绝。 */
    private fun requireNoActiveOrderForBlind(blindUserId: String) {
        val activeStatuses = RunRequestStatus.values().filter { it.isActive() && it != RunRequestStatus.CREATED }
        runRequestRepo.findFirstByBlindRunnerIdAndStatusInOrderByCreatedAtDesc(blindUserId, activeStatuses)?.let {
            throw AppException(
                ErrorCode.HAS_ACTIVE_ORDER,
                "您已有进行中的跑步订单，请先处理或取消后再发起新订单",
                HttpStatus.CONFLICT,
            )
        }
    }

    /** 志愿者接单前置校验：已有进行中订单则拒绝。 */
    private fun requireNoActiveOrderForVolunteer(volunteerId: String) {
        val activeStatuses = RunRequestStatus.values().filter { it.isActive() && it != RunRequestStatus.CREATED }
        runRequestRepo.findFirstByVolunteerIdAndStatusInOrderByCreatedAtDesc(volunteerId, activeStatuses)?.let {
            throw AppException(
                ErrorCode.HAS_ACTIVE_ORDER,
                "您已有进行中的陪跑订单，请先处理后再接新单",
                HttpStatus.CONFLICT,
            )
        }
    }

    /** 补评（订单已 CLOSED）增量累加 rating，不动 totalRuns/totalHoursMinutes 避免重复累加。 */
    private fun accumulateRating(revieweeId: String, rating: Int) {
        val reviewee = loadUser(revieweeId)
        reviewee.ratingSum += rating
        reviewee.ratingCount++
        userRepo.save(reviewee)
    }

    private fun closeAndUpdateStats(entity: RunRequestEntity) {
        entity.status = RunRequestStatus.CLOSED
        entity.closedAt = Instant.now()
        saveOrConflict(entity, "订单关闭时发生冲突")
        writeEvent(entity.id, RunRequestStatus.FINISHED, RunRequestStatus.CLOSED, TriggeredRole.SYSTEM, null)
        pushStatusChange(entity, TriggeredRole.SYSTEM)

        val reviews = reviewRepo.findByRequestId(entity.id)
        val blindRunner = loadUser(entity.blindRunnerId)
        val volunteer = loadUser(entity.volunteerId!!)
        blindRunner.totalRuns++
        volunteer.totalRuns++
        val runMinutes = (entity.actualDurationSeconds ?: 0) / 60
        volunteer.totalHoursMinutes += runMinutes
        blindRunner.totalHoursMinutes += runMinutes
        reviews.forEach { review ->
            val reviewee = if (review.revieweeId == entity.blindRunnerId) blindRunner else volunteer
            reviewee.ratingSum += review.rating
            reviewee.ratingCount++
        }
        userRepo.saveAll(listOf(blindRunner, volunteer))
    }

    private fun ReviewEntity.toDto() = ReviewResponseDto(
        id = id,
        requestId = requestId,
        reviewerId = reviewerId,
        revieweeId = revieweeId,
        rating = rating,
        tags = tags,
        comment = comment,
        voiceUrl = voiceUrl,
        createdAt = createdAt.toEpochMilli(),
    )

    // ── 位置上报（EN_ROUTE / MET / RUNNING 阶段） ─────────────────────────

    fun reportPosition(volunteerId: String, requestId: String, dto: ReportPositionDto) {
        val entity = loadRequest(requestId)
        requireAssignedVolunteer(volunteerId, entity)
        val validStatuses = setOf(RunRequestStatus.EN_ROUTE, RunRequestStatus.MET, RunRequestStatus.RUNNING)
        if (entity.status !in validStatuses)
            throw AppException(ErrorCode.INVALID_STATE_TRANSITION, "当前状态不支持位置上报", HttpStatus.UNPROCESSABLE_ENTITY)
        entity.volunteerLat = dto.lat
        entity.volunteerLng = dto.lng
        entity.volunteerPositionUpdatedAt = Instant.now()
        runRequestRepo.save(entity)
    }

    private fun pushStatusChange(
        entity: RunRequestEntity,
        triggeredRole: TriggeredRole,
        notifyVolunteerId: String? = entity.volunteerId,
    ) {
        val recipients = listOfNotNull(entity.blindRunnerId, notifyVolunteerId)
        wsHandler.pushStatusChanged(entity.id, entity.status, entity.version, recipients, triggeredRole.name)
    }

    private fun approximateDistanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = (lat2 - lat1) * 111000
        val dLng = (lng2 - lng1) * 111000 * cos(Math.toRadians(lat1))
        return sqrt(dLat.pow(2) + dLng.pow(2))
    }
}
