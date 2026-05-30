package com.guiderun.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.guiderun.app.domain.model.RunRequest
import com.guiderun.app.domain.model.UserRole
import com.guiderun.app.ui.navigation.MainViewModel
import com.guiderun.app.ui.navigation.StartTarget
import com.guiderun.app.ui.blind.BlindActivity
import com.guiderun.app.ui.navigation.ActiveOrderRouter
import com.guiderun.app.ui.navigation.AppNavGraph
import com.guiderun.app.ui.navigation.Screen
import com.guiderun.app.ui.theme.GuideRunTheme
import com.guiderun.app.ui.theme.getPresetTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * 主入口 Activity。
 *
 * 启动后由 [MainViewModel.startTarget] 决定渲染分支：
 * - [StartTarget.Login] → Compose NavHost，起始 [Screen.Login]
 * - [StartTarget.VolunteerHome] → Compose NavHost，起始 [Screen.VolunteerHome]
 * - [StartTarget.BlindHome] → 立即启动 [BlindActivity]（默认目的地 [BlindActivity.DEST_HOME]）并 finish 自身，
 *   不渲染任何 Compose UI；视障端的无障碍能力（TTS / 震动 / 音量键长按语音 / 三连击 SOS / 拨号）
 *   完全由 BlindActivity 提供。
 *
 * 登录/角色选择完成后（[Screen.Login] / [Screen.RoleSelect]）通过 [onLoginCompleted] 触发
 * [MainViewModel.reresolveAfterLogin]，按新角色再次分派——避免登录后还要"先回 VolunteerHome
 * 再跳板到 BlindActivity"的视觉断裂。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var pendingResumeNavController: NavHostController? = null

    companion object {
        /** 由前台服务通知 / 横幅点击传入，启动后路由到对应志愿者流程页。 */
        const val EXTRA_RECOVERY_REQUEST_ID = "recovery_request_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeId by viewModel.themeId.collectAsStateWithLifecycle()
            GuideRunTheme(appColorScheme = getPresetTheme(themeId)) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val target by viewModel.startTarget.collectAsStateWithLifecycle()
                    when (target) {
                        null -> LoadingPlaceholder()
                        StartTarget.BlindHome -> {
                            // 视障路径：立即启动 BlindActivity 并退出本 Activity
                            LaunchedEffect(Unit) {
                                BlindActivity.start(this@MainActivity, BlindActivity.DEST_HOME)
                                finish()
                            }
                            LoadingPlaceholder()
                        }
                        StartTarget.Login, StartTarget.VolunteerHome -> {
                            ComposeNavRoot(target as StartTarget)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ComposeNavRoot(target: StartTarget) {
        val navController = rememberNavController()
        LaunchedEffect(navController) { pendingResumeNavController = navController }

        // 401 自动登出：仅 MainActivity 存活时有效。视障路径中本 Activity 已 finish，
        // 视障端登出由 BlindSettingsFragment 显式 startActivity(MainActivity, CLEAR_TASK) 重启回 Login。
        LaunchedEffect(Unit) {
            viewModel.authEvents.collect {
                navController.navigate(Screen.Login.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }

        // 处理"通知拉回"延迟到达 Compose 时的恢复请求（仅志愿者路径）
        LaunchedEffect(navController, target) {
            if (target != StartTarget.VolunteerHome) return@LaunchedEffect
            intent?.getStringExtra(EXTRA_RECOVERY_REQUEST_ID)?.let {
                handleRecoveryIntent(navController)
            }
        }

        val start = when (target) {
            StartTarget.Login -> Screen.Login.route
            StartTarget.VolunteerHome -> Screen.VolunteerHome.route
            // 不可达：BlindHome 已在外层 when 分支处理
            StartTarget.BlindHome -> Screen.Login.route
        }
        AppNavGraph(
            navController = navController,
            startDestination = start,
            onLoginCompleted = { handleLoginCompleted(navController) },
            onResumeActiveOrder = { request ->
                routeToActiveOrder(request, viewModel.currentRole(), navController)
            },
        )
    }

    @Composable
    private fun LoadingPlaceholder() {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }

    /** 登录 / 角色选择完成后：重读 token+role，按角色直接分派，跳过 Compose 跳板。 */
    private fun handleLoginCompleted(navController: NavHostController) {
        lifecycleScope.launch {
            when (viewModel.reresolveAfterLogin()) {
                StartTarget.BlindHome -> {
                    // 不显式 start：reresolveAfterLogin 已更新 _startTarget，
                    // Compose 外层 when 切到 BlindHome 分支后 LaunchedEffect 会负责 start + finish()。
                    // 之前在此处再调一次 start 会导致 standard launchMode 下压入两个 BlindActivity 实例
                    // → BlindHomeFragment 双重 onResume → 欢迎语播两次。
                }
                StartTarget.VolunteerHome -> {
                    navController.navigate(Screen.VolunteerHome.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
                StartTarget.Login -> {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
        }
    }

    /**
     * 把活跃订单路由到对应页面：
     * - 视障订单 → BlindActivity 起始 destination 设为对应 Fragment
     * - 志愿者订单 → Compose NavController 跳转到对应 Screen（清理回退栈到 VolunteerHome）
     */
    private fun routeToActiveOrder(
        request: RunRequest,
        role: UserRole?,
        navController: NavHostController,
    ) {
        if (request.status.isTerminal()) return
        val isBlind = role == UserRole.BLIND_RUNNER
        if (isBlind) {
            BlindActivity.startForRecovery(this, request.id, request.status.name)
        } else {
            ActiveOrderRouter.volunteerRoute(request.status, request.id)?.let { route ->
                navController.navigate(route) {
                    popUpTo(Screen.VolunteerHome.route) { inclusive = false }
                    launchSingleTop = true
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getStringExtra(EXTRA_RECOVERY_REQUEST_ID) == null) return
        val navController = pendingResumeNavController ?: return
        handleRecoveryIntent(navController)
    }

    private fun handleRecoveryIntent(navController: NavHostController) {
        lifecycleScope.launch {
            viewModel.refreshActiveRequest()
            val request = viewModel.activeRequestNow() ?: return@launch
            routeToActiveOrder(request, viewModel.currentRole(), navController)
            intent.removeExtra(EXTRA_RECOVERY_REQUEST_ID)
        }
    }
}
