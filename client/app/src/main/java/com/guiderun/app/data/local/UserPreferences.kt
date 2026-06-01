package com.guiderun.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

/**
 * 用户偏好持久化层，基于 Jetpack DataStore（Preferences API）。
 *
 * 存储内容：
 * - Token（AES/GCM 加密存储，由 [TokenCipher] 处理）
 * - 当前用户 ID、角色
 * - 视障端无障碍配置：字号缩放、对比度主题、TTS 语速/音量、震动强度
 * - 志愿者端主题 ID
 * - 进行中订单 ID（用于冷启动恢复）
 *
 * 注意：[getBlindFontScaleOnce] / [getBlindContrastThemeOnce] 供 BaseBlindActivity.attachBaseContext
 * 同步读取（runBlocking），其余配置项通过 Flow 异步观察，避免主线程阻塞。
 */
@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenCipher: TokenCipher,
) {
    private val store = context.dataStore

    // token 在磁盘上以 KeyStore AES/GCM 密文存储；读出后解密，解密失败（旧明文 / 损坏）返回 null 触发重新登录。
    suspend fun getAccessToken(): String? =
        store.data.map { it[Keys.ACCESS_TOKEN] }.first()?.let { tokenCipher.decrypt(it) }

    suspend fun getRefreshToken(): String? =
        store.data.map { it[Keys.REFRESH_TOKEN] }.first()?.let { tokenCipher.decrypt(it) }

    suspend fun getCurrentUserId(): String? =
        store.data.map { it[Keys.USER_ID] }.first()

    suspend fun getActiveRole(): String? =
        store.data.map { it[Keys.ACTIVE_ROLE] }.first()

    suspend fun saveTokens(accessToken: String, refreshToken: String) {
        // 先加密再写入：加密阶段（KeyStore 异常）失败时不会留下半套数据
        val encryptedAccess = tokenCipher.encrypt(accessToken)
        val encryptedRefresh = tokenCipher.encrypt(refreshToken)
        store.edit {
            it[Keys.ACCESS_TOKEN] = encryptedAccess
            it[Keys.REFRESH_TOKEN] = encryptedRefresh
        }
    }

    suspend fun saveUserSession(userId: String, activeRole: String) {
        store.edit {
            it[Keys.USER_ID] = userId
            it[Keys.ACTIVE_ROLE] = activeRole
        }
    }

    fun getTtsSpeechRate(): Flow<Float> =
        store.data.map { it[Keys.TTS_SPEECH_RATE] ?: DEFAULT_TTS_SPEECH_RATE }

    suspend fun saveTtsSpeechRate(rate: Float) {
        store.edit { it[Keys.TTS_SPEECH_RATE] = rate }
    }

    /** 观察主题 ID，默认 "orange" */
    fun observeThemeId(): Flow<String> =
        store.data.map { it[Keys.THEME_ID] ?: DEFAULT_THEME_ID }

    suspend fun saveThemeId(themeId: String) {
        store.edit { it[Keys.THEME_ID] = themeId }
    }

    // ===== 视障端无障碍配置（字号缩放 / 对比度主题 / TTS 音量 / 震动强度） =====

    /** 视障端字号缩放，1.0 / 1.25 / 1.5 / 2.0 */
    fun getBlindFontScale(): Flow<Float> =
        store.data.map { it[Keys.BLIND_FONT_SCALE] ?: DEFAULT_BLIND_FONT_SCALE }

    suspend fun getBlindFontScaleOnce(): Float =
        getBlindFontScale().first()

    suspend fun saveBlindFontScale(scale: Float) {
        store.edit { it[Keys.BLIND_FONT_SCALE] = scale }
    }

    /** 视障端对比度主题 ID：BLACK / WHITE / YELLOW */
    fun getBlindContrastTheme(): Flow<String> =
        store.data.map { it[Keys.BLIND_CONTRAST_THEME] ?: DEFAULT_BLIND_CONTRAST_THEME }

    suspend fun getBlindContrastThemeOnce(): String =
        getBlindContrastTheme().first()

    suspend fun saveBlindContrastTheme(theme: String) {
        store.edit { it[Keys.BLIND_CONTRAST_THEME] = theme }
    }

    /** TTS 音量倍率，0.5~1.0 */
    fun getBlindTtsVolume(): Flow<Float> =
        store.data.map { it[Keys.BLIND_TTS_VOLUME] ?: DEFAULT_BLIND_TTS_VOLUME }

    suspend fun saveBlindTtsVolume(volume: Float) {
        store.edit { it[Keys.BLIND_TTS_VOLUME] = volume }
    }

    /** 震动强度档：0=关 / 1=标准 / 2=强 */
    fun getBlindHapticStrength(): Flow<Int> =
        store.data.map { it[Keys.BLIND_HAPTIC_STRENGTH] ?: DEFAULT_BLIND_HAPTIC_STRENGTH }

    suspend fun saveBlindHapticStrength(strength: Int) {
        store.edit { it[Keys.BLIND_HAPTIC_STRENGTH] = strength }
    }

    fun observeActiveRequestId(): Flow<String?> =
        store.data.map { it[Keys.ACTIVE_REQUEST_ID] }

    suspend fun getActiveRequestId(): String? =
        store.data.map { it[Keys.ACTIVE_REQUEST_ID] }.first()

    suspend fun saveActiveRequestId(requestId: String) {
        store.edit { it[Keys.ACTIVE_REQUEST_ID] = requestId }
    }

    suspend fun clearActiveRequestId() {
        store.edit { it.remove(Keys.ACTIVE_REQUEST_ID) }
    }

    suspend fun clearAll() {
        store.edit { it.clear() }
    }

    private object Keys {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val USER_ID = stringPreferencesKey("user_id")
        val ACTIVE_ROLE = stringPreferencesKey("active_role")
        val TTS_SPEECH_RATE = floatPreferencesKey("tts_speech_rate")
        val ACTIVE_REQUEST_ID = stringPreferencesKey("active_request_id")
        val THEME_ID = stringPreferencesKey("theme_id")
        val BLIND_FONT_SCALE = floatPreferencesKey("blind_font_scale")
        val BLIND_CONTRAST_THEME = stringPreferencesKey("blind_contrast_theme")
        val BLIND_TTS_VOLUME = floatPreferencesKey("blind_tts_volume")
        val BLIND_HAPTIC_STRENGTH = intPreferencesKey("blind_haptic_strength")
    }

    companion object {
        const val DEFAULT_TTS_SPEECH_RATE = 1.0f
        const val DEFAULT_THEME_ID = "orange"
        const val DEFAULT_BLIND_FONT_SCALE = 1.0f
        const val DEFAULT_BLIND_CONTRAST_THEME = "BLACK"
        const val DEFAULT_BLIND_TTS_VOLUME = 1.0f
        const val DEFAULT_BLIND_HAPTIC_STRENGTH = 1
    }
}
