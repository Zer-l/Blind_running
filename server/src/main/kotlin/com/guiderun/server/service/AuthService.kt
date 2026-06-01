package com.guiderun.server.service

import com.guiderun.server.config.JwtProperties
import com.guiderun.server.dto.auth.LoginResponse
import com.guiderun.server.dto.auth.RefreshResponse
import com.guiderun.server.entity.RefreshTokenEntity
import com.guiderun.server.common.ProvisioningStatus
import com.guiderun.server.entity.UserEntity
import com.guiderun.server.exception.AppException
import com.guiderun.server.exception.ErrorCode
import com.guiderun.server.mapper.toDto
import com.guiderun.server.repository.RefreshTokenJpaRepository
import com.guiderun.server.repository.UserJpaRepository
import com.guiderun.server.util.JwtUtil
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * 鉴权业务：短信发码 / 登录 / 刷新 token / 登出。
 *
 * - 短信验证码固定 Mock 为 `123456`（[sendSms] 仅打日志，未集成真实短信服务）
 * - 首次登录自动创建 [UserEntity]，状态置 PENDING_ROLE，等待客户端选角色
 * - refreshToken 自管随机字符串，落库存哈希（[sha256]），不签发 JWT 形式避免被解码
 * - 登出 = 撤销该用户所有 refreshToken（accessToken 因短期有效不主动失效）
 */
@Service
@Transactional
class AuthService(
    private val userRepo: UserJpaRepository,
    private val tokenRepo: RefreshTokenJpaRepository,
    private val jwtUtil: JwtUtil,
    private val jwtProps: JwtProperties,
) {
    private val log = LoggerFactory.getLogger(AuthService::class.java)

    /** Mock 短信发码：仅打日志，验证码固定 123456。 */
    fun sendSms(phone: String) {
        log.info("Mock SMS → {}: 123456", phone)
    }

    /** 登录：校验验证码 → 查/建 User → 签发 access + refresh token。 */
    fun login(phone: String, smsCode: String): LoginResponse {
        if (smsCode != "123456") {
            throw AppException(ErrorCode.INVALID_SMS_CODE, "验证码错误")
        }

        val user = userRepo.findByPhone(phone)
            ?: userRepo.save(
                UserEntity(
                    phone = phone,
                    nickname = "用户${phone.takeLast(4)}",
                    roles = emptyList(),
                    provisioningStatus = ProvisioningStatus.PENDING_ROLE,
                )
            )

        val accessToken = jwtUtil.generateAccessToken(user.id, user.roles)
        val rawRefreshToken = issueRefreshToken(user.id)

        return LoginResponse(
            accessToken = accessToken,
            refreshToken = rawRefreshToken,
            user = user.toDto(),
            isNewUser = user.roles.isEmpty(),
            provisioningStatus = user.provisioningStatus.name,
        )
    }

    /** 刷新 accessToken：校验 refreshToken 哈希存在 + 未撤销 + 未过期，更新 lastUsedAt。 */
    fun refresh(rawToken: String): RefreshResponse {
        val hash = sha256(rawToken)
        val entity = tokenRepo.findByTokenHash(hash)
            ?: throw AppException(ErrorCode.INVALID_TOKEN, "无效的刷新令牌", HttpStatus.UNAUTHORIZED)

        if (entity.revoked) {
            throw AppException(ErrorCode.INVALID_TOKEN, "令牌已失效", HttpStatus.UNAUTHORIZED)
        }
        if (entity.expiresAt.isBefore(Instant.now())) {
            throw AppException(ErrorCode.TOKEN_EXPIRED, "令牌已过期", HttpStatus.UNAUTHORIZED)
        }

        entity.lastUsedAt = Instant.now()

        val user = userRepo.findById(entity.userId).orElseThrow {
            AppException(ErrorCode.USER_NOT_FOUND, "用户不存在")
        }
        return RefreshResponse(accessToken = jwtUtil.generateAccessToken(user.id, user.roles))
    }

    fun logout(userId: String) {
        tokenRepo.revokeAllByUserId(userId)
    }

    private fun issueRefreshToken(userId: String): String {
        val raw = UUID.randomUUID().toString() + UUID.randomUUID().toString()
        val expiresAt = Instant.now().plus(jwtProps.refreshTokenExpiryDays, ChronoUnit.DAYS)
        tokenRepo.save(
            RefreshTokenEntity(
                userId = userId,
                tokenHash = sha256(raw),
                expiresAt = expiresAt,
            )
        )
        return raw
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
