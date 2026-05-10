package com.guiderun.app.ui.blind

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.addCallback
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.guiderun.app.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class BlindActivity : BaseBlindActivity() {

    companion object {
        private const val EXTRA_DESTINATION = "destination"

        const val DEST_CREATE_REQUEST = "create_request"
        const val DEST_HISTORY = "history"
        const val DEST_SETTINGS = "settings"

        fun start(context: Context, destination: String = DEST_CREATE_REQUEST) {
            val intent = Intent(context, BlindActivity::class.java).apply {
                putExtra(EXTRA_DESTINATION, destination)
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
            val destination = intent.getStringExtra(EXTRA_DESTINATION) ?: DEST_CREATE_REQUEST
            val startDestId = when (destination) {
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
            navController?.let { controller ->
                val newGraph = controller.navInflater.inflate(R.navigation.blind_nav_graph).apply {
                    setStartDestination(startDestId)
                }
                controller.setGraph(newGraph, intent.extras)
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
}
