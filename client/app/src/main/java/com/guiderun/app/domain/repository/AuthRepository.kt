package com.guiderun.app.domain.repository

import com.guiderun.app.domain.model.LoginResult

interface AuthRepository {
    suspend fun sendSms(phone: String): Result<Unit>
    suspend fun login(phone: String, smsCode: String): Result<LoginResult>
    suspend fun logout(): Result<Unit>
}
