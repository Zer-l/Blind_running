package com.guiderun.server.dto

/**
 * 全局 API 响应包装：`code=0` 表示成功，非 0 时 `errorCode` 字符串供客户端做分支处理。
 * 错误响应由 [com.guiderun.server.exception.GlobalExceptionHandler] 构造。
 */
data class ApiResponse<T>(
    val code: Int,
    val message: String,
    val data: T? = null,
    val errorCode: String? = null,
) {
    companion object {
        fun <T> ok(data: T): ApiResponse<T> = ApiResponse(0, "OK", data)
        fun ok(): ApiResponse<Unit> = ApiResponse(0, "OK")
        fun error(httpCode: Int, errorCode: String, message: String): ApiResponse<Nothing> =
            ApiResponse(httpCode, message, null, errorCode)
    }
}
