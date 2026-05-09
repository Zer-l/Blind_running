package com.guiderun.server.exception

import org.springframework.http.HttpStatus

class AppException(
    val errorCode: String,
    override val message: String,
    val httpStatus: HttpStatus = HttpStatus.BAD_REQUEST,
) : RuntimeException(message)

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
}
