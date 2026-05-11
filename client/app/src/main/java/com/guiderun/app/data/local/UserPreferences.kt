package com.guiderun.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store = context.dataStore

    suspend fun getAccessToken(): String? =
        store.data.map { it[Keys.ACCESS_TOKEN] }.first()

    suspend fun getRefreshToken(): String? =
        store.data.map { it[Keys.REFRESH_TOKEN] }.first()

    suspend fun getCurrentUserId(): String? =
        store.data.map { it[Keys.USER_ID] }.first()

    suspend fun getActiveRole(): String? =
        store.data.map { it[Keys.ACTIVE_ROLE] }.first()

    suspend fun saveTokens(accessToken: String, refreshToken: String) {
        store.edit {
            it[Keys.ACCESS_TOKEN] = accessToken
            it[Keys.REFRESH_TOKEN] = refreshToken
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
    }

    companion object {
        const val DEFAULT_TTS_SPEECH_RATE = 1.5f
    }
}
