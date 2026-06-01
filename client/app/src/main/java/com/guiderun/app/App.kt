package com.guiderun.app

import android.app.Application
import com.amap.api.maps.MapsInitializer
import com.guiderun.app.accessibility.TtsManager
import com.guiderun.app.data.local.UserPreferences
import com.guiderun.app.data.remote.WebSocketManager
import com.iflytek.cloud.SpeechConstant
import com.iflytek.cloud.SpeechUtility
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * 应用入口。
 *
 * 职责：
 * - 触发 Hilt 依赖图初始化（@HiltAndroidApp）
 * - 初始化第三方 SDK（高德地图、讯飞 MSC、TTS）
 * - 已登录用户冷启动时建立 WebSocket 长连接，确保订单状态实时推送不丢失
 */
@HiltAndroidApp
class App : Application() {

    @Inject lateinit var ttsManager: TtsManager
    @Inject lateinit var userPreferences: UserPreferences
    @Inject lateinit var webSocketManager: WebSocketManager

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())

        // 高德地图隐私合规 - 必须在任何地图SDK调用之前
        MapsInitializer.updatePrivacyShow(this, true, true)
        MapsInitializer.updatePrivacyAgree(this, true)

        // 讯飞 MSC SDK 初始化：必须在创建 SpeechRecognizer 之前调用一次
        // AppId 留空时跳过，等同于讯飞引擎不可用，UI 层会朗读"语音引擎未就绪"
        if (BuildConfig.IFLYTEK_APPID.isNotBlank()) {
            SpeechUtility.createUtility(
                this,
                "${SpeechConstant.APPID}=${BuildConfig.IFLYTEK_APPID}",
            )
        } else {
            Timber.w("IFLYTEK_APPID not configured; voice recognition will be disabled")
        }

        ttsManager.init()

        // 全局 WebSocket：已登录用户冷启动即连接，确保订单状态变化能实时推送到客户端。
        // 否则视障端首页/列表都收不到 status_changed，活跃订单 CLOSED 后无法清理横幅。
        appScope.launch {
            userPreferences.getAccessToken()?.let { token ->
                webSocketManager.connect(token)
            }
        }
    }
}
