package com.guiderun.server.common

enum class Gender { MALE, FEMALE, OTHER }

enum class UserRole { BLIND_RUNNER, VOLUNTEER }

enum class UserStatus { ACTIVE, FROZEN, BANNED }

enum class ProvisioningStatus { PENDING_ROLE, ACTIVE }

enum class RunRequestStatus {
    CREATED, MATCHING, ACCEPTED, EN_ROUTE, MET, RUNNING, FINISHED, CLOSED, ABORTED;
    fun isTerminal() = this == CLOSED || this == ABORTED
    fun isActive() = !isTerminal()
}

enum class RunRequestAction {
    CREATE, ACCEPT, DEPART, CONFIRM_MET, START_RUN, END_RUN,
    CANCEL, ABANDON, RELEASE, EMERGENCY, REVIEW_COMPLETE
}

enum class AbortBy { BLIND, VOLUNTEER, SYSTEM, ADMIN }

// "事件触发方角色"，语义不同于 UserRole（多了 SYSTEM/ADMIN，且用短名 BLIND 而非 BLIND_RUNNER）
enum class TriggeredRole { BLIND, VOLUNTEER, SYSTEM, ADMIN }

fun UserRole.toTriggeredRole() = when (this) {
    UserRole.BLIND_RUNNER -> TriggeredRole.BLIND
    UserRole.VOLUNTEER    -> TriggeredRole.VOLUNTEER
}
