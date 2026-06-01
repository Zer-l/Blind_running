package com.guiderun.app.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * 紧急联系人 DTO，用于请求体和响应体的双向序列化。
 * 服务端以数组有序存储，客户端通过 index 定位进行 PATCH/DELETE。
 */
@Serializable
data class EmergencyContactDto(
    val name: String,
    val phone: String,
    val relationship: String,
)
