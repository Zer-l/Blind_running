package com.guiderun.app.data.remote

import com.guiderun.app.data.local.UserPreferences
import com.guiderun.app.util.AuthEventBus
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber
import javax.inject.Inject

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
