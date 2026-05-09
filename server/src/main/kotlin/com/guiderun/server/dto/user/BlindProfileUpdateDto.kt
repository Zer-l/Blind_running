package com.guiderun.server.dto.user

import jakarta.validation.constraints.Size

data class BlindProfileUpdateDto(
    @field:Size(max = 20, message = "视障级别最多20个字符")
    val visionLevel: String? = null,
    val preferredPaceSeconds: Int? = null,
    val preferredDurationMinutes: Int? = null,
    @field:Size(max = 500, message = "医疗备注最多500个字符")
    val medicalNotes: String? = null,
    @field:Size(max = 200, message = "外观描述最多200个字符")
    val visualDescription: String? = null,
)
