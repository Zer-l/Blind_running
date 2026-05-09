package com.guiderun.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.guiderun.app.data.local.UserPreferences
import com.guiderun.app.data.local.dao.RunSessionStatsDao
import com.guiderun.app.data.local.dao.RunTrackBufferDao
import com.guiderun.app.data.local.entity.RunSessionStatsEntity
import com.guiderun.app.data.local.entity.RunTrackBufferEntity
import com.guiderun.app.domain.repository.LocationProvider
import com.guiderun.app.domain.repository.RunRequestRepository
import com.guiderun.app.util.PaceCalculator
import com.guiderun.app.util.SimpleKalmanFilter
import java.util.LinkedList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

abstract class RunTrackingService : Service() {

    @Inject lateinit var locationProvider: LocationProvider
    @Inject lateinit var runRequestRepository: RunRequestRepository
    @Inject lateinit var trackBufferDao: RunTrackBufferDao
    @Inject lateinit var sessionStatsDao: RunSessionStatsDao
    @Inject lateinit var userPreferences: UserPreferences

    protected val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var trackingJob: Job? = null
    private var uploadJob: Job? = null
    private var startTimeMs: Long = 0L
    private var currentUserId: String = ""
    private var lastValidLat: Double? = null
    private var lastValidLng: Double? = null
    private var lastValidTimeMs: Long = 0L
    private var accumulatedDistanceMeters: Float = 0f
    private val recentPaces = mutableListOf<Int>()
    private val bufferedRecentPoints = mutableListOf<Pair<Double, Double>>()
    private val locationIntervalFlow = MutableStateFlow(locationIntervalMs)
    private var isStationary = false
    private var stationarySinceMs: Long = -1L

    // 轨迹平滑
    private val kalmanFilter = SimpleKalmanFilter(processNoise = 0.001, measurementNoise = 5.0)
    private val medianLatBuffer = LinkedList<Double>()
    private val medianLngBuffer = LinkedList<Double>()

    protected abstract val role: String
    protected abstract val notificationTitle: String
    protected abstract val notificationText: String
    protected abstract val locationIntervalMs: Long

