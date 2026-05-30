package com.guiderun.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.guiderun.app.domain.model.RunRequest
import com.guiderun.app.ui.auth.LoginScreen
import com.guiderun.app.ui.auth.RoleSelectScreen
import com.guiderun.app.ui.volunteer.VolunteerHomeScreen
import com.guiderun.app.ui.volunteer.VolunteerMetScreen
import com.guiderun.app.ui.volunteer.VolunteerNavigatingScreen
import com.guiderun.app.ui.volunteer.VolunteerRequestDetailScreen
import com.guiderun.app.ui.volunteer.VolunteerTrackPlaybackScreen
import com.guiderun.app.ui.volunteer.VolunteerHistoryScreen
import com.guiderun.app.ui.volunteer.VolunteerProfileEditScreen
import com.guiderun.app.ui.volunteer.VolunteerSettingsScreen
import com.guiderun.app.ui.volunteer.VolunteerThemeSelectionScreen
import com.guiderun.app.ui.volunteer.VolunteerOrderListScreen
import com.guiderun.app.ui.volunteer.VolunteerReviewScreen
import com.guiderun.app.ui.volunteer.VolunteerRunningScreen

/**
 * 志愿者侧 + 公共（Login / RoleSelect）的 Compose 导航图。
 *
 * 视障端**不**挂在本图里：[com.guiderun.app.MainActivity] 在 [com.guiderun.app.ui.StartTarget.BlindHome]
 * 分支会直接启动 [com.guiderun.app.ui.blind.BlindActivity] 并 finish 自身，
 * 视障流程完全在 BlindActivity 的 `blind_nav_graph.xml` 内。
 *
 * @param onLoginCompleted Login / RoleSelect 完成后调用：由 MainActivity 重新解析 token+role，
 *   按新角色直接分派（视障 → 启动 BlindActivity 并 finish；志愿者 → navigate VolunteerHome）。
 */
