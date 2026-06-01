package com.guiderun.server.dto.auth

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

/** 发码请求：手机号需符合大陆 11 位号段格式。 */
data class SendSmsRequest(
    @field:NotBlank(message = "手机号不能为空")
    @field:Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    val phone: String,
)
