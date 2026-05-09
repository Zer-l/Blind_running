package com.guiderun.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class SendSmsRequestDto(val phone: String)

@Serializable
data class LoginRequestDto(val phone: String, val smsCode: String)

@Serializable
data class LoginResponseDto(
    val accessToken: String,
    val refreshToken: String,
    val user: UserDto,
    val isNewUser: Boolean,
    val provisioningStatus: String,
)

@Serializable
data class RefreshRequestDto(val refreshToken: String)

@Serializable
data class RefreshResponseDto(val accessToken: String)
