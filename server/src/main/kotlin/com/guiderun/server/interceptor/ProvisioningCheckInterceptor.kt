package com.guiderun.server.interceptor

import com.guiderun.server.common.ProvisioningStatus
import com.guiderun.server.exception.AppException
import com.guiderun.server.exception.ErrorCode
import com.guiderun.server.repository.UserJpaRepository
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.util.AntPathMatcher
import org.springframework.web.servlet.HandlerInterceptor

/**
 * 注册流程守卫拦截器：账户 [ProvisioningStatus.PENDING_ROLE] 时禁止访问业务接口。
 *
 * 白名单放行 `/auth/` 前缀（登录/刷新）+ `/users/me` 前缀（PATCH 设置角色完成开通）+ `/ws/` 前缀。
 * 命中其他业务路径且账号仍未选角色时抛 [ErrorCode.PROVISIONING_INCOMPLETE]（403），
 * 客户端据此跳转到角色选择页。
 */
@Component
class ProvisioningCheckInterceptor(
    private val userRepo: UserJpaRepository,
) : HandlerInterceptor {

    private val matcher = AntPathMatcher()

    companion object {
        // PENDING_ROLE 用户可以访问的路径白名单
        // 规则：auth 全放行；users/me 及其子路径放行（含 PATCH /me 设置角色）
        private val WHITELIST = listOf(
            "/api/v1/auth/**",
            "/api/v1/users/me",
            "/api/v1/users/me/**",
            "/ws/**",
        )
    }

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        val path = request.requestURI
        if (WHITELIST.any { matcher.match(it, path) }) return true

        val auth = SecurityContextHolder.getContext().authentication
        if (auth == null || !auth.isAuthenticated || auth is AnonymousAuthenticationToken) return true

        val userId = auth.principal as? String ?: return true

        val user = userRepo.findById(userId).orElse(null) ?: return true
        if (user.provisioningStatus == ProvisioningStatus.PENDING_ROLE) {
            throw AppException(
                errorCode = ErrorCode.PROVISIONING_INCOMPLETE,
                message = "账号未完成注册流程，请先选择角色",
                httpStatus = HttpStatus.FORBIDDEN,
            )
        }

        return true
    }
}
