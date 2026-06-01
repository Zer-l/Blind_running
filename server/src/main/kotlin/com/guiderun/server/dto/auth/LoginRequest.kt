package com.guiderun.server.dto.auth

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** 登录请求体：手机号 + 6 位短信验证码（Mock 模式固定 123456）。 */
data class LoginRequest(
    @field:NotBlank(message = "手机号不能为空")
    val phone: String,

    @field:NotBlank(message = "验证码不能为空")
    @field:Size(min = 6, max = 6, message = "验证码必须为6位")
    val smsCode: String,
)
