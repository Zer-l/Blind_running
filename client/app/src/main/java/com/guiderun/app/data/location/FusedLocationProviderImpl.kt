package com.guiderun.app.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
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

/**
 * FusedLocationProvider 实现，同时使用 Google FusedLocation 和 Android 原生 LocationManager 作为备选。
 * 解决小米 MIUI 等设备上 FusedLocationProvider 被拦截的问题。
 */
class FusedLocationProviderImpl(private val context: Context) : LocationProvider {

    private val client = LocationServices.getFusedLocationProviderClient(context)
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    @SuppressLint("MissingPermission")
    override suspend fun getLastLocation(): GeoPoint? {
        // 优先尝试 Android 原生 LocationManager（在小米设备上更可靠）
        val legacyLocation = getLastLocationFromLegacy()
        if (legacyLocation != null) return legacyLocation

        // 备选：尝试 Google FusedLocation
        return try {
            val location = try {
                client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
            } catch (_: Exception) {
                client.lastLocation.await()
            }
            location?.let { GeoPoint(it.latitude, it.longitude, "", it.accuracy) }
        } catch (_: Exception) {
            null
        }
    }

    @SuppressLint("MissingPermission")
    private fun getLastLocationFromLegacy(): GeoPoint? {
        return try {
            val gpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val networkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

            // 选择最新的位置
            val location = when {
                gpsLocation != null && networkLocation != null ->
                    if (gpsLocation.time > networkLocation.time) gpsLocation else networkLocation
                gpsLocation != null -> gpsLocation
                networkLocation != null -> networkLocation
                else -> null
            }
            location?.let { GeoPoint(it.latitude, it.longitude, "", it.accuracy) }
        } catch (_: Exception) {
            null
        }
    }

    @SuppressLint("MissingPermission")
    override fun locationUpdates(intervalMs: Long): Flow<GeoPoint> = callbackFlow {
        // 同时使用 Legacy LocationManager 和 FusedLocation
        val legacyListener = LocationListener { location ->
            trySend(GeoPoint(location.latitude, location.longitude, "", location.accuracy))
        }

        // 注册 Legacy LocationManager
        val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        val mainLooper = Looper.getMainLooper()
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

        // 注册 FusedLocation
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .setMaxUpdateDelayMillis(intervalMs * 2)
            .build()

        val fusedCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let {
                    trySend(GeoPoint(it.latitude, it.longitude, "", it.accuracy))
                }
            }
        }

        try {
            client.requestLocationUpdates(request, fusedCallback, Looper.getMainLooper())
        } catch (_: Exception) {
            // FusedLocation 失败时 Legacy 仍可用
        }

        awaitClose {
            locationManager.removeUpdates(legacyListener)
            client.removeLocationUpdates(fusedCallback)
        }
    }
}
