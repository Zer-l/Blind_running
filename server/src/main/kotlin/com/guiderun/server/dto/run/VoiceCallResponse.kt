package com.guiderun.server.dto.run

data class VoiceCallResponse(
    val requestId: String,
    val otherPartyPhone: String,
    val status: String,
)
