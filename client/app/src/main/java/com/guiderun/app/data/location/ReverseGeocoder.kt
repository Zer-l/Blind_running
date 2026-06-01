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

/**
 * 反向地理编码：经纬度 → 可读地址字符串。
 *
 * 使用 Android 系统 [android.location.Geocoder]，避免引入第三方地图 SDK 依赖。
 * Android 33+ 使用异步回调 API（suspendCancellableCoroutine 桥接为 suspend），
 * 低版本使用已废弃的同步 API（在 IO 线程调用，不阻塞主线程）。
 * 解析失败（网络不可用、Geocoder 不支持）统一返回空字符串，上层按需显示"未知位置"。
 */
@Singleton
class ReverseGeocoder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * @return 格式化后的省/市/区/街道拼接字符串，失败返回空字符串。
     */
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
