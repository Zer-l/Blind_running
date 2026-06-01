package com.guiderun.app.service

import android.content.Intent
import com.guiderun.app.ui.blind.BlindActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * 视障端跑步轨迹追踪前台服务。
 *
 * 继承 [RunTrackingService] 通用实现，覆盖视障端差异：
 * - role = "BLIND"（用于服务端区分轨迹来源）
 * - 通知点击跳回 BlindActivity（带 EXTRA_RECOVERY_REQUEST_ID），由 BlindActivity.onNewIntent 路由到 BlindRunning 页
 */
@AndroidEntryPoint
class BlindRunTrackingService : RunTrackingService() {

    override val role: String = "BLIND"
    override val notificationTitle: String get() = getString(com.guiderun.app.R.string.blind_tracking_title)
    override val notificationText: String get() = getString(com.guiderun.app.R.string.blind_tracking_text)
    override val locationIntervalMs: Long = DEFAULT_INTERVAL_MS

    override fun createContentIntent(requestId: String): Intent =
        Intent(this, BlindActivity::class.java).apply {
            putExtra(BlindActivity.EXTRA_RECOVERY_REQUEST_ID, requestId)
        }
}
