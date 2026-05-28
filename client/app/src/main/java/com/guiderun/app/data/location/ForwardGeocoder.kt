package com.guiderun.app.data.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.guiderun.app.domain.model.GeoPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * 正向地理编码：地址文本 → 经纬度。
 *
 * 与 [ReverseGeocoder] 配套，复用 Android 系统 [Geocoder]，零新增依赖。
 * 解析失败（空结果、网络异常、设备不支持）统一返回 null，由上层决定是否回退。
 */
@Singleton
class ForwardGeocoder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * @param near 可选偏置中心（一般传当前 GPS）。非空时优先用带包围盒的查询，
     *        把裸地名（无城市上下文，如"南门"）约束到附近 ~50km 内，避免命中同名异地；
     *        包围盒命中失败再回退无界查询。
     */
    suspend fun geocode(address: String, near: GeoPoint? = null): GeoPoint? {
        if (address.isBlank()) return null
        return try {
            val geocoder = Geocoder(context, Locale.CHINA)
            if (near != null) {
                geocodeBiased(geocoder, address, near) ?: geocodePlain(geocoder, address)
            } else {
                geocodePlain(geocoder, address)
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun geocodeBiased(
        geocoder: Geocoder,
        address: String,
        near: GeoPoint,
    ): GeoPoint? {
        val lowerLeftLat = (near.lat - BOX_DEGREES).coerceIn(-90.0, 90.0)
        val lowerLeftLng = (near.lng - BOX_DEGREES).coerceIn(-180.0, 180.0)
        val upperRightLat = (near.lat + BOX_DEGREES).coerceIn(-90.0, 90.0)
        val upperRightLng = (near.lng + BOX_DEGREES).coerceIn(-180.0, 180.0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocationName(
                    address, 1,
                    lowerLeftLat, lowerLeftLng, upperRightLat, upperRightLng,
                ) { addresses ->
                    if (cont.isActive) cont.resume(addresses.firstOrNull()?.toGeoPoint(address))
                }
            }
        } else {
            @Suppress("DEPRECATION")
            geocoder.getFromLocationName(
                address, 1,
                lowerLeftLat, lowerLeftLng, upperRightLat, upperRightLng,
            )?.firstOrNull()?.toGeoPoint(address)
        }
    }

    private suspend fun geocodePlain(geocoder: Geocoder, address: String): GeoPoint? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocationName(address, 1) { addresses ->
                    if (cont.isActive) cont.resume(addresses.firstOrNull()?.toGeoPoint(address))
                }
            }
        } else {
            @Suppress("DEPRECATION")
            geocoder.getFromLocationName(address, 1)?.firstOrNull()?.toGeoPoint(address)
        }
    }

    private fun Address.toGeoPoint(description: String): GeoPoint =
        GeoPoint(latitude, longitude, description)

    private companion object {
        /** 包围盒半边长（度），约 50km，足够覆盖一座城市又能排除同名异地。 */
        const val BOX_DEGREES = 0.45
    }
}
