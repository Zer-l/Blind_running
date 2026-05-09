package com.guiderun.app.data.location

import android.content.Context
import android.location.Geocoder
import android.location.Address
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class ReverseGeocoder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun getAddress(lat: Double, lng: Double): String {
        return try {
            val geocoder = Geocoder(context, Locale.CHINA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(lat, lng, 1) { addresses ->
                        val text = addresses.firstOrNull()?.let(::formatAddress) ?: ""
                        if (cont.isActive) cont.resume(text)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lng, 1)
                addresses?.firstOrNull()?.let(::formatAddress) ?: ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun formatAddress(addr: Address): String =
        listOfNotNull(
            addr.adminArea,
            addr.subAdminArea,
            addr.locality,
            addr.subLocality,
            addr.thoroughfare,
            addr.subThoroughfare,
        ).distinct().joinToString("")
}
