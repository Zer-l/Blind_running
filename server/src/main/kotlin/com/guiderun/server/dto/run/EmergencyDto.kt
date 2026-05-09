package com.guiderun.server.dto.run

data class EmergencyGeoDto(val lat: Double, val lng: Double)

data class EmergencyDto(
    val reason: String? = null,
    val currentLocation: EmergencyGeoDto? = null,
    val timestamp: Long? = null,
)
