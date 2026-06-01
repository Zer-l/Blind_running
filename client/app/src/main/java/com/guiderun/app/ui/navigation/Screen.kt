package com.guiderun.app.ui.navigation

/**
 * 志愿者端（Compose）所有路由定义。
 *
 * 带参数的路由（RequestDetail / Navigating / Met / VolunteerRunning / VolunteerReview / TrackPlayback）
 * 通过 createRoute(id) 生成具体 URL，避免字符串拼接散落在各处调用方。
 *
 * 视障端路由不在此处：视障端使用 XML Fragment Navigation（blind_nav_graph.xml）。
 */
sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object RoleSelect : Screen("role_select")

    /** 志愿者侧 Compose 首页路由。视障端走独立 [com.guiderun.app.ui.blind.BlindActivity]，不挂在 Compose 图里。 */
    data object VolunteerHome : Screen("volunteer_home")

    // Volunteer flow
    /** 志愿者接单列表（"志愿者主战场"，不是欢迎页；欢迎页是 [Home]）。 */
    data object OrderList : Screen("order_list")
    data object VolunteerProfileEdit : Screen("volunteer_profile_edit")
    data object Settings : Screen("settings")
    data object ThemeSelection : Screen("theme_selection")
    data object RequestDetail : Screen("request_detail/{requestId}") {
        fun createRoute(requestId: String) = "request_detail/$requestId"
    }
    data object Navigating : Screen("navigating/{requestId}") {
        fun createRoute(requestId: String) = "navigating/$requestId"
    }
    data object Met : Screen("met/{requestId}") {
        fun createRoute(requestId: String) = "met/$requestId"
    }
    data object VolunteerRunning : Screen("volunteer_running/{requestId}") {
        fun createRoute(requestId: String) = "volunteer_running/$requestId"
    }
    data object VolunteerReview : Screen("volunteer_review/{requestId}") {
        fun createRoute(requestId: String) = "volunteer_review/$requestId"
    }
    data object VolunteerHistory : Screen("volunteer_history")
    data object TrackPlayback : Screen("track_playback/{requestId}/{role}") {
        /** role 必填，区分 BLIND / VOLUNTEER；用于双端各自只看自己的轨迹 */
        fun createRoute(requestId: String, role: String) = "track_playback/$requestId/$role"
    }
}
