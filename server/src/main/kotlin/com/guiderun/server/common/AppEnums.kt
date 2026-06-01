package com.guiderun.server.common

/** 性别枚举。 */
enum class Gender { MALE, FEMALE, OTHER }

/** 用户角色：同一 User 可同时持有多角色，存表时以逗号分隔字符串保存。 */
enum class UserRole { BLIND_RUNNER, VOLUNTEER }

/** 账户状态：FROZEN/BANNED 由 Admin 后台触发，登录与发单接口需要拦截。 */
enum class UserStatus { ACTIVE, FROZEN, BANNED }

/** 账户开通进度：注册后先 PENDING_ROLE，选定角色（首次 setRoles）后转 ACTIVE。 */
enum class ProvisioningStatus { PENDING_ROLE, ACTIVE }

enum class RunRequestStatus {
    CREATED, MATCHING, ACCEPTED, EN_ROUTE, MET, RUNNING, FINISHED, CLOSED, ABORTED;

    /**
     * 终态包含 FINISHED：跑步结束即视为订单完成，评价独立于订单生命周期，
     * 不再阻塞 getActiveRequest。FINISHED→CLOSED 仍由 24h 定时器 / 双方评价完成触发用于落统计字段。
     */
    fun isTerminal() = this == CLOSED || this == ABORTED || this == FINISHED
    fun isActive() = !isTerminal()
}

/** 状态机驱动动作枚举，对应 [com.guiderun.server.domain.RunRequestStateMachine] 中的转移规则。 */
enum class RunRequestAction {
    CREATE, ACCEPT, DEPART, CONFIRM_MET, START_RUN, END_RUN, REQUEST_END_RUN,
    CANCEL, ABANDON, EMERGENCY, REVIEW_COMPLETE
}

/** 订单异常终止发起方。 */
enum class AbortBy { BLIND, VOLUNTEER, SYSTEM, ADMIN }

// "事件触发方角色"，语义不同于 UserRole（多了 SYSTEM/ADMIN，且用短名 BLIND 而非 BLIND_RUNNER）
enum class TriggeredRole { BLIND, VOLUNTEER, SYSTEM, ADMIN }

fun UserRole.toTriggeredRole() = when (this) {
    UserRole.BLIND_RUNNER -> TriggeredRole.BLIND
    UserRole.VOLUNTEER    -> TriggeredRole.VOLUNTEER
}
