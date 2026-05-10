package com.guiderun.app

import android.app.Application
import com.amap.api.maps.MapsInitializer
import com.guiderun.app.accessibility.TtsManager
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class App : Application() {

    @Inject lateinit var ttsManager: TtsManager

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())

        // 高德地图隐私合规 - 必须在任何地图SDK调用之前
        MapsInitializer.updatePrivacyShow(this, true, true)
        MapsInitializer.updatePrivacyAgree(this, true)

        ttsManager.init()
    }
}
