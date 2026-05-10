package com.guiderun.app.util

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Edge-to-edge 辅助工具。
 * Android 15 (API 35) 强制启用 edge-to-edge，需要手动处理 WindowInsets。
 */
object EdgeToEdgeHelper {

    /**
     * 为根 View 添加状态栏和导航栏的 padding。
     * 适用于设置了 fitsSystemWindows="true" 的布局。
     */
    fun applyInsets(rootView: View) {
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout()
            )
            view.updatePadding(
                left = insets.left,
                top = insets.top,
                right = insets.right,
                bottom = insets.bottom,
            )
            WindowInsetsCompat.CONSUMED
        }
    }
}
