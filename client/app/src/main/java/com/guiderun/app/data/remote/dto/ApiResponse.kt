package com.guiderun.app.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * 服务端统一响应信封。
 *
 * code=0 为成功，非 0 为业务错误（如"验证码错误"）。
 * Repository 实现层检查 code 后决定是 Result.success(data) 还是抛出 DomainException。
 * errorCode 是机器可读的错误标识，供未来多语言/特定错误处理使用，当前仅记录日志。
 */
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
