package com.guiderun.server.dto.auth

import jakarta.validation.constraints.NotBlank

/** 刷新 accessToken 请求体，仅需 refreshToken 明文。 */
data class RefreshRequest(
    @field:NotBlank(message = "刷新令牌不能为空")
    val refreshToken: String,
)
