package com.guiderun.app.ui.shared.map

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.graphics.toColorInt
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.PolylineOptions
import com.guiderun.app.data.location.Wgs84ToGcj02Converter

data class PolylineConfig(
    val points: List<Pair<Double, Double>>,
    val colorHex: String,
    val width: Float = 10f,
)

/**
 * 相机定位意图。
 *
 * 仅当传入的 [CameraTarget] 实例（引用）与上次不同的时候，[GuideRunMap] 才会调用
 * 一次 `moveCamera`。这样可以避免每次 mapState 局部更新（如 marker 跟随轨迹播放）
 * 都把用户手势缩放/平移后的相机强制拉回，造成"地图反复缩回"问题。
 *
 * 用法：
 * - 页面首次进入或用户点击"重新定位"按钮时：构造新的 CameraTarget 实例传入；
 * - 数据增量刷新（marker 位置、折线增长等）时：保持 cameraTarget 引用不变，
 *   只 copy 其它字段。
 */
data class CameraTarget(
    val lat: Double,
    val lng: Double,
    val zoom: Float = 16f,
)

data class GuideRunMapState(
    val cameraTarget: CameraTarget? = null,
    val volunteerLatLng: Pair<Double, Double>? = null,
    val blindLatLng: Pair<Double, Double>? = null,
    val polylines: List<PolylineConfig> = emptyList(),
    val animatedMarker: Pair<Double, Double>? = null,
)

/** 纯对象 holder：不进 Compose snapshot，避免 mutableStateOf 在 AndroidView.update 中写入触发的重组循环/时序问题。 */
private class CameraStateHolder {
    var lastApplied: CameraTarget? = null
}

@Composable
fun GuideRunMap(
    state: GuideRunMapState,
    modifier: Modifier = Modifier,
) {
    val mapView = rememberMapViewWithLifecycle()
    // 记录上次已应用的 CameraTarget 引用：引用相同 = 业务侧没要求重新定位，跳过 moveCamera。
    val cameraHolder = remember { CameraStateHolder() }
    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { mv ->
            val map = mv.map
            // 业务层 GeoPoint 是 WGS-84，必须转 GCJ-02 才能与高德底图对齐，否则会偏移数百米。
            val converter = Wgs84ToGcj02Converter(mv.context)

            val target = state.cameraTarget
            if (target != null && target !== cameraHolder.lastApplied) {
                map.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        converter.convert(target.lat, target.lng),
                        target.zoom,
                    )
                )
                cameraHolder.lastApplied = target
            }

            map.clear()
            state.volunteerLatLng?.let { (lat, lng) ->
                map.addMarker(MarkerOptions().position(converter.convert(lat, lng)).title("志愿者"))
            }
            state.blindLatLng?.let { (lat, lng) ->
                map.addMarker(MarkerOptions().position(converter.convert(lat, lng)).title("集合点"))
            }
            state.polylines.forEach { config ->
                if (config.points.size >= 2) {
                    map.addPolyline(
                        PolylineOptions()
                            .addAll(config.points.map { (lat, lng) -> converter.convert(lat, lng) })
                            .color(config.colorHex.toColorInt())
                            .width(config.width)
                    )
                }
            }
            state.animatedMarker?.let { (lat, lng) ->
                map.addMarker(
                    MarkerOptions()
                        .position(converter.convert(lat, lng))
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                        .title("当前位置")
                )
            }
        },
    )
}

@Composable
private fun rememberMapViewWithLifecycle(): MapView {
    val context = LocalContext.current
    val mapView = remember { MapView(context) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(Unit) {
        mapView.onCreate(Bundle())
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        // If lifecycle is already RESUMED (Navigation Compose), call onResume() explicitly
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            mapView.onResume()
        }
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }
    return mapView
}
