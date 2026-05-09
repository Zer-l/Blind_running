package com.guiderun.server.entity.json

data class VolunteerProfileJson(
    val averagePaceSeconds: Int? = null,
    val runningLevel: String? = null,
    val hasGuideExperience: Boolean = false,
    val trainingCompleted: Boolean = false,
    val trainingPassedAt: String? = null,
    val availableTimeSlots: List<TimeSlotJson> = emptyList(),
)

data class TimeSlotJson(
    val dayOfWeek: Int,
    val startHour: Int,
    val endHour: Int,
)
