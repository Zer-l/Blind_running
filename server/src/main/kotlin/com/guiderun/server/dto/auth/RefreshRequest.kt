package com.guiderun.server.dto.auth

import jakarta.validation.constraints.NotBlank

data class RefreshRequest(
    @field:NotBlank(message = "刷新令牌不能为空")
    val refreshToken: String,
)
