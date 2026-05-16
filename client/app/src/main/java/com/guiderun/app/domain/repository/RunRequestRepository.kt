package com.guiderun.app.domain.repository

import com.guiderun.app.domain.model.AvailableRunRequest
import com.guiderun.app.domain.model.CreateReviewParams
import com.guiderun.app.domain.model.CreateRunRequestParams
import com.guiderun.app.domain.model.Review
import com.guiderun.app.domain.model.RunRequest
import com.guiderun.app.domain.model.RunTrack
import com.guiderun.app.domain.model.TrackPoint
import kotlinx.coroutines.flow.StateFlow

interface RunRequestRepository {

    /** 当前用户的活跃订单（订单进入活跃态时被写入，进入终态时清空）。 */
    val activeRequest: StateFlow<RunRequest?>

    /** 从服务端拉取一次活跃订单并刷新 [activeRequest]；失败时不修改本地状态。 */
    suspend fun refreshActiveRequest(role: String): Result<RunRequest?>

    suspend fun createRunRequest(params: CreateRunRequestParams, idempotencyKey: String? = null): Result<RunRequest>

    suspend fun getRunRequest(id: String): Result<RunRequest>

    suspend fun getMyRequests(role: String, page: Int = 0): Result<List<RunRequest>>

    suspend fun getAvailableRequests(lat: Double, lng: Double, radiusMeters: Double = 3000.0): Result<List<AvailableRunRequest>>

    suspend fun accept(id: String): Result<RunRequest>

    suspend fun depart(id: String): Result<RunRequest>

    suspend fun confirmMet(id: String): Result<RunRequest>

    suspend fun startRun(id: String): Result<RunRequest>

    suspend fun endRun(id: String, actualDistanceMeters: Int? = null, actualDurationSeconds: Int? = null, avgPaceSeconds: Int? = null): Result<RunRequest>

    /** 志愿者申请结束跑步：服务端不改状态，仅推送 WS 给视障端等待确认。 */
    suspend fun requestEndRun(id: String): Result<RunRequest>

    suspend fun cancel(id: String, reason: String? = null): Result<RunRequest>

    suspend fun abandon(id: String): Result<RunRequest>

    suspend fun emergency(id: String, reason: String? = null, lat: Double? = null, lng: Double? = null): Result<RunRequest>

    suspend fun reportPosition(id: String, lat: Double, lng: Double): Result<Unit>

    suspend fun createReview(requestId: String, params: CreateReviewParams): Result<Unit>

    suspend fun getReviews(requestId: String): Result<List<Review>>

    suspend fun uploadTracks(requestId: String, role: String, points: List<TrackPoint>): Result<List<RunTrack>>

    suspend fun getTracks(requestId: String): Result<List<RunTrack>>

    suspend fun pushPeerMetrics(
        requestId: String,
        totalDistanceMeters: Int,
        totalDurationSeconds: Int,
        currentPaceSeconds: Int? = null,
        avgPaceSeconds: Int? = null,
    ): Result<Unit>
}
