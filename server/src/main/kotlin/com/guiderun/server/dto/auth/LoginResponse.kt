package com.guiderun.server.dto.auth

import com.guiderun.server.dto.user.UserDto

/**
 * 登录响应：accessToken + refreshToken + 用户信息 + 注册流程进度。
 * `provisioningStatus = PENDING_ROLE` 时客户端应跳转角色选择页。
 */
data class LoginResponse(
    val accessToken: String,
    val refreshToken: String,
    val user: UserDto,
    val isNewUser: Boolean,
    val provisioningStatus: String,
)
