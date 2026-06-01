package com.guiderun.server.dto.run

/** 紧急事件携带的经纬度坐标（精简版，不复用 GeoPointDto 以避免必填 description）。 */
data class EmergencyGeoDto(val lat: Double, val lng: Double)

/** 紧急求助请求体：原因 + 当前位置 + 触发时间戳，全部可选（兼容仅按键触发场景）。 */
data class EmergencyDto(
    val reason: String? = null,
    val currentLocation: EmergencyGeoDto? = null,
    val timestamp: Long? = null,
)
