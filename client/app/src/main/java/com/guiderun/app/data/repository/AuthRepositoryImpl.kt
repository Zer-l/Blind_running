package com.guiderun.app.data.repository

import com.guiderun.app.data.local.UserPreferences
import com.guiderun.app.data.local.dao.UserDao
import com.guiderun.app.data.mapper.toDomain
import com.guiderun.app.data.mapper.toEntity
import com.guiderun.app.data.remote.api.AuthApi
import com.guiderun.app.data.remote.dto.LoginRequestDto
import com.guiderun.app.data.remote.dto.SendSmsRequestDto
import com.guiderun.app.domain.model.LoginResult
import com.guiderun.app.domain.model.ProvisioningStatus
import com.guiderun.app.domain.repository.AuthRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val authApi: AuthApi,
    private val userPreferences: UserPreferences,
    private val userDao: UserDao,
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
        userPreferences.clearAll()
        userDao.deleteAll()
    }
}
