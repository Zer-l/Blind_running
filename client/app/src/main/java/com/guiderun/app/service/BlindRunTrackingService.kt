package com.guiderun.app.service

import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class BlindRunTrackingService : RunTrackingService() {

    override val role: String = "BLIND"
    override val notificationTitle: String get() = getString(com.guiderun.app.R.string.blind_tracking_title)
    override val notificationText: String get() = getString(com.guiderun.app.R.string.blind_tracking_text)
    override val locationIntervalMs: Long = DEFAULT_INTERVAL_MS
}
