package com.guiderun.app.domain.exception

sealed class DomainException(message: String, cause: Throwable? = null) : Exception(message, cause)

class InvalidStateTransitionException(message: String) : DomainException(message)
class ForbiddenActionException(message: String) : DomainException(message)
class RequestConflictException(message: String) : DomainException(message)
class RequestNotFoundException : DomainException("订单不存在")
class AlreadyReviewedException : DomainException("已经评价过此订单")
class ProvisioningIncompleteException : DomainException("账号未完成注册，请先选择角色")
class NetworkException(message: String, cause: Throwable? = null) : DomainException(message, cause)
class UnknownApiException(val code: Int, message: String) : DomainException(message)
class LocationUnavailableException : DomainException("无法获取当前位置，请检查位置权限")
