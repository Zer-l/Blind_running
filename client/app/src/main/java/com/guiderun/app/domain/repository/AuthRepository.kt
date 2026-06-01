package com.guiderun.app.domain.repository

import com.guiderun.app.domain.model.LoginResult

/**
 * 认证仓库接口（Domain 层）。
 *
 * 实现位于 data 层（AuthRepositoryImpl），通过 Hilt 绑定注入，
 * 确保业务层不直接依赖 Retrofit / DataStore 等基础设施。
 */
interface AuthRepository {
    suspend fun sendSms(phone: String): Result<Unit>
    suspend fun login(phone: String, smsCode: String): Result<LoginResult>
    suspend fun logout(): Result<Unit>
}
