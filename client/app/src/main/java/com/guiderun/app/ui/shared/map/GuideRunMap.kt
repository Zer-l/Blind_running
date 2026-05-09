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
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.PolylineOptions

data class PolylineConfig(
    val points: List<Pair<Double, Double>>,
    val colorHex: String,
    val width: Float = 10f,
)

data class GuideRunMapState(
    val centerLat: Double = 39.9042,
    val centerLng: Double = 116.4074,
    val zoom: Float = 16f,
    val volunteerLatLng: Pair<Double, Double>? = null,
    val blindLatLng: Pair<Double, Double>? = null,
    val polylines: List<PolylineConfig> = emptyList(),
    val animatedMarker: Pair<Double, Double>? = null,
)

@Composable
fun GuideRunMap(
    state: GuideRunMapState,
    modifier: Modifier = Modifier,
) {
    val mapView = rememberMapViewWithLifecycle()
    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { mv ->
            val map = mv.map
            map.moveCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(state.centerLat, state.centerLng),
                    state.zoom,
                )
            )
            map.clear()
            state.volunteerLatLng?.let { (lat, lng) ->
                map.addMarker(MarkerOptions().position(LatLng(lat, lng)).title("志愿者"))
            }
            state.blindLatLng?.let { (lat, lng) ->
                map.addMarker(MarkerOptions().position(LatLng(lat, lng)).title("集合点"))
            }
            state.polylines.forEach { config ->
                if (config.points.size >= 2) {
                    map.addPolyline(
                        PolylineOptions()
                            .addAll(config.points.map { (lat, lng) -> LatLng(lat, lng) })
                            .color(config.colorHex.toColorInt())
                            .width(config.width)
                    )
                }
            }
            state.animatedMarker?.let { (lat, lng) ->
                map.addMarker(
                    MarkerOptions()
                        .position(LatLng(lat, lng))
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
