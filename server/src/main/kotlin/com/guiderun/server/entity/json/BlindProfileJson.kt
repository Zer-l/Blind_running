package com.guiderun.server.entity.json

/** 视障端资料，作为 JSON 字段嵌入 [com.guiderun.server.entity.UserEntity]。 */
data class BlindProfileJson(
    val visionLevel: String,
    val preferredPaceSeconds: Int? = null,
    val preferredDurationMinutes: Int? = null,
    val emergencyContacts: List<EmergencyContactJson> = emptyList(),
    val medicalNotes: String? = null,
    val visualDescription: String? = null,
    val voiceProfileUrl: String? = null,
)

/** 紧急联系人嵌入对象，列表上限 5 条（业务规则在 UserService 中强制）。 */
data class EmergencyContactJson(
    val name: String,
    val phone: String,
    val relationship: String,
)
