package com.guiderun.app.domain.exception

/**
 * 业务异常基类（Domain 层）。
 *
 * 所有从 Repository 向上抛出的异常均继承此类，使 ViewModel 可以用
 * `is DomainException` 统一捕获业务错误，与底层 IOException / 未知异常区分开。
 * cause 保留原始异常链，便于日志追踪。
 */
sealed class DomainException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** 非法状态机跳转（如在 RUNNING 状态尝试 accept）。 */
class InvalidStateTransitionException(message: String) : DomainException(message)

/** 当前用户无权执行该操作（对应 HTTP 403）。 */
class ForbiddenActionException(message: String) : DomainException(message)

/** 幂等创建冲突：相同 idempotencyKey 已存在（对应 HTTP 409）。 */
class RequestConflictException(message: String) : DomainException(message)

/** 订单不存在（对应 HTTP 404）。 */
class RequestNotFoundException : DomainException("订单不存在")

/** 用户已对该订单评价过，禁止重复提交。 */
class AlreadyReviewedException : DomainException("已经评价过此订单")

/** 账号已登录但未完成角色选择（provisioning 状态为 PENDING_ROLE），需引导至角色选择页。 */
class ProvisioningIncompleteException : DomainException("账号未完成注册，请先选择角色")

/** 网络层异常（断网/超时/连接失败），包装 IOException 等底层错误向上透传。 */
class NetworkException(message: String, cause: Throwable? = null) : DomainException(message, cause)

/** 服务端返回了未知错误码（code ≠ 0 且无法映射到具体业务异常），保留 code 供日志上报。 */
class UnknownApiException(val code: Int, message: String) : DomainException(message)

/** 定位服务不可用（权限被拒或 GPS 超时），需引导用户检查权限设置。 */
class LocationUnavailableException : DomainException("无法获取当前位置，请检查位置权限")
