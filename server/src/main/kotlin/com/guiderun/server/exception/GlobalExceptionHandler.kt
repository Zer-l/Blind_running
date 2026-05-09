package com.guiderun.server.exception

import com.guiderun.server.dto.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import org.hibernate.StaleObjectStateException
import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.transaction.TransactionSystemException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.NoHandlerFoundException

@RestControllerAdvice
class GlobalExceptionHandler(private val env: Environment) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val isDevProfile: Boolean
        get() = env.activeProfiles.contains("dev")

    @ExceptionHandler(AppException::class)
    fun handleApp(e: AppException, request: HttpServletRequest): ResponseEntity<ApiResponse<Nothing>> {
        log.warn("AppException at {} {}: [{}] {}", request.method, request.requestURI, e.errorCode, e.message)
        return ResponseEntity.status(e.httpStatus)
            .body(ApiResponse.error(e.httpStatus.value(), e.errorCode, e.message))
    }

    @ExceptionHandler(OptimisticLockingFailureException::class)
    fun handleOptimisticLock(e: OptimisticLockingFailureException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiResponse.error(409, ErrorCode.REQUEST_ALREADY_MATCHED, "数据已被其他请求修改，请刷新后重试"))

    // Hibernate 有时直接抛 StaleObjectStateException 而不经过 Spring 的异常翻译层
    @ExceptionHandler(StaleObjectStateException::class)
    fun handleStaleObjectState(ex: StaleObjectStateException, request: HttpServletRequest): ResponseEntity<ApiResponse<Nothing>> {
        log.warn("Optimistic lock (StaleObject) at {} {}", request.method, request.requestURI)
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiResponse.error(409, ErrorCode.REQUEST_ALREADY_MATCHED, "资源已被其他操作修改，请重试"))
    }

    // @Transactional 提交/回滚阶段失败时，Spring 抛 TransactionSystemException 包装原始异常
    @ExceptionHandler(TransactionSystemException::class)
    fun handleTransactionSystem(ex: TransactionSystemException, request: HttpServletRequest): ResponseEntity<*> {
        val rootCause = unwrapCause(ex)
        log.warn(
            "TransactionSystemException at {} {}, rootCause={}: {}",
            request.method, request.requestURI,
            rootCause.javaClass.simpleName, rootCause.message,
        )
        return when (rootCause) {
            is AppException -> ResponseEntity.status(rootCause.httpStatus)
                .body(ApiResponse.error(rootCause.httpStatus.value(), rootCause.errorCode, rootCause.message))
            is OptimisticLockingFailureException, is StaleObjectStateException ->
                ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error(409, ErrorCode.REQUEST_ALREADY_MATCHED, "数据已被其他请求修改，请刷新后重试"))
            else -> handleGeneral(ex, request)
        }
    }

    // 数据库约束违反（FK、UNIQUE、NOT NULL 等）
    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrity(ex: DataIntegrityViolationException, request: HttpServletRequest): ResponseEntity<ApiResponse<Nothing>> {
        val specificMsg = unwrapCause(ex).message ?: ex.message
        log.error("Data integrity violation at {} {}: {}", request.method, request.requestURI, specificMsg, ex)
        val devMessage = if (isDevProfile) "DB约束违反: $specificMsg" else "数据冲突"
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(400, "DATA_INTEGRITY_VIOLATION", devMessage))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Nothing>> {
        val msg = e.bindingResult.allErrors.firstOrNull()?.defaultMessage ?: "参数校验失败"
        return ResponseEntity.badRequest()
            .body(ApiResponse.error(400, "VALIDATION_ERROR", msg))
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleHttpMessageNotReadable(ex: HttpMessageNotReadableException, request: HttpServletRequest): ResponseEntity<ApiResponse<Nothing>> {
        log.warn("Bad request body at {} {}: {}", request.method, request.requestURI, ex.message)
        val devMessage = if (isDevProfile) "请求体格式错误: ${ex.message}" else "请求参数错误"
        return ResponseEntity.badRequest()
            .body(ApiResponse.error(400, "INVALID_REQUEST_BODY", devMessage))
    }

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingParam(ex: MissingServletRequestParameterException, request: HttpServletRequest): ResponseEntity<ApiResponse<Nothing>> {
        log.warn("Missing param at {} {}: {}", request.method, request.requestURI, ex.parameterName)
        return ResponseEntity.badRequest()
            .body(ApiResponse.error(400, "MISSING_PARAMETER", "缺少必要参数: ${ex.parameterName}"))
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(ex: MethodArgumentTypeMismatchException, request: HttpServletRequest): ResponseEntity<ApiResponse<Nothing>> {
        log.warn("Type mismatch at {} {}: param={}, value={}", request.method, request.requestURI, ex.name, ex.value)
        val devMessage = if (isDevProfile) "参数 '${ex.name}' 类型错误: ${ex.value}" else "请求参数类型错误"
        return ResponseEntity.badRequest()
            .body(ApiResponse.error(400, "TYPE_MISMATCH", devMessage))
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotSupported(ex: HttpRequestMethodNotSupportedException, request: HttpServletRequest): ResponseEntity<ApiResponse<Nothing>> {
        log.warn("Method not supported at {} {}", request.method, request.requestURI)
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
            .body(ApiResponse.error(405, "METHOD_NOT_ALLOWED", "不支持 ${ex.method} 请求"))
    }

    @ExceptionHandler(NoHandlerFoundException::class)
    fun handleNoHandler(ex: NoHandlerFoundException, request: HttpServletRequest): ResponseEntity<ApiResponse<Nothing>> {
        log.warn("No handler found at {} {}", request.method, request.requestURI)
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(404, "PATH_NOT_FOUND", "接口不存在: ${ex.requestURL}"))
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneral(ex: Exception, request: HttpServletRequest): ResponseEntity<ApiResponse<Nothing>> {
        log.error("Unhandled exception at {} {}", request.method, request.requestURI, ex)
        val devMessage = if (isDevProfile) "${ex.javaClass.simpleName}: ${ex.message}" else "服务器内部错误"
        return ResponseEntity.internalServerError()
            .body(ApiResponse.error(500, "INTERNAL_ERROR", devMessage))
    }

    private fun unwrapCause(ex: Throwable): Throwable {
        var cause: Throwable = ex
        val seen = HashSet<Throwable>()
        while (cause.cause != null && seen.add(cause)) cause = cause.cause!!
        return cause
    }
}
