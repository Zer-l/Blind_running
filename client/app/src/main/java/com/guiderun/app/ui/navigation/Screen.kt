package com.guiderun.app.ui.navigation

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object RoleSelect : Screen("role_select")
    data object Home : Screen("home")

    // Volunteer flow
    /** 志愿者接单列表（"志愿者主战场"，不是欢迎页；欢迎页是 [Home]）。 */
    data object OrderList : Screen("order_list")
    data object VolunteerProfileEdit : Screen("volunteer_profile_edit")
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
    data object TrackPlayback : Screen("track_playback/{requestId}") {
        fun createRoute(requestId: String) = "track_playback/$requestId"
    }
}
