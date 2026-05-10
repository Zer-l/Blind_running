package com.guiderun.app.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.guiderun.app.domain.model.GeoPoint
import com.guiderun.app.domain.repository.LocationProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlin.math.max

/**
 * 双源 LocationProvider：FusedLocation + LocationManager（GPS/Network）。
 *
 * 解决两个问题：
 * 1. MIUI 等设备 FusedLocation 被代理拦截 → Legacy 兜底；
 * 2. 双源同时上报会重复进入处理流水线 → 去重 + 节流。
 *
 * 去重策略：
 * - 同一 elapsedRealtime 时间窗口（[DEDUP_WINDOW_MS]）内，保留 accuracy 更小的点；
 * - 实际 emit 间隔下界：`intervalMs * MIN_INTERVAL_RATIO`，避免下游被淹没。
 */
class FusedLocationProviderImpl(private val context: Context) : LocationProvider {

    private val client = LocationServices.getFusedLocationProviderClient(context)
    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    @SuppressLint("MissingPermission")
    override suspend fun getLastLocation(): GeoPoint? {
        // 优先尝试 Android 原生 LocationManager（在小米设备上更可靠）
        getLastLocationFromLegacy()?.let { return it }

        return runCatching {
            val location = runCatching {
                client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
            }.getOrNull() ?: client.lastLocation.await()
            location?.toGeoPoint()
        }.getOrNull()
    }

    @SuppressLint("MissingPermission")
    private fun getLastLocationFromLegacy(): GeoPoint? {
        return runCatching {
            val gps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val network = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            val location = when {
                gps != null && network != null -> if (gps.time > network.time) gps else network
                else -> gps ?: network
            }
            location?.toGeoPoint()
        }.getOrNull()
    }

    @SuppressLint("MissingPermission")
    override fun locationUpdates(intervalMs: Long): Flow<GeoPoint> = callbackFlow {
        val minEmitGapMs = max((intervalMs * MIN_INTERVAL_RATIO).toLong(), MIN_EMIT_GAP_FLOOR_MS)
        val gate = LocationDedupGate(
            dedupWindowMs = DEDUP_WINDOW_MS,
            minEmitGapMs = minEmitGapMs,
        )

        fun handle(location: Location) {
            val geo = location.toGeoPoint()
            val emitted = gate.tryEmit(geo)
            if (emitted != null) trySend(emitted)
        }

        // Legacy LocationManager（GPS + Network）
        val legacyListener = LocationListener { handle(it) }
        val mainLooper = Looper.getMainLooper()
        val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        if (gpsEnabled) {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, intervalMs, 0f, legacyListener, mainLooper
            )
        }
        if (networkEnabled) {
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER, intervalMs, 0f, legacyListener, mainLooper
            )
        }

        // FusedLocation
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .setMaxUpdateDelayMillis(intervalMs * 2)
            .build()
        val fusedCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let(::handle)
            }
        }
        runCatching {
            client.requestLocationUpdates(request, fusedCallback, mainLooper)
        }

        awaitClose {
            locationManager.removeUpdates(legacyListener)
            client.removeLocationUpdates(fusedCallback)
        }
    }

    private fun Location.toGeoPoint(): GeoPoint {
        // elapsedRealtimeNanos 在所有 Android 版本上单调递增，作为统一时间戳基准。
        val realtimeMs = elapsedRealtimeNanos / 1_000_000L
        return GeoPoint(
            lat = latitude,
            lng = longitude,
            description = "",
            accuracy = accuracy,
            realtimeMs = if (realtimeMs > 0L) realtimeMs else SystemClock.elapsedRealtime(),
        )
    }

    private companion object {
        /** 同一去重窗口内最多 emit 一个点（取 accuracy 更小者）。 */
        const val DEDUP_WINDOW_MS = 250L

        /** 实际 emit 间隔不低于 intervalMs * 该比例。 */
        const val MIN_INTERVAL_RATIO = 0.7

        /** emit 间隔的硬下限，避免极短 intervalMs 下被打满。 */
        const val MIN_EMIT_GAP_FLOOR_MS = 500L
    }
}

/**
 * 双源去重 + 节流闸门。线程安全：所有 [tryEmit] 调用都需要在外部串行（Looper.MAIN）。
 *
 * 行为：
 * - 在 [dedupWindowMs] 窗口内仅 emit 一个点：保留 accuracy 更小的；
 * - 即使两次都通过去重，也要满足 [minEmitGapMs] 节流间隔；
 * - emit 时刻使用上一次 buffered 点，避免"等到下一帧才发"的延迟。
 */
private class LocationDedupGate(
    private val dedupWindowMs: Long,
    private val minEmitGapMs: Long,
) {
    private var bufferPoint: GeoPoint? = null
    private var bufferAtRealtimeMs: Long = 0L
    private var lastEmittedAtRealtimeMs: Long = 0L

    fun tryEmit(geo: GeoPoint): GeoPoint? {
        val now = if (geo.realtimeMs > 0L) geo.realtimeMs else SystemClock.elapsedRealtime()
        val buf = bufferPoint
        // 同一去重窗口内：合并取 accuracy 更优者
        if (buf != null && now - bufferAtRealtimeMs < dedupWindowMs) {
            if (geo.accuracy in 0.01f..buf.accuracy) {
                bufferPoint = geo
                bufferAtRealtimeMs = now
            }
            return null
        }
        // 节流：距上次 emit 不足 minEmitGapMs，缓冲后丢弃
        if (now - lastEmittedAtRealtimeMs < minEmitGapMs) {
            bufferPoint = geo
            bufferAtRealtimeMs = now
            return null
        }
        // 通过：emit 当前点
        bufferPoint = null
        bufferAtRealtimeMs = 0L
        lastEmittedAtRealtimeMs = now
        return geo
    }
}
