package com.guiderun.app.data.remote

import com.guiderun.app.data.local.UserPreferences
import com.guiderun.app.util.AuthEventBus
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber
import javax.inject.Inject

/**
 * OkHttp 拦截器：统一注入 Bearer Token，并处理 401 自动登出。
 *
 * 注意：[runBlocking] 在 OkHttp 的 IO 线程上执行，读 DataStore 是同步阻塞，
 * 但持续时间极短（本地 protobuf 文件读），不会阻塞主线程。
 * 401 时调用 [com.guiderun.app.util.AuthEventBus.emitLogout] 发出全局登出信号，
 * MainActivity 订阅后跳转到 LoginScreen 并清空返回栈。
 */
class AuthInterceptor @Inject constructor(
    private val userPreferences: UserPreferences,
    private val authEventBus: AuthEventBus,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { userPreferences.getAccessToken() }

        val request = if (token != null) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }

        val response = chain.proceed(request)

        if (response.code == 401) {
            Timber.w("401 Unauthorized — clearing session")
            runBlocking { userPreferences.clearAll() }
            authEventBus.emitLogout()
        }

        return response
    }
}
