package com.guiderun.server.dto

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
