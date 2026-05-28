package com.guiderun.app.data.repository

import android.content.Context
import android.content.Intent
import com.guiderun.app.data.local.UserPreferences
import com.guiderun.app.data.local.dao.UserDao
import com.guiderun.app.data.mapper.toDomain
import com.guiderun.app.data.mapper.toEntity
import com.guiderun.app.data.remote.WebSocketManager
import com.guiderun.app.data.remote.api.AuthApi
import com.guiderun.app.data.remote.dto.LoginRequestDto
import com.guiderun.app.data.remote.dto.SendSmsRequestDto
import com.guiderun.app.domain.model.LoginResult
import com.guiderun.app.domain.model.ProvisioningStatus
import com.guiderun.app.domain.repository.AuthRepository
import com.guiderun.app.service.BlindRunTrackingService
import com.guiderun.app.service.LocationUpdateService
import com.guiderun.app.service.VolunteerRunTrackingService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authApi: AuthApi,
    private val userPreferences: UserPreferences,
    private val userDao: UserDao,
    private val webSocketManager: WebSocketManager,
) : AuthRepository {

    override suspend fun sendSms(phone: String): Result<Unit> = runCatching {
        val resp = authApi.sendSms(SendSmsRequestDto(phone))
        if (resp.code != 0) error(resp.message)
    }

    override suspend fun login(phone: String, smsCode: String): Result<LoginResult> = runCatching {
        val resp = authApi.login(LoginRequestDto(phone, smsCode))
        if (resp.code != 0) error(resp.message)
        val data = requireNotNull(resp.data)

        userPreferences.saveTokens(data.accessToken, data.refreshToken)

        val user = data.user.toDomain()
        val activeRole = user.roles.firstOrNull()?.name ?: ""
        userPreferences.saveUserSession(user.id, activeRole)
        userDao.upsert(user.toEntity())

        // 登录成功后立刻连接 WebSocket，保证后续订单状态变化能实时同步到所有页面
        webSocketManager.connect(data.accessToken)

        val provisioningStatus = runCatching {
            ProvisioningStatus.valueOf(data.provisioningStatus)
        }.getOrDefault(ProvisioningStatus.ACTIVE)

        LoginResult(
            user = user,
            isNewUser = data.isNewUser,
            provisioningStatus = provisioningStatus,
        )
    }

    override suspend fun logout(): Result<Unit> = runCatching {
        runCatching { authApi.logout() } // best-effort
        // 主动停掉所有前台 service，避免登出后孤立运行（GPS 持续耗电 + 后台时长 tick 写入旧用户的 DB 行）。
        // 终态分支（FINISHED/ABORTED/endRun）已经显式 stop 各 service，但用户跑步中点 logout 时
        // 不走任何终态分支，需要在这里兜底。
        stopAllTrackingServices()
        webSocketManager.disconnect()
        userPreferences.clearAll()
        userDao.deleteAll()
    }

    private fun stopAllTrackingServices() {
        runCatching { context.stopService(Intent(context, BlindRunTrackingService::class.java)) }
        runCatching { context.stopService(Intent(context, VolunteerRunTrackingService::class.java)) }
        runCatching { context.stopService(Intent(context, LocationUpdateService::class.java)) }
    }
}
