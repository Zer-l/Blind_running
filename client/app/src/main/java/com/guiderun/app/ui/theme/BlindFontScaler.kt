package com.guiderun.app.ui.theme

import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

/**
 * 视障端字号缩放工具。
 *
 * 使用方式：Fragment.onViewCreated 后调用 `BlindFontScaler.apply(view, scale)`。
 * scale 由 UserPreferences.getBlindFontScale 提供（1.0 / 1.25 / 1.5 / 2.0）。
 *
 * 实现：遍历 View 树所有 TextView，把 XML 中通过 @dimen/blind_text_* 设置的基准字号
 * 重新计算为 base * scale。scale=1.0 时无操作。
 *
 * 注意：本工具只支持 onViewCreated 一次性应用，不监听字号变化。切换字号需配合 Activity.recreate()。
 */
object BlindFontScaler {

    fun apply(root: View, scale: Float) {
        if (scale == DEFAULT_SCALE) return
        walk(root) { view ->
            if (view is TextView) {
                val currentPx = view.textSize
                val originalSp = currentPx / view.resources.displayMetrics.scaledDensity
                view.setTextSize(TypedValue.COMPLEX_UNIT_SP, originalSp * scale)
            }
        }
    }

    private fun walk(root: View, action: (View) -> Unit) {
        action(root)
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                walk(root.getChildAt(i), action)
            }
        }
    }

    private const val DEFAULT_SCALE = 1.0f
}