    companion object {
        const val EXTRA_REQUEST_ID = "request_id"
        const val DEFAULT_INTERVAL_MS = 3_000L
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "run_tracking"
        private const val UPLOAD_BATCH_SIZE = 100
        private const val UPLOAD_INTERVAL_MS = 30_000L
        private const val STATIONARY_INTERVAL_MS = 30_000L
        private const val STATIONARY_SPEED_THRESHOLD = 0.5f
        private const val STATIONARY_DURATION_MS = 30_000L
        private const val OUTLIER_DISTANCE_THRESHOLD = 50f
        private const val OUTLIER_TIME_THRESHOLD_MS = 2_000L

        // 精度门控
        private const val ACCURACY_THRESHOLD = 30f  // 超过30米丢弃

        // 动态间隔阈值（米/秒）
        private const val SPEED_WALK = 2.0f         // 步行
        private const val SPEED_JOG = 4.0f          // 慢跑
        private const val INTERVAL_WALK_MS = 5_000L
        private const val INTERVAL_JOG_MS = 3_000L
        private const val INTERVAL_RUN_MS = 2_000L
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val requestId = intent?.getStringExtra(EXTRA_REQUEST_ID)
            ?: run { stopSelf(); return START_NOT_STICKY }
        startForeground(NOTIFICATION_ID, buildNotification())
        startTimeMs = System.currentTimeMillis()
        locationIntervalFlow.value = locationIntervalMs
        scope.launch {
            currentUserId = userPreferences.getCurrentUserId() ?: ""
            startTracking(requestId)
            startPeriodicUpload(requestId)
        }
        return START_REDELIVER_INTENT
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun startTracking(requestId: String) {
        trackingJob?.cancel()
        trackingJob = scope.launch {
            locationIntervalFlow.flatMapLatest { interval ->
                locationProvider.locationUpdates(interval)
            }.collect { geoPoint ->
                val now = System.currentTimeMillis()

                // P0: 精度门控 - 精度过差直接丢弃
                if (geoPoint.accuracy > ACCURACY_THRESHOLD) {
                    return@collect
                }

                // 异常点过滤
                val prevLat = lastValidLat
                val prevLng = lastValidLng
                val prevTimeMs = lastValidTimeMs
                if (prevLat != null && prevLng != null && prevTimeMs > 0) {
                    val dist = PaceCalculator.distanceMeters(prevLat, prevLng, geoPoint.lat, geoPoint.lng)
                    val dtMs = now - prevTimeMs
                    if (dist > OUTLIER_DISTANCE_THRESHOLD && dtMs < OUTLIER_TIME_THRESHOLD_MS) {
                        return@collect
                    }
                }

                // P1: 中值滤波 - 3点窗口去尖刺
                val (medianLat, medianLng) = medianFilter(geoPoint.lat, geoPoint.lng)

                // P2: 卡尔曼滤波 - 持续平滑
                val (smoothLat, smoothLng) = kalmanFilter.update(medianLat, medianLng, geoPoint.accuracy)

                val prevPointLat = lastValidLat
                val prevPointLng = lastValidLng
                val prevPointTimeMs = lastValidTimeMs

                lastValidLat = smoothLat
                lastValidLng = smoothLng
                lastValidTimeMs = now

                bufferedRecentPoints.add(smoothLat to smoothLng)
                if (bufferedRecentPoints.size > 60) {
                    bufferedRecentPoints.removeAt(0)
                }

                // 动态调整采集间隔
                adjustIntervalBySpeed()

                val bufferEntity = RunTrackBufferEntity(
                    requestId = requestId,
                    userId = currentUserId,
                    role = role,
                    timestamp = now,
                    lat = smoothLat,
                    lng = smoothLng,
                    accuracy = geoPoint.accuracy,
                    speed = null,
                )
                trackBufferDao.insert(bufferEntity)
                accumulateDistance(prevPointLat, prevPointLng, smoothLat, smoothLng)
                updateSessionStats(requestId, now, prevPointLat, prevPointLng, prevPointTimeMs)
                checkAndUpdateStationary(now)
            }
        }
    }

    private fun checkAndUpdateStationary(nowMs: Long) {
        val avgSpeed = computeRecentAvgSpeed()
        val nowStationary = avgSpeed != null && avgSpeed < STATIONARY_SPEED_THRESHOLD
        if (nowStationary && !isStationary) {
            if (stationarySinceMs <= 0L) stationarySinceMs = nowMs
            if (nowMs - stationarySinceMs >= STATIONARY_DURATION_MS) {
                isStationary = true
                locationIntervalFlow.value = STATIONARY_INTERVAL_MS
            }
        } else if (!nowStationary) {
            stationarySinceMs = -1L
            if (isStationary) {
                isStationary = false
                // 恢复时根据速度选择间隔
                adjustIntervalBySpeed()
            }
        }
    }

    /**
     * 中值滤波：用最近3个点的中值替代当前点，去除尖刺噪声。
     */
    private fun medianFilter(lat: Double, lng: Double): Pair<Double, Double> {
        medianLatBuffer.addLast(lat)
        medianLngBuffer.addLast(lng)
        if (medianLatBuffer.size > 3) {
            medianLatBuffer.removeFirst()
            medianLngBuffer.removeFirst()
        }
        val sortedLat = medianLatBuffer.sorted()
        val sortedLng = medianLngBuffer.sorted()
        return Pair(
            sortedLat[sortedLat.size / 2],
            sortedLng[sortedLng.size / 2],
        )
    }

    /**
     * 根据当前速度动态调整采集间隔。
     */
    private fun adjustIntervalBySpeed() {
        if (isStationary) return  // 静止状态不调整

        val avgSpeed = computeRecentAvgSpeed() ?: return
        val targetInterval = when {
            avgSpeed < SPEED_WALK -> INTERVAL_WALK_MS    // 步行：5秒
            avgSpeed < SPEED_JOG  -> INTERVAL_JOG_MS     // 慢跑：3秒
            else                  -> INTERVAL_RUN_MS     // 快跑：2秒
        }
        if (locationIntervalFlow.value != targetInterval) {
            locationIntervalFlow.value = targetInterval
        }
    }

    private fun computeRecentAvgSpeed(): Float? {
        val points = bufferedRecentPoints
        if (points.size < 2) return null
        var totalDist = 0f
        for (i in 1 until points.size) {
            val (lat1, lng1) = points[i - 1]
            val (lat2, lng2) = points[i]
            totalDist += PaceCalculator.distanceMeters(lat1, lng1, lat2, lng2)
        }
        val totalTimeSec = (points.size - 1) * (locationIntervalFlow.value / 1000f)
        if (totalTimeSec <= 0f) return null
        return totalDist / totalTimeSec
    }

    private fun accumulateDistance(prevLat: Double?, prevLng: Double?, curLat: Double, curLng: Double) {
        if (prevLat != null && prevLng != null) {
            accumulatedDistanceMeters += PaceCalculator.distanceMeters(prevLat, prevLng, curLat, curLng)
        }
    }

    private fun updateSessionStats(
        requestId: String,
        nowMs: Long,
        prevLat: Double?,
        prevLng: Double?,
        prevTimeMs: Long,
    ) {
        val durationSec = ((nowMs - startTimeMs) / 1000).toInt()
        val totalDistance = accumulatedDistanceMeters.toInt()

        if (!isStationary && prevLat != null && prevLng != null && prevTimeMs > 0) {
            val dtSec = ((nowMs - prevTimeMs) / 1000).toInt()
            if (dtSec > 0) {
                val dist = PaceCalculator.distanceMeters(prevLat, prevLng, lastValidLat!!, lastValidLng!!)
                val distKm = dist / 1000f
                if (distKm > 0.005f && dist / dtSec > 0.5f) {
                    recentPaces.add((dtSec / distKm).toInt())
                    if (recentPaces.size > 10) recentPaces.removeAt(0)
                }
            }
        }

        val currentPace = PaceCalculator.slidingAverage(recentPaces)
        val avgPace = PaceCalculator.avgPace(totalDistance, durationSec)

        scope.launch {
            val existing = sessionStatsDao.get(requestId, currentUserId)
            val stats = RunSessionStatsEntity(
                requestId = requestId,
                userId = currentUserId,
                totalDistanceMeters = totalDistance,
                totalDurationSeconds = durationSec,
                currentPaceSeconds = currentPace,
                avgPaceSeconds = avgPace,
                maxSpeedMps = existing?.maxSpeedMps,
                lastUpdatedAt = nowMs,
            )
            sessionStatsDao.upsert(stats)
        }
    }

    private fun startPeriodicUpload(requestId: String) {
        uploadJob?.cancel()
        uploadJob = scope.launch {
            while (true) {
                delay(UPLOAD_INTERVAL_MS)
                uploadPendingPoints(requestId)
            }
        }
    }

    private suspend fun uploadPendingPoints(requestId: String) {
        val pending = trackBufferDao.getPendingPoints(requestId, UPLOAD_BATCH_SIZE)
        if (pending.isEmpty()) return
        val points = pending.map {
            com.guiderun.app.domain.model.TrackPoint(
                t = it.timestamp,
                lat = it.lat,
                lng = it.lng,
                acc = it.accuracy,
                spd = it.speed,
            )
        }
        runCatching {
            runRequestRepository.uploadTracks(requestId, role, points)
        }.onSuccess {
            trackBufferDao.markUploaded(pending.map { it.id })
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        trackingJob?.cancel()
        uploadJob?.cancel()
        scope.launch {
            if (currentUserId.isNotEmpty()) {
                val pending = trackBufferDao.getPendingPoints("", 1)
                pending.firstOrNull()?.requestId?.let { uploadPendingPoints(it) }
            }
        }
        kalmanFilter.reset()
        medianLatBuffer.clear()
        medianLngBuffer.clear()
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(com.guiderun.app.R.string.run_tracking_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(notificationTitle)
            .setContentText(notificationText)
            .setSmallIcon(com.guiderun.app.R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
}
