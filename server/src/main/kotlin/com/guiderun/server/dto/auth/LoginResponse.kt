package com.guiderun.server.dto.auth

import com.guiderun.server.dto.user.UserDto

data class LoginResponse(
    val accessToken: String,
    val refreshToken: String,
    val user: UserDto,
    val isNewUser: Boolean,
    val provisioningStatus: String,
)
