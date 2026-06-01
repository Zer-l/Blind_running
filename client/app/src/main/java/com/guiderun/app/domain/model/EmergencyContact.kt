package com.guiderun.app.domain.model

/**
 * 紧急联系人（SOS 时自动拨打的联系人信息）。
 *
 * 存储在服务端用户资料里，SosCoordinator 触发紧急求助时按列表顺序依次拨打。
 */
data class EmergencyContact(
    val name: String,
    val phone: String,
    val relationship: String,
)
