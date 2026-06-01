package com.guiderun.app.data.remote.api

import com.guiderun.app.data.remote.dto.ApiResponse
import com.guiderun.app.data.remote.dto.LoginRequestDto
import com.guiderun.app.data.remote.dto.LoginResponseDto
import com.guiderun.app.data.remote.dto.RefreshRequestDto
import com.guiderun.app.data.remote.dto.RefreshResponseDto
import com.guiderun.app.data.remote.dto.SendSmsRequestDto
import com.guiderun.app.data.remote.dto.VoidApiResponse
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * 认证相关 Retrofit 接口。
 *
 * 所有方法均为 suspend，由协程在 IO 线程执行，不阻塞调用方线程。
 * 登录成功后需将 accessToken 持久化并传给 [com.guiderun.app.data.remote.WebSocketManager.connect]，
 * 保证 WebSocket 与 HTTP 使用同一会话 Token。
 */
interface AuthApi {
    @POST("api/v1/auth/send-sms")
    suspend fun sendSms(@Body request: SendSmsRequestDto): VoidApiResponse

    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequestDto): ApiResponse<LoginResponseDto>

    @POST("api/v1/auth/refresh")
    suspend fun refresh(@Body request: RefreshRequestDto): ApiResponse<RefreshResponseDto>

    @POST("api/v1/auth/logout")
    suspend fun logout(): VoidApiResponse
}
