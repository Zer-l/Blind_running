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
import com.guiderun.app.ui.home.HomeScreen
import com.guiderun.app.ui.volunteer.MetScreen
import com.guiderun.app.ui.volunteer.NavigatingScreen
import com.guiderun.app.ui.volunteer.RequestDetailScreen
import com.guiderun.app.ui.volunteer.TrackPlaybackScreen
import com.guiderun.app.ui.volunteer.VolunteerHistoryScreen
import com.guiderun.app.ui.profile.VolunteerProfileEditScreen
import com.guiderun.app.ui.volunteer.VolunteerOrderListScreen
import com.guiderun.app.ui.volunteer.VolunteerReviewScreen
import com.guiderun.app.ui.volunteer.VolunteerRunningScreen

@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: String,
    onEnterBlindFlow: () -> Unit,
    onEnterBlindSettings: () -> Unit = {},
    onEnterBlindHistory: () -> Unit = {},
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
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
            )
        }

        composable(Screen.RoleSelect.route) {
            RoleSelectScreen(
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.RoleSelect.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onLoggedOut = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onEnterBlindFlow = onEnterBlindFlow,
                onEnterBlindSettings = onEnterBlindSettings,
                onEnterBlindHistory = onEnterBlindHistory,
                onEnterVolunteerFlow = {
                    navController.navigate(Screen.OrderList.route)
                },
                onNavigateToProfile = {
                    navController.navigate(Screen.VolunteerProfileEdit.route)
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

        composable(Screen.VolunteerHistory.route) {
            VolunteerHistoryScreen(
                onBack = { navController.popBackStack() },
                onNavigateToTrackPlayback = { requestId ->
                    navController.navigate(Screen.TrackPlayback.createRoute(requestId))
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
            RequestDetailScreen(
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
            NavigatingScreen(
                onNavigateToMet = { requestId ->
                    navController.navigate(Screen.Met.createRoute(requestId)) {
                        popUpTo(Screen.Navigating.route) { inclusive = true }
                    }
                },
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
            )
        }

        composable(
            route = Screen.Met.route,
            arguments = listOf(navArgument("requestId") { type = NavType.StringType }),
        ) {
            MetScreen(
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
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
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
            )
        }

        composable(
            route = Screen.VolunteerReview.route,
            arguments = listOf(navArgument("requestId") { type = NavType.StringType }),
        ) {
            VolunteerReviewScreen(
                requestId = it.arguments?.getString("requestId") ?: "",
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
            )
        }

        composable(
            route = Screen.TrackPlayback.route,
            arguments = listOf(navArgument("requestId") { type = NavType.StringType }),
        ) {
            TrackPlaybackScreen(
                requestId = it.arguments?.getString("requestId") ?: "",
                onBack = { navController.popBackStack() },
            )
        }
    }
}
