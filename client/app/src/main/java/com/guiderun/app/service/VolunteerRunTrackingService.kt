package com.guiderun.app.service

import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class VolunteerRunTrackingService : RunTrackingService() {

    override val role: String = "VOLUNTEER"
    override val notificationTitle: String get() = getString(com.guiderun.app.R.string.volunteer_tracking_title)
    override val notificationText: String get() = getString(com.guiderun.app.R.string.volunteer_tracking_text)
    override val locationIntervalMs: Long = DEFAULT_INTERVAL_MS
}
