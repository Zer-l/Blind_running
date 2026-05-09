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
