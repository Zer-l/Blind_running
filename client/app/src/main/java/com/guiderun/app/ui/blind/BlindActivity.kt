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

        // 处理返回键
        onBackPressedDispatcher.addCallback(this) {
            val controller = navController
            if (controller != null && controller.previousBackStackEntry != null) {
                controller.popBackStack()
            } else {
                finish()
            }
        }

        // 根据目标设置起始目的地
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

            // 设置导航图并指定起始目的地
            val navGraph = navController?.graph
            if (navGraph != null) {
                navGraph.setStartDestination(startDestId)
                navController?.setGraph(navGraph, intent.extras)
            }
        }
    }
}
