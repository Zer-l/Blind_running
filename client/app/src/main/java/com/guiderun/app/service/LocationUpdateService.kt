package com.guiderun.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.guiderun.app.R
import com.guiderun.app.domain.repository.LocationProvider
import com.guiderun.app.domain.repository.RunRequestRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LocationUpdateService : Service() {

    @Inject lateinit var locationProvider: LocationProvider
    @Inject lateinit var runRequestRepository: RunRequestRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var trackingJob: Job? = null

    companion object {
        const val EXTRA_REQUEST_ID = "request_id"
        const val EXTRA_INTERVAL_SECONDS = "interval_seconds"
        const val DEFAULT_INTERVAL_SECONDS = 5
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "location_update"
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val requestId = intent?.getStringExtra(EXTRA_REQUEST_ID)
            ?: run { stopSelf(); return START_NOT_STICKY }
        val intervalMs =
            intent.getIntExtra(EXTRA_INTERVAL_SECONDS, DEFAULT_INTERVAL_SECONDS) * 1_000L
        startForeground(NOTIFICATION_ID, buildNotification())
        startTracking(requestId, intervalMs)
        return START_REDELIVER_INTENT
    }

    private fun startTracking(requestId: String, intervalMs: Long) {
        trackingJob?.cancel()
        trackingJob = scope.launch {
            runCatching {
                locationProvider.locationUpdates(intervalMs).collect { geoPoint ->
                    runRequestRepository.reportPosition(requestId, geoPoint.lat, geoPoint.lng)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        trackingJob?.cancel()
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.location_service_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.location_service_title))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
}
