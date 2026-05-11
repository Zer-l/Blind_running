package com.guiderun.app.ui.navigation

import com.guiderun.app.R
import com.guiderun.app.domain.model.RunRequestStatus

/**
 * 把订单当前状态映射到目标导航位置：
 * - 视障端 → blind_nav_graph 的 fragment id（用于 BlindActivity setStartDestination）
 * - 志愿者端 → AppNavGraph 的 Compose route 字符串
 *
 * 终态（CLOSED/ABORTED）返回 null，调用方应直接返回首页。
 */
object ActiveOrderRouter {

    /**
     * 视障端订单恢复目标 fragment id。终态（含 FINISHED）返回 null。
     * FINISHED 视为订单已完成，评价独立于订单生命周期，不再作为冷启动恢复目标。
     */
    fun blindFragmentId(status: RunRequestStatus): Int? = when (status) {
        RunRequestStatus.MATCHING                                     -> R.id.blindWaitingMatchFragment
        RunRequestStatus.ACCEPTED, RunRequestStatus.EN_ROUTE,
        RunRequestStatus.MET                                          -> R.id.blindMatchedFragment
        RunRequestStatus.RUNNING                                      -> R.id.blindRunningFragment
        RunRequestStatus.CREATED, RunRequestStatus.FINISHED,
        RunRequestStatus.CLOSED, RunRequestStatus.ABORTED             -> null
    }

    /**
     * 志愿者端订单恢复目标 route，需带 requestId 参数。终态（含 FINISHED）返回 null。
     */
    fun volunteerRoute(status: RunRequestStatus, requestId: String): String? = when (status) {
        RunRequestStatus.MATCHING -> Screen.RequestDetail.createRoute(requestId)
        RunRequestStatus.ACCEPTED, RunRequestStatus.EN_ROUTE -> Screen.Navigating.createRoute(requestId)
        RunRequestStatus.MET                                 -> Screen.Met.createRoute(requestId)
        RunRequestStatus.RUNNING                             -> Screen.VolunteerRunning.createRoute(requestId)
        RunRequestStatus.CREATED, RunRequestStatus.FINISHED,
        RunRequestStatus.CLOSED, RunRequestStatus.ABORTED    -> null
    }
}
