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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.guiderun.app.domain.model.RunRequest
import com.guiderun.app.domain.model.UserRole
import com.guiderun.app.ui.MainViewModel
import com.guiderun.app.ui.blind.BlindActivity
import com.guiderun.app.ui.navigation.ActiveOrderRouter
import com.guiderun.app.ui.navigation.AppNavGraph
import com.guiderun.app.ui.navigation.Screen
import com.guiderun.app.ui.theme.GuideRunTheme
import com.guiderun.app.ui.theme.getPresetTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * 主入口 Activity（志愿者端 Compose 导航宿主）。
 *
 * 视障端相关的辅助能力（TTS / 震动 / 音量键长按语音 / 三连击 SOS / 三连击拨号 / 全局语音指令）
 * 完全不在此处启用：视障跑者用户登录后会通过 HomeScreen.LaunchedEffect 跳转到 [BlindActivity]，
 * 由 BaseBlindActivity 提供这些视障端专属能力。志愿者用户保持纯 Compose UI + 系统标准交互。
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
            val themeId by viewModel.themeId.collectAsState()
            GuideRunTheme(appColorScheme = getPresetTheme(themeId)) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val startDestination by viewModel.startDestination.collectAsStateWithLifecycle()
                    val navController = rememberNavController()

                    // 持有 navController 引用，方便 onNewIntent 在 Compose 之外触发路由
                    LaunchedEffect(navController) { pendingResumeNavController = navController }

                    LaunchedEffect(Unit) {
                        viewModel.authEvents.collect {
                            navController.navigate(Screen.Login.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    }

                    // 处理"通知拉回"延迟到达 Compose 时的恢复请求（onCreate 触发的场景）
                    LaunchedEffect(navController, startDestination) {
                        if (startDestination == null || startDestination == Screen.Login.route) return@LaunchedEffect
                        intent?.getStringExtra(EXTRA_RECOVERY_REQUEST_ID)?.let {
                            handleRecoveryIntent(navController)
                        }
                    }

                    if (startDestination != null) {
                        AppNavGraph(
                            navController = navController,
                            startDestination = startDestination!!,
                            onEnterBlindHome = { BlindActivity.start(this@MainActivity, BlindActivity.DEST_HOME) },
                            onEnterBlindFlow = { BlindActivity.start(this@MainActivity, BlindActivity.DEST_CREATE_REQUEST) },
                            onEnterBlindSettings = { BlindActivity.start(this@MainActivity, BlindActivity.DEST_SETTINGS) },
                            onEnterBlindHistory = { BlindActivity.start(this@MainActivity, BlindActivity.DEST_HISTORY) },
                            onQuickStartBlindFlow = { BlindActivity.startForQuickStart(this@MainActivity) },
                            onResumeActiveOrder = { request ->
                                routeToActiveOrder(request, viewModel.currentRole(), navController)
                            },
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }

    /**
     * 把活跃订单路由到对应页面：
     * - 视障订单 → BlindActivity 起始 destination 设为对应 Fragment
     * - 志愿者订单 → Compose NavController 跳转到对应 Screen（清理回退栈到 Home）
     */
    private fun routeToActiveOrder(
        request: RunRequest,
        role: UserRole?,
        navController: NavHostController,
    ) {
        if (request.status.isTerminal()) return
        val isBlind = when (role) {
            UserRole.BLIND_RUNNER -> true
            UserRole.VOLUNTEER    -> false
            else                  -> false
        }
        if (isBlind) {
            BlindActivity.startForRecovery(this, request.id, request.status.name)
        } else {
            ActiveOrderRouter.volunteerRoute(request.status, request.id)?.let { route ->
                navController.navigate(route) {
                    popUpTo(Screen.Home.route) { inclusive = false }
                    launchSingleTop = true
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getStringExtra(EXTRA_RECOVERY_REQUEST_ID) == null) return
        // 通知点击 / 横幅再次触发：Activity 已存在，直接读当前活跃订单并路由
        val navController = pendingResumeNavController ?: return
        handleRecoveryIntent(navController)
    }

    private fun handleRecoveryIntent(navController: NavHostController) {
        lifecycleScope.launch {
            // 强制刷一次远端，再读当前 activeRequest（已被 Repository 内部 trackActive 更新）
            viewModel.refreshActiveRequest()
            val request = viewModel.activeRequestNow() ?: return@launch
            routeToActiveOrder(request, viewModel.currentRole(), navController)
            // 消费 intent 防止再次进入此 Activity 时重复跳转
            intent.removeExtra(EXTRA_RECOVERY_REQUEST_ID)
        }
    }
}
