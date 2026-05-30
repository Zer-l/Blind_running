package com.guiderun.app.data.repository

import android.content.Context
import android.content.Intent
import com.guiderun.app.data.local.UserPreferences
import com.guiderun.app.data.local.dao.UserDao
import com.guiderun.app.data.mapper.toDomain
import com.guiderun.app.data.mapper.toEntity
import com.guiderun.app.R
import com.guiderun.app.data.remote.WebSocketManager
import com.guiderun.app.data.remote.api.AuthApi
import com.guiderun.app.data.remote.dto.ApiResponse
import com.guiderun.app.data.remote.dto.LoginRequestDto
import com.guiderun.app.data.remote.dto.SendSmsRequestDto
import com.guiderun.app.domain.model.LoginResult
import com.guiderun.app.domain.model.ProvisioningStatus
import com.guiderun.app.domain.repository.AuthRepository
import com.guiderun.app.service.BlindRunTrackingService
import com.guiderun.app.service.LocationUpdateService
import com.guiderun.app.service.VolunteerRunTrackingService
import com.guiderun.app.util.runCatchingCancellable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authApi: AuthApi,
    private val userPreferences: UserPreferences,
    private val userDao: UserDao,
    private val webSocketManager: WebSocketManager,
    private val json: Json,
) : AuthRepository {

    override suspend fun sendSms(phone: String): Result<Unit> = runCatchingCancellable {
        val resp = try {
            authApi.sendSms(SendSmsRequestDto(phone))
        } catch (e: HttpException) {
            // 4xx/5xx：优先用服务端错误体里的 message，否则回退友好默认文案
            error(parseServerMessage(e) ?: context.getString(R.string.sms_send_failed_generic))
        }
        if (resp.code != 0) error(resp.message)
    }

    override suspend fun login(phone: String, smsCode: String): Result<LoginResult> = runCatchingCancellable {
        val resp = try {
            authApi.login(LoginRequestDto(phone, smsCode))
        } catch (e: HttpException) {
            // 验证码错误 / 频率限制等业务异常多走 4xx + JSON 体；提取 message 避免暴露 "HTTP 400" 这类技术细节
            error(parseServerMessage(e) ?: context.getString(R.string.login_failed_generic))
        }
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

    override suspend fun logout(): Result<Unit> = runCatchingCancellable {
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

    /**
     * 从 [HttpException] 的 errorBody 中解析服务端返回的 message。
     * 服务端约定错误体形如 `{ "code": x, "message": "...", "data": null }`。
     * 解析失败（非 JSON / 网络中断 / 空体）返回 null，调用方自行兜底友好文案。
     */
    private fun parseServerMessage(e: HttpException): String? = runCatching {
        e.response()?.errorBody()?.string()
            ?.let { json.decodeFromString<ApiResponse<JsonElement>>(it).message }
            ?.takeIf { it.isNotBlank() }
    }.getOrNull()
}
