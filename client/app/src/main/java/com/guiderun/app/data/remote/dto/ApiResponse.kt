package com.guiderun.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse<T>(
    val code: Int,
    val message: String,
    val data: T? = null,
    val errorCode: String? = null,
)

// 用于无返回值的接口（避免 Unit 序列化问题）
@Serializable
data class VoidApiResponse(
    val code: Int,
    val message: String,
)
