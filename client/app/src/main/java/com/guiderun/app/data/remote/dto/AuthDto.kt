package com.guiderun.app.data.remote.dto

import kotlinx.serialization.Serializable

/** 发送短信验证码请求体。 */
@Serializable
data class SendSmsRequestDto(val phone: String)

/** 短信登录请求体：手机号 + 验证码（当前 Mock 固定 "123456"）。 */
@Serializable
data class LoginRequestDto(val phone: String, val smsCode: String)

/**
 * 登录响应体。
 *
 * [provisioningStatus] 标识用户资料是否完整（ACTIVE / INCOMPLETE）；
 * 新用户首次登录时为 INCOMPLETE，需引导填写视障/志愿者档案。
 */
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
