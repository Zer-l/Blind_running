package com.guiderun.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guiderun.app.data.local.UserPreferences
import com.guiderun.app.domain.model.RunRequest
import com.guiderun.app.domain.model.UserRole
import com.guiderun.app.domain.repository.RunRequestRepository
import com.guiderun.app.util.AuthEvent
import com.guiderun.app.util.AuthEventBus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * MainActivity 启动目标。由 token + activeRole 推导：
 * - 无 token → [Login]
 * - 有 token + 视障角色 → [BlindHome]（MainActivity 立即启动 BlindActivity 并 finish 自身，避免 Compose 跳板闪屏）
 * - 有 token + 其他 → [VolunteerHome]（默认志愿者侧 Compose 首页）
 *
 * 通过 [MainViewModel.reresolveAfterLogin] 在登录/角色选择完成后强制重算。
 */
sealed interface StartTarget {
    data object Login : StartTarget
    data object VolunteerHome : StartTarget
    data object BlindHome : StartTarget
}

/**
 * MainActivity 的 ViewModel，负责应用启动路由决策。
 *
 * 设计意图：
 * - startTarget 在 init 中异步解析（读 DataStore），解析完成前 UI 显示 Loading 不抖动
 * - 视障角色（BLIND_RUNNER）走 BlindActivity（XML Fragment），避免 Compose NavHost 闪屏后再 launch
 * - authEvents 订阅 AuthEventBus：AuthInterceptor 拦截到 401 时广播登出信号，MainActivity 响应后清栈跳登录
 * - themeId 驱动 Compose MaterialTheme 动态切换（志愿者端主题，4 套预设）
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val runRequestRepository: RunRequestRepository,
    authEventBus: AuthEventBus,
) : ViewModel() {

    val themeId: StateFlow<String> = userPreferences.observeThemeId()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "orange")

    private val _startTarget = MutableStateFlow<StartTarget?>(null)
    val startTarget: StateFlow<StartTarget?> = _startTarget.asStateFlow()

    val authEvents: SharedFlow<AuthEvent> = authEventBus.events

    /** 当前 ACTIVE_ROLE 的快照，供路由时同步读取；由 [resolveTarget] 更新。 */
    @Volatile
    private var cachedRole: UserRole? = null

    init {
        viewModelScope.launch {
            _startTarget.value = resolveTarget()
            // 冷启动只刷新 Repository.activeRequest（让首页横幅有数据展示），不主动跳转流程页。
            // 用户从首页横幅点击 onResumeActiveOrder 才会路由，符合主流 App 订单中心体验。
            refreshActiveRequest()
        }
    }

    /**
     * 登录 / 角色选择完成后调用：重新解析 token + role 并更新 [startTarget]。
     * 返回新解析的目标供调用方同步分派（避免 Flow 抢跑延迟）。
     */
    suspend fun reresolveAfterLogin(): StartTarget {
        val target = resolveTarget()
        _startTarget.value = target
        return target
    }

    private suspend fun resolveTarget(): StartTarget {
        // token 解密失败已在 UserPreferences 层兜底为 null；非 null 即"真正登录态"
        userPreferences.getAccessToken() ?: return StartTarget.Login
        val role = userPreferences.getActiveRole()
            ?.let { runCatching { UserRole.valueOf(it) }.getOrNull() }
        cachedRole = role
        return when (role) {
            UserRole.BLIND_RUNNER -> StartTarget.BlindHome
            // VOLUNTEER 或 null（已登录但 role 异常）兜底走志愿者侧 Compose 首页
            else -> StartTarget.VolunteerHome
        }
    }

    suspend fun refreshActiveRequest() {
        val activeRole = userPreferences.getActiveRole() ?: return
        val serverRole = when (activeRole) {
            "BLIND_RUNNER" -> "BLIND"
            "VOLUNTEER"    -> "VOLUNTEER"
            else           -> return
        }
        runRequestRepository.refreshActiveRequest(serverRole)
    }

    fun activeRequestNow(): RunRequest? = runRequestRepository.activeRequest.value

    fun currentRole(): UserRole? = cachedRole
}
