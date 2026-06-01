package com.guiderun.server.exception

import org.springframework.http.HttpStatus

/**
 * 业务异常：携带 [errorCode] 字符串和 [httpStatus]，由 [GlobalExceptionHandler] 转 [ApiResponse.error]。
 * 客户端可凭 errorCode 做分支处理（如 PROVISIONING_INCOMPLETE → 跳角色选择页）。
 */
class AppException(
    val errorCode: String,
    override val message: String,
    val httpStatus: HttpStatus = HttpStatus.BAD_REQUEST,
) : RuntimeException(message)

/** 业务错误码常量集合，保持字符串形式便于跨语言对齐。 */
object ErrorCode {
    // Auth
    const val INVALID_SMS_CODE = "INVALID_SMS_CODE"
    const val USER_NOT_FOUND   = "USER_NOT_FOUND"
    const val INVALID_TOKEN    = "INVALID_TOKEN"
    const val TOKEN_EXPIRED    = "TOKEN_EXPIRED"
    const val FORBIDDEN        = "FORBIDDEN"
    // Run Request
    const val INVALID_STATE_TRANSITION = "INVALID_STATE_TRANSITION"
    const val FORBIDDEN_ACTION         = "FORBIDDEN_ACTION"
    const val REQUEST_NOT_FOUND        = "REQUEST_NOT_FOUND"
    const val REQUEST_ALREADY_MATCHED  = "REQUEST_ALREADY_MATCHED"
    const val ALREADY_REVIEWED         = "ALREADY_REVIEWED"
    const val INVALID_PARAM            = "INVALID_PARAM"
    const val PROVISIONING_INCOMPLETE  = "PROVISIONING_INCOMPLETE"
    /** 已有进行中订单时禁止重复发单 / 接单 */
    const val HAS_ACTIVE_ORDER         = "HAS_ACTIVE_ORDER"
}
