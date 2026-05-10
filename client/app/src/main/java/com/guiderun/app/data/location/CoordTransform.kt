package com.guiderun.app.data.location

import android.content.Context
import com.amap.api.maps.CoordinateConverter
import com.amap.api.maps.CoordinateConverter.CoordType
import com.amap.api.maps.model.LatLng

/**
 * 坐标系转换：WGS-84（系统定位 / 业务数据层）→ GCJ-02（高德地图渲染）。
 *
 * 设计约束：
 * - 系统定位 API（FusedLocationProviderClient / LocationManager）在中国大陆返回 WGS-84；
 * - 高德地图底图使用 GCJ-02。直接把 WGS-84 喂给地图 SDK 会出现「火星坐标」偏移（数百米）；
 * - 业务数据层（Room、网络、距离计算、轨迹存储）统一保持 WGS-84，仅在地图渲染这一 UI 边界做转换，
 *   避免坐标语义在数据层被污染。
 *
 * 单点转换使用 [toGcj02LatLng]；同一帧内多点转换（如轨迹折线、多 Marker）使用
 * [Wgs84ToGcj02Converter] 复用底层 [CoordinateConverter] 实例以减少对象分配。
 */
fun toGcj02LatLng(context: Context, lat: Double, lng: Double): LatLng =
    CoordinateConverter(context)
        .from(CoordType.GPS)
        .coord(LatLng(lat, lng))
        .convert()

/**
 * 可复用的批量坐标转换器。适合折线、轨迹回放等单帧多点场景。
 *
 * 注意：[CoordinateConverter] 内部不是线程安全的，本类同样只能在调用方线程（通常是主线程）使用。
 */
class Wgs84ToGcj02Converter(context: Context) {
    private val converter = CoordinateConverter(context).from(CoordType.GPS)

    fun convert(lat: Double, lng: Double): LatLng =
        converter.coord(LatLng(lat, lng)).convert()
}
