package com.guiderun.app.data.location

import android.content.Context
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
    suspend fun geocode(address: String): GeoPoint? {
        if (address.isBlank()) return null
        return try {
            val geocoder = Geocoder(context, Locale.CHINA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocationName(address, 1) { addresses ->
                        val pt = addresses.firstOrNull()?.let {
                            GeoPoint(it.latitude, it.longitude, address)
                        }
                        if (cont.isActive) cont.resume(pt)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val results = geocoder.getFromLocationName(address, 1)
                results?.firstOrNull()?.let {
                    GeoPoint(it.latitude, it.longitude, address)
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}
