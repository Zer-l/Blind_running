package com.guiderun.server.domain

import com.guiderun.server.common.RunRequestAction
import com.guiderun.server.common.RunRequestAction.*
import com.guiderun.server.common.RunRequestStatus
import com.guiderun.server.common.RunRequestStatus.*
import com.guiderun.server.common.TriggeredRole
import com.guiderun.server.common.TriggeredRole.*
import com.guiderun.server.exception.AppException
import com.guiderun.server.exception.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component

@Component
class RunRequestStateMachine {

    private val log = LoggerFactory.getLogger(javaClass)

    private data class Key(val from: RunRequestStatus, val action: RunRequestAction, val role: TriggeredRole)

    // 合法转移矩阵。ABANDON 的目标状态由 abandonCount 动态决定，此处占位为 MATCHING。
    private val matrix: Map<Key, RunRequestStatus> = mapOf(
        Key(CREATED,  CREATE,      SYSTEM)    to MATCHING,
        Key(MATCHING, ACCEPT,      VOLUNTEER) to ACCEPTED,
        Key(ACCEPTED, DEPART,      VOLUNTEER) to EN_ROUTE,
        Key(EN_ROUTE, CONFIRM_MET, BLIND)     to MET,
        Key(EN_ROUTE, CONFIRM_MET, VOLUNTEER) to MET,
        Key(MET,      START_RUN,   VOLUNTEER) to RUNNING,
        Key(MET,      START_RUN,   BLIND)     to RUNNING,
        Key(RUNNING,  END_RUN,     VOLUNTEER) to FINISHED,
        Key(RUNNING,  END_RUN,     BLIND)     to FINISHED,
        // CANCEL 允许的状态和角色
        Key(MATCHING, CANCEL, BLIND)     to ABORTED,
        Key(MATCHING, CANCEL, SYSTEM)    to ABORTED,
        Key(EN_ROUTE, CANCEL, BLIND)     to ABORTED,
        Key(EN_ROUTE, CANCEL, VOLUNTEER) to ABORTED,
        Key(RUNNING,  CANCEL, BLIND)     to ABORTED,
        Key(RUNNING,  CANCEL, VOLUNTEER) to ABORTED,
        // ABANDON：只有志愿者，只在 ACCEPTED 阶段，目标由 abandonCount 决定
        Key(ACCEPTED, ABANDON, VOLUNTEER) to MATCHING,
        // RELEASE：视障用户主动更换志愿者，只在 ACCEPTED 阶段，回到 MATCHING
        Key(ACCEPTED, RELEASE, BLIND) to MATCHING,
        // EMERGENCY
        Key(RUNNING, EMERGENCY, BLIND)     to ABORTED,
        Key(RUNNING, EMERGENCY, VOLUNTEER) to ABORTED,
        // 系统触发关闭
        Key(FINISHED, REVIEW_COMPLETE, SYSTEM) to CLOSED,
    )

    /**
     * 校验状态转移合法性，返回目标状态。
     * - ABANDON 时根据 [abandonCount] 决定返回 MATCHING（< 3）或 ABORTED（≥ 3）。
     * - CONFIRM_MET 的幂等判断（已是 MET 时直接返回 MET）由 Service 层在调用前处理，不在此处。
     */
    fun validate(
        from: RunRequestStatus,
        action: RunRequestAction,
        actorRole: TriggeredRole,
        abandonCount: Int = 0,
    ): RunRequestStatus {
        matrix[Key(from, action, actorRole)]
            ?: throw AppException(
                ErrorCode.INVALID_STATE_TRANSITION,
                "订单当前状态 $from 不允许执行 $action（角色：$actorRole）",
                HttpStatus.BAD_REQUEST,
            )

        val toStatus = if (action == ABANDON) {
            if (abandonCount >= 3) ABORTED else MATCHING
        } else {
            matrix[Key(from, action, actorRole)]!!
        }

        log.debug("stateMachine: ({}, {}, {}) → {}", from, action, actorRole, toStatus)
        return toStatus
    }
}
