package com.guiderun.app.service

import android.content.Intent
import com.guiderun.app.MainActivity
import dagger.hilt.android.AndroidEntryPoint

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
