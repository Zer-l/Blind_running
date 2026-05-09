package com.guiderun.server.entity.json

data class BlindProfileJson(
    val visionLevel: String,
    val preferredPaceSeconds: Int? = null,
    val preferredDurationMinutes: Int? = null,
    val emergencyContacts: List<EmergencyContactJson> = emptyList(),
    val medicalNotes: String? = null,
    val visualDescription: String? = null,
    val voiceProfileUrl: String? = null,
)

data class EmergencyContactJson(
    val name: String,
    val phone: String,
    val relationship: String,
)
