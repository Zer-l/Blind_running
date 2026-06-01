package com.guiderun.server.entity.json

/** 志愿者端资料，作为 JSON 字段嵌入 [com.guiderun.server.entity.UserEntity]。 */
data class VolunteerProfileJson(
    val averagePaceSeconds: Int? = null,
    val runningLevel: String? = null,
    val hasGuideExperience: Boolean = false,
    val trainingCompleted: Boolean = false,
    val trainingPassedAt: String? = null,
    val availableTimeSlots: List<TimeSlotJson> = emptyList(),
)

/** 志愿者可服务时段（周几 + 起止小时），预留字段当前未启用。 */
data class TimeSlotJson(
    val dayOfWeek: Int,
    val startHour: Int,
    val endHour: Int,
)
