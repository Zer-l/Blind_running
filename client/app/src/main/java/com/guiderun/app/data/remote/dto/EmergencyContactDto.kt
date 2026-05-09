package com.guiderun.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class EmergencyContactDto(
    val name: String,
    val phone: String,
    val relationship: String,
)