@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: String,
    onLoginCompleted: () -> Unit,
    onResumeActiveOrder: (RunRequest) -> Unit = {},
) {
    NavHost(navController = navController, startDestination = startDestination) {

        composable(Screen.Login.route) {
            LoginScreen(
                onNavigateToRoleSelect = {
                    navController.navigate(Screen.RoleSelect.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateToHome = onLoginCompleted,
            )
        }

        composable(Screen.RoleSelect.route) {
            RoleSelectScreen(
                onNavigateToHome = onLoginCompleted,
            )
        }

        composable(Screen.VolunteerHome.route) {
            VolunteerHomeScreen(
                onLoggedOut = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onEnterVolunteerFlow = {
                    navController.navigate(Screen.OrderList.route)
                },
                onNavigateToProfile = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToHistory = {
                    navController.navigate(Screen.VolunteerHistory.route)
                },
                onResumeActiveOrder = onResumeActiveOrder,
            )
        }

        composable(Screen.OrderList.route) {
            VolunteerOrderListScreen(
                onNavigateToDetail = { requestId ->
                    navController.navigate(Screen.RequestDetail.createRoute(requestId))
                },
                onResumeActiveOrder = onResumeActiveOrder,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Screen.VolunteerProfileEdit.route) {
            VolunteerProfileEditScreen(
                onSaved = { navController.popBackStack() },
            )
        }

        composable(Screen.Settings.route) {
            VolunteerSettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToProfile = { navController.navigate(Screen.VolunteerProfileEdit.route) },
                onNavigateToTheme = { navController.navigate(Screen.ThemeSelection.route) },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.VolunteerHome.route) { inclusive = true }
                    }
                },
            )
        }

        composable(Screen.ThemeSelection.route) {
            VolunteerThemeSelectionScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(Screen.VolunteerHistory.route) {
            VolunteerHistoryScreen(
                onBack = { navController.popBackStack() },
                onNavigateToTrackPlayback = { requestId ->
                    // 志愿者侧入口固定 role=VOLUNTEER，只回放自己采集的轨迹
                    navController.navigate(Screen.TrackPlayback.createRoute(requestId, "VOLUNTEER"))
                },
                onNavigateToReview = { requestId ->
                    navController.navigate(Screen.VolunteerReview.createRoute(requestId))
                },
            )
        }

        composable(
            route = Screen.RequestDetail.route,
            arguments = listOf(navArgument("requestId") { type = NavType.StringType }),
        ) {
            VolunteerRequestDetailScreen(
                onNavigateToNavigating = { requestId ->
                    navController.navigate(Screen.Navigating.createRoute(requestId)) {
                        popUpTo(Screen.RequestDetail.route) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Screen.Navigating.route,
            arguments = listOf(navArgument("requestId") { type = NavType.StringType }),
        ) {
            VolunteerNavigatingScreen(
                onNavigateToMet = { requestId ->
                    navController.navigate(Screen.Met.createRoute(requestId)) {
                        popUpTo(Screen.Navigating.route) { inclusive = true }
                    }
                },
                onNavigateToHome = {
                    navController.navigate(Screen.VolunteerHome.route) {
                        popUpTo(Screen.VolunteerHome.route) { inclusive = true }
                    }
                },
            )
        }

        composable(
            route = Screen.Met.route,
            arguments = listOf(navArgument("requestId") { type = NavType.StringType }),
        ) {
            VolunteerMetScreen(
                onNavigateToHome = {
                    navController.navigate(Screen.VolunteerHome.route) {
                        popUpTo(Screen.VolunteerHome.route) { inclusive = true }
                    }
                },
                onNavigateToRunning = { requestId ->
                    navController.navigate(Screen.VolunteerRunning.createRoute(requestId)) {
                        popUpTo(Screen.Met.route) { inclusive = true }
                    }
                },
            )
        }

        composable(
            route = Screen.VolunteerRunning.route,
            arguments = listOf(navArgument("requestId") { type = NavType.StringType }),
        ) {
            VolunteerRunningScreen(
                requestId = it.arguments?.getString("requestId") ?: "",
                onNavigateToReview = { requestId ->
                    navController.navigate(Screen.VolunteerReview.createRoute(requestId)) {
                        popUpTo(Screen.VolunteerRunning.route) { inclusive = true }
                    }
                },
                onNavigateToHome = {
                    navController.navigate(Screen.VolunteerHome.route) {
                        popUpTo(Screen.VolunteerHome.route) { inclusive = true }
                    }
                },
            )
        }

        composable(
            route = Screen.VolunteerReview.route,
            arguments = listOf(navArgument("requestId") { type = NavType.StringType }),
        ) {
            // 评价完成后的返回策略：从历史页补评 → popBackStack 回历史；正常跑步结束 → 清栈回首页。
            // VolunteerRunning → VolunteerReview 用了 popUpTo VolunteerRunning inclusive=true，
            // 所以正常流程的 previousBackStackEntry 不会是 VolunteerHistory。
            val cameFromHistory = navController.previousBackStackEntry?.destination?.route ==
                Screen.VolunteerHistory.route
            VolunteerReviewScreen(
                requestId = it.arguments?.getString("requestId") ?: "",
                onNavigateToHome = {
                    if (cameFromHistory) {
                        navController.popBackStack()
                    } else {
                        navController.navigate(Screen.VolunteerHome.route) {
                            popUpTo(Screen.VolunteerHome.route) { inclusive = true }
                        }
                    }
                },
            )
        }

        composable(
            route = Screen.TrackPlayback.route,
            arguments = listOf(
                navArgument("requestId") { type = NavType.StringType },
                navArgument("role") { type = NavType.StringType },
            ),
        ) {
            VolunteerTrackPlaybackScreen(
                requestId = it.arguments?.getString("requestId") ?: "",
                role = it.arguments?.getString("role") ?: "VOLUNTEER",
                onBack = { navController.popBackStack() },
            )
        }
    }
}
