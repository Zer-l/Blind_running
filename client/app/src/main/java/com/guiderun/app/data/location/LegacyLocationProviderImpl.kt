package com.guiderun.app.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import com.guiderun.app.domain.model.GeoPoint
import com.guiderun.app.domain.repository.LocationProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Fallback for devices without Google Play Services (e.g. some Huawei models). */
class LegacyLocationProviderImpl(context: Context) : LocationProvider {

    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    @SuppressLint("MissingPermission")
    override suspend fun getLastLocation(): GeoPoint? {
        val gpsLoc = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        val networkLoc = manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

        // 选择最新的位置
        val loc = when {
            gpsLoc != null && networkLoc != null ->
                if (gpsLoc.time > networkLoc.time) gpsLoc else networkLoc
            gpsLoc != null -> gpsLoc
            networkLoc != null -> networkLoc
            else -> null
        }
        return loc?.let {
            GeoPoint(
                lat = it.latitude,
                lng = it.longitude,
                description = "",
                accuracy = it.accuracy,
                realtimeMs = it.elapsedRealtimeNanos / 1_000_000L,
            )
        }
    }

    @SuppressLint("MissingPermission")
    override fun locationUpdates(intervalMs: Long): Flow<GeoPoint> = callbackFlow {
        val listener = LocationListener { location ->
            val speed = if (location.hasSpeed()) location.speed else null
            val speedAcc = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
                location.hasSpeedAccuracy()
            ) location.speedAccuracyMetersPerSecond else null
            trySend(
                GeoPoint(
                    lat = location.latitude,
                    lng = location.longitude,
                    description = "",
                    accuracy = location.accuracy,
                    realtimeMs = location.elapsedRealtimeNanos / 1_000_000L,
                    speedMps = speed,
                    speedAccuracyMps = speedAcc,
                )
            )
        }

        // 同时注册 GPS 和网络定位
        val gpsEnabled = manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val networkEnabled = manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        val mainLooper = Looper.getMainLooper()
        if (gpsEnabled) {
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, intervalMs, 0f, listener, mainLooper)
        }
        if (networkEnabled) {
            manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, intervalMs, 0f, listener, mainLooper)
        }

        if (!gpsEnabled && !networkEnabled) {
            close(Exception("没有可用的定位提供者"))
        }

        awaitClose { manager.removeUpdates(listener) }
    }
}
