package com.guiderun.app.ui.blind

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.addCallback
import androidx.annotation.IdRes
import androidx.core.os.bundleOf
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.guiderun.app.R
import com.guiderun.app.accessibility.voice.CommandExecutor
import com.guiderun.app.accessibility.voice.VoiceDestination
import com.guiderun.app.domain.model.RunRequestStatus
import com.guiderun.app.ui.navigation.ActiveOrderRouter
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class BlindActivity : BaseBlindActivity() {

    @Inject lateinit var commandExecutor: CommandExecutor

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

    // ===== VoiceCommandHost：导航相关由 BlindActivity 提供（持有 NavController） =====

    override fun voiceNavigate(destination: VoiceDestination): Boolean {
        val destId = when (destination) {
            VoiceDestination.CREATE_REQUEST -> R.id.blindCreateRequestFragment
            VoiceDestination.VIEW_HISTORY -> R.id.blindHistoryFragment
            VoiceDestination.PROFILE -> R.id.settingsFragment
        }
        val controller = navController ?: return false
        if (controller.currentDestination?.id == destId) return false
        return runCatching {
            controller.navigate(destId)
            true
        }.getOrDefault(false)
    }

    override fun voiceNavigateToHome() = navigateToHome()

    override fun voiceDescribeStatus(): String {
        val destId = navController?.currentDestination?.id
        val pageRes = when (destId) {
            R.id.blindCreateRequestFragment -> R.string.tts_page_create_request
            R.id.blindWaitingMatchFragment -> R.string.tts_page_waiting_match
            R.id.blindMatchedFragment -> R.string.tts_page_matched
            R.id.blindRunningFragment -> R.string.tts_page_blind_running
            R.id.blindReviewFragment -> R.string.tts_page_blind_review
            R.id.blindHistoryFragment -> R.string.tts_page_blind_history
            R.id.blindEditRequestFragment -> R.string.tts_page_edit_request
            R.id.blindTrackPlaybackFragment -> R.string.tts_page_track_playback
            R.id.settingsFragment -> R.string.tts_page_settings
            else -> null
        }
        val pageText = pageRes?.let { getString(it) } ?: getString(R.string.app_name)
        val orderText = activeRequestId?.let { "，正在进行订单" } ?: "，当前没有进行中的订单"
        return "当前在$pageText$orderText"
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

    override fun onResume() {
        super.onResume()
        commandExecutor.bind(this)
    }

    override fun onPause() {
        commandExecutor.unbind(this)
        voiceCommandManager.cancel()
        super.onPause()
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
