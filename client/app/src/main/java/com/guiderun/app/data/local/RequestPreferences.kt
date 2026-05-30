package com.guiderun.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 视障端发起跑步请求的偏好持久化。
 *
 * 用途：
 * - BlindCreateRequestViewModel 在 init 时预填上次成功提交的参数；
 * - HomeScreen 长按 2 秒"一键发起"快捷入口的数据源。
 *
 * 独立于 UserPreferences 的 DataStore（"request_prefs"），避免与认证 / 主题等配置混在一起。
 */
@Singleton
class RequestPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store: DataStore<Preferences> = context.requestPrefsDataStore

    /** 最近一次成功提交的请求参数；从未提交时返回 null。 */
    suspend fun loadLast(): LastRequestPrefs? {
        val prefs = store.data.map { it }.first()
        val duration = prefs[Keys.DURATION_MIN] ?: return null
        val locationDesc = prefs[Keys.LOCATION_DESC] ?: return null
        val notes = prefs[Keys.NOTES].orEmpty()
        val lat = prefs[Keys.LAT]
        val lng = prefs[Keys.LNG]
        return LastRequestPrefs(
            durationMinutes = duration,
            locationDesc = locationDesc,
            notes = notes,
            lat = lat,
            lng = lng,
        )
    }

    suspend fun saveLast(
        durationMinutes: Int,
        locationDesc: String,
        notes: String,
        lat: Double?,
        lng: Double?,
    ) {
        store.edit { p ->
            p[Keys.DURATION_MIN] = durationMinutes
            p[Keys.LOCATION_DESC] = locationDesc
            p[Keys.NOTES] = notes
            if (lat != null) p[Keys.LAT] = lat else p.remove(Keys.LAT)
            if (lng != null) p[Keys.LNG] = lng else p.remove(Keys.LNG)
        }
    }

    suspend fun clear() {
        store.edit { it.clear() }
    }

    private object Keys {
        val DURATION_MIN = intPreferencesKey("duration_min")
        val LOCATION_DESC = stringPreferencesKey("location_desc")
        val NOTES = stringPreferencesKey("notes")
        val LAT = doublePreferencesKey("lat")
        val LNG = doublePreferencesKey("lng")
    }

    companion object {
        private val Context.requestPrefsDataStore: DataStore<Preferences> by preferencesDataStore(
            name = "request_prefs"
        )
    }
}

/** 上次成功提交的请求参数快照。 */
data class LastRequestPrefs(
    val durationMinutes: Int,
    val locationDesc: String,
    val notes: String,
    val lat: Double?,
    val lng: Double?,
)
