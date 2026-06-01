package com.guiderun.app.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/** 认证相关全局事件。当前仅有 [LoggedOut]（401 响应触发）。 */
sealed interface AuthEvent {
    /** 服务端返回 401，Token 失效，需要重新登录。 */
    data object LoggedOut : AuthEvent
}

/**
 * 全局认证事件总线（Singleton）。
 *
 * 职责：AuthInterceptor（data 层）检测到 401 时，无法直接访问 UI 层。
 * 通过本总线发出 [AuthEvent.LoggedOut] 信号，MainActivity 在 viewModelScope 订阅后执行导航跳转。
 * 这是 data → UI 解耦的标准 SharedFlow 事件模式。
 *
 * extraBufferCapacity=1：允许 tryEmit 无 collect 方也能成功，防止登出信号丢失。
 */
@Singleton
class AuthEventBus @Inject constructor() {
    private val _events = MutableSharedFlow<AuthEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<AuthEvent> = _events.asSharedFlow()

    fun emitLogout() {
        _events.tryEmit(AuthEvent.LoggedOut)
    }
}
