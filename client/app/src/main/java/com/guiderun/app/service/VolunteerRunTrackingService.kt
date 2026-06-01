package com.guiderun.app.service

import android.content.Intent
import com.guiderun.app.MainActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * 志愿者端跑步轨迹追踪前台服务。
 *
 * 继承 [RunTrackingService] 通用实现，覆盖 role / 通知文案 / 点击 Intent 三个志愿者侧差异点。
 * 通知点击回跳 MainActivity（带 EXTRA_RECOVERY_REQUEST_ID），由 MainActivity 路由到 VolunteerRunning 页。
 */
@AndroidEntryPoint
class VolunteerRunTrackingService : RunTrackingService() {

    override val role: String = "VOLUNTEER"
    override val notificationTitle: String get() = getString(com.guiderun.app.R.string.volunteer_tracking_title)
    override val notificationText: String get() = getString(com.guiderun.app.R.string.volunteer_tracking_text)
    override val locationIntervalMs: Long = DEFAULT_INTERVAL_MS

    override fun createContentIntent(requestId: String): Intent =
        Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_RECOVERY_REQUEST_ID, requestId)
        }
}
