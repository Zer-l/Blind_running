package com.guiderun.app.data.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import timber.log.Timber
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 基于 Android KeyStore 的对称加密，用于在写入 DataStore 前加密敏感令牌（access/refresh token）。
 *
 * 设计：
 * - 密钥由硬件支持的 AndroidKeyStore 生成与保管，**永不离开 KeyStore**，App 仅持有句柄；
 * - 算法 AES‑256/GCM/NoPadding，每次加密生成随机 IV（12 字节），IV ‖ 密文 拼接后 Base64 存储；
 * - 解密失败（密文损坏 / 密钥被系统清除 / 升级前的旧明文残留）返回 null，由上层当作"未登录"
 *   触发重新登录，**不抛异常打断调用链**。
 *
 * 不引入第三方依赖：EncryptedSharedPreferences(security-crypto) 已被 Google 标记 deprecated，
 * 此处直接用平台 KeyStore + javax.crypto 实现等效能力。
 */
@Singleton
class TokenCipher @Inject constructor() {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    // KeyStore 取/建密钥非线程安全，token 读取可能在多个 IO/OkHttp 线程并发，故同步。
    @Synchronized
    private fun getOrCreateKey(): SecretKey {
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    /** 加密明文，返回 Base64(iv ‖ ciphertext)。KeyStore 异常时向上抛出，由写入路径（登录）感知失败。 */
    fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + cipherText, Base64.NO_WRAP)
    }

    /** 解密 [encrypt] 的产出；任何失败（损坏 / 旧明文 / 密钥失效）返回 null，触发上层重新登录。 */
    fun decrypt(encoded: String): String? = runCatching {
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        if (combined.size <= GCM_IV_LENGTH) return null
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val cipherText = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }.onFailure { Timber.w(it, "TokenCipher: decrypt failed, treat as no token") }.getOrNull()

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "guiderun_token_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_LENGTH = 12
        const val GCM_TAG_BITS = 128
    }
}
