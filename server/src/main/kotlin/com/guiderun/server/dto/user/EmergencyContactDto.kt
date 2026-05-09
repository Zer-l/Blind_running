package com.guiderun.server.dto.user

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class EmergencyContactDto(
    @field:NotBlank(message = "姓名不能为空")
    @field:Size(max = 50, message = "姓名最多50个字符")
    val name: String,
    @field:NotBlank(message = "手机号不能为空")
    @field:Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    val phone: String,
    @field:NotBlank(message = "关系不能为空")
    @field:Size(max = 20, message = "关系最多20个字符")
    val relationship: String,
)
