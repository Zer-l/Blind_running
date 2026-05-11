package com.guiderun.app.ui.blind

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.addCallback
import androidx.core.os.bundleOf
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.guiderun.app.R
import com.guiderun.app.domain.model.RunRequestStatus
import com.guiderun.app.ui.navigation.ActiveOrderRouter
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class BlindActivity : BaseBlindActivity() {

    companion object {
        private const val EXTRA_DESTINATION = "destination"

        const val DEST_CREATE_REQUEST = "create_request"
        const val DEST_HISTORY = "history"
        const val DEST_SETTINGS = "settings"

        /** 从外部（前台服务通知、首页横幅、冷启动 UseCase）传入活跃订单 id 触发恢复。 */
        const val EXTRA_RECOVERY_REQUEST_ID = "recovery_request_id"

        /** 从外部传入活跃订单状态 name，与 [EXTRA_RECOVERY_REQUEST_ID] 配合决定起始 fragment。 */
        const val EXTRA_RECOVERY_STATUS = "recovery_status"

        fun start(context: Context, destination: String = DEST_CREATE_REQUEST) {
            val intent = Intent(context, BlindActivity::class.java).apply {
                putExtra(EXTRA_DESTINATION, destination)
            }
            context.startActivity(intent)
        }

        /** 直接进入指定订单的恢复目的地（由 RecoverActiveRequestUseCase 或通知点击触发）。 */
        fun startForRecovery(context: Context, requestId: String, statusName: String) {
            val intent = Intent(context, BlindActivity::class.java).apply {
                putExtra(EXTRA_RECOVERY_REQUEST_ID, requestId)
                putExtra(EXTRA_RECOVERY_STATUS, statusName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(intent)
        }
    }

    private var navController: NavController? = null
    private var isStartDestination = true

    /** 统一返回首页：清空 Fragment 栈并 finish Activity，回到 MainActivity 的 HomeScreen */
    fun navigateToHome() {
        navController?.popBackStack(R.id.blind_nav_graph, true)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blind)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.blind_nav_host) as? NavHostFragment
        navController = navHostFragment?.navController

        // 首次启动：根据 intent 切换起始目的地
        // 注意：必须用 navInflater 重新 inflate 得到新 NavGraph 引用。
        // 若复用 navController.graph 同一引用，NavController.setGraph 会走 else 分支只更新节点不重启，
        // 导致起始目的地切换不生效（落到 XML 默认起点）。
        // savedInstanceState != null 时由 NavHostFragment 自行恢复 graph，无需在此处理。
        if (savedInstanceState == null) {
            // 优先处理订单恢复：通知点击 / 横幅点击 / 冷启动 UseCase 都通过此 extra 触发
            val recoveryRequestId = intent.getStringExtra(EXTRA_RECOVERY_REQUEST_ID)
            val recoveryStatus = intent.getStringExtra(EXTRA_RECOVERY_STATUS)
                ?.let { runCatching { RunRequestStatus.valueOf(it) }.getOrNull() }
            val recoveryDestId = if (recoveryRequestId != null && recoveryStatus != null) {
                ActiveOrderRouter.blindFragmentId(recoveryStatus)
            } else null

            val (startDestId, startArgs) = when {
                recoveryDestId != null && recoveryRequestId != null -> {
                    isStartDestination = false
                    recoveryDestId to bundleOf("requestId" to recoveryRequestId)
                }
                else -> {
                    val destination = intent.getStringExtra(EXTRA_DESTINATION) ?: DEST_CREATE_REQUEST
                    val destId = when (destination) {
                        DEST_HISTORY -> {
                            isStartDestination = false
                            R.id.blindHistoryFragment
                        }
                        DEST_SETTINGS -> {
                            isStartDestination = false
                            R.id.settingsFragment
                        }
                        else -> R.id.blindCreateRequestFragment
                    }
                    destId to intent.extras
                }
            }
            navController?.let { controller ->
                val newGraph = controller.navInflater.inflate(R.navigation.blind_nav_graph).apply {
                    setStartDestination(startDestId)
                }
                controller.setGraph(newGraph, startArgs)
            }
        }

        // 处理返回键
        // 不能用 previousBackStackEntry != null 判断，因为 NavGraph entry 永远在栈中（容器），会误判。
        // 在起始目的地误调 popBackStack 会清空 backQueue 导致后续 navigate 因 currentDestination=null 抛异常。
        onBackPressedDispatcher.addCallback(this) {
            val controller = navController
            val current = controller?.currentDestination?.id
            val start = controller?.graph?.startDestinationId
            if (controller != null && current != null && current != start) {
                controller.popBackStack()
            } else {
                finish()
            }
        }
    }

    /**
     * BlindActivity 已存在时（如用户停留在 CreateRequest，再点跑步中通知），
     * standard launchMode + FLAG_ACTIVITY_CLEAR_TOP 会复用本实例。
     * onCreate 不会重跑，因此在 onNewIntent 里把 NavController 重定向到恢复目的地。
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val recoveryRequestId = intent.getStringExtra(EXTRA_RECOVERY_REQUEST_ID) ?: return
        val recoveryStatus = intent.getStringExtra(EXTRA_RECOVERY_STATUS)
            ?.let { runCatching { RunRequestStatus.valueOf(it) }.getOrNull() } ?: return
        val destId = ActiveOrderRouter.blindFragmentId(recoveryStatus) ?: return
        val controller = navController ?: return
        // 清空中间页（含起点 CreateRequest），让恢复目的地成为新的栈底
        runCatching {
            controller.popBackStack(controller.graph.startDestinationId, true)
        }
        val newGraph = controller.navInflater.inflate(R.navigation.blind_nav_graph).apply {
            setStartDestination(destId)
        }
        controller.setGraph(newGraph, bundleOf("requestId" to recoveryRequestId))
        isStartDestination = false
    }
}
