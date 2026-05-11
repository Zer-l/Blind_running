package com.guiderun.app.domain.usecase

import com.guiderun.app.data.local.UserPreferences
import com.guiderun.app.domain.model.RunRequest
import com.guiderun.app.domain.repository.RunRequestRepository
import javax.inject.Inject

sealed interface ActiveRecovery {
    data object None : ActiveRecovery
    data class Resume(val request: RunRequest, val role: String) : ActiveRecovery
}

/**
 * 冷启动 / 重新进入主入口时，决定是否需要把用户路由回进行中的订单页面。
 *
 * 优先信任本地保存的 ACTIVE_REQUEST_ID（最快），若拉取失败或本地无 id 则回退到 /active 端点。
 */
class RecoverActiveRequestUseCase @Inject constructor(
    private val repository: RunRequestRepository,
    private val userPreferences: UserPreferences,
) {
    suspend operator fun invoke(): ActiveRecovery {
        val activeRole = userPreferences.getActiveRole() ?: return ActiveRecovery.None
        val serverRole = mapToServerRole(activeRole) ?: return ActiveRecovery.None
        val request = repository.refreshActiveRequest(serverRole).getOrNull() ?: return ActiveRecovery.None
        return ActiveRecovery.Resume(request, activeRole)
    }

    /** UserRole.name (BLIND_RUNNER/VOLUNTEER) → 服务端 role 参数 (BLIND/VOLUNTEER)。 */
    private fun mapToServerRole(activeRole: String): String? = when (activeRole) {
        "BLIND_RUNNER" -> "BLIND"
        "VOLUNTEER"    -> "VOLUNTEER"
        else           -> null
    }
}
