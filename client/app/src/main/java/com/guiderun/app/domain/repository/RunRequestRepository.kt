package com.guiderun.app.domain.repository

import com.guiderun.app.domain.model.AvailableRunRequest
import com.guiderun.app.domain.model.CreateReviewParams
import com.guiderun.app.domain.model.CreateRunRequestParams
import com.guiderun.app.domain.model.Review
import com.guiderun.app.domain.model.RunRequest
import com.guiderun.app.domain.model.RunTrack
import com.guiderun.app.domain.model.TrackPoint
import com.guiderun.app.service.VoiceCallInfo

interface RunRequestRepository {

    suspend fun createRunRequest(params: CreateRunRequestParams, idempotencyKey: String? = null): Result<RunRequest>

    suspend fun getRunRequest(id: String): Result<RunRequest>

    suspend fun getMyRequests(role: String, page: Int = 0): Result<List<RunRequest>>

    suspend fun getAvailableRequests(lat: Double, lng: Double, radiusMeters: Double = 3000.0): Result<List<AvailableRunRequest>>

    suspend fun accept(id: String): Result<RunRequest>

    suspend fun releaseVolunteer(id: String): Result<RunRequest>

    suspend fun depart(id: String): Result<RunRequest>

    suspend fun confirmMet(id: String): Result<RunRequest>

    suspend fun startRun(id: String): Result<RunRequest>

    suspend fun endRun(id: String, actualDistanceMeters: Int? = null, actualDurationSeconds: Int? = null, avgPaceSeconds: Int? = null): Result<RunRequest>

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

    suspend fun initiateVoiceCall(requestId: String): Result<VoiceCallInfo>

    suspend fun endVoiceCall(requestId: String): Result<Unit>
}
