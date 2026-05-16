package com.guiderun.app.data.remote.api

import com.guiderun.app.data.remote.dto.*
import retrofit2.http.*

interface RunRequestApi {

    @POST("api/v1/run-requests")
    suspend fun createRunRequest(
        @Body dto: CreateRunRequestRequestDto,
        @Header("Idempotency-Key") idempotencyKey: String? = null,
    ): ApiResponse<RunRequestResponseDto>

    @GET("api/v1/run-requests/available")
    suspend fun getAvailable(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("radius") radius: Double,
    ): ApiResponse<ListResponseDto<AvailableRequestItemDto>>

    @GET("api/v1/run-requests/active")
    suspend fun getActive(@Query("role") role: String): ApiResponse<RunRequestResponseDto>

    @GET("api/v1/run-requests/{id}")
    suspend fun getById(@Path("id") id: String): ApiResponse<RunRequestResponseDto>

    @GET("api/v1/run-requests")
    suspend fun getMyRequests(
        @Query("role") role: String,
        @Query("page") page: Int,
        @Query("size") size: Int = 20,
        @Query("status") status: String? = null,
    ): ApiResponse<ListResponseDto<RunRequestResponseDto>>

    @POST("api/v1/run-requests/{id}/accept")
    suspend fun accept(@Path("id") id: String, @Body body: Unit = Unit): ApiResponse<RunRequestResponseDto>

    @POST("api/v1/run-requests/{id}/depart")
    suspend fun depart(@Path("id") id: String, @Body body: Unit = Unit): ApiResponse<RunRequestResponseDto>

    @POST("api/v1/run-requests/{id}/confirm-met")
    suspend fun confirmMet(@Path("id") id: String, @Body body: Unit = Unit): ApiResponse<RunRequestResponseDto>

    @POST("api/v1/run-requests/{id}/start-run")
    suspend fun startRun(@Path("id") id: String, @Body body: Unit = Unit): ApiResponse<RunRequestResponseDto>

    @POST("api/v1/run-requests/{id}/end-run")
    suspend fun endRun(@Path("id") id: String, @Body dto: EndRunRequestDto = EndRunRequestDto()): ApiResponse<RunRequestResponseDto>

    @POST("api/v1/run-requests/{id}/request-end-run")
    suspend fun requestEndRun(@Path("id") id: String, @Body body: Unit = Unit): ApiResponse<RunRequestResponseDto>

    @POST("api/v1/run-requests/{id}/cancel")
    suspend fun cancel(@Path("id") id: String, @Body dto: CancelRequestDto = CancelRequestDto()): ApiResponse<RunRequestResponseDto>

    @POST("api/v1/run-requests/{id}/abandon")
    suspend fun abandon(@Path("id") id: String, @Body body: Unit = Unit): ApiResponse<RunRequestResponseDto>

    @POST("api/v1/run-requests/{id}/emergency")
    suspend fun emergency(
        @Path("id") id: String,
        @Body dto: EmergencyRequestDto? = null,
    ): ApiResponse<RunRequestResponseDto>

    @POST("api/v1/run-requests/{id}/position")
    suspend fun reportPosition(
        @Path("id") id: String,
        @Body dto: ReportPositionRequestDto,
    ): VoidApiResponse

    @POST("api/v1/run-requests/{id}/reviews")
    suspend fun createReview(
        @Path("id") id: String,
        @Body dto: CreateReviewRequestDto,
    ): VoidApiResponse

    @GET("api/v1/run-requests/{id}/reviews")
    suspend fun getReviews(
        @Path("id") id: String,
    ): ApiResponse<ListResponseDto<ReviewResponseDto>>

    @POST("api/v1/run-requests/{id}/tracks")
    suspend fun uploadTracks(
        @Path("id") id: String,
        @Body dto: UploadTracksDto,
    ): ApiResponse<ListResponseDto<RunTrackResponseDto>>

    @GET("api/v1/run-requests/{id}/tracks")
    suspend fun getTracks(
        @Path("id") id: String,
    ): ApiResponse<ListResponseDto<RunTrackResponseDto>>

    @POST("api/v1/run-requests/{id}/peer-metrics")
    suspend fun pushPeerMetrics(
        @Path("id") id: String,
        @Body dto: PeerMetricsDto,
    ): VoidApiResponse
}
