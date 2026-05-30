package com.guiderun.app.util

import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.children
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup

/**
 * 视障端 ToggleGroup 自适应字号方向：
 * 当 [fontScale] ≥ 1.5x（大字号 / 特大档位）时切换为竖排，每个选项独占一行；
 * 否则保持横排（默认 0dp+weight=1 等宽分摊）。
 *
 * 之所以不依赖 MaterialButton 的 autoSize 救场：autoSize 的 min/max 用 sp 单位，
 * 会随 BaseBlindActivity 注入的 Configuration.fontScale 同步放大；
 * fontScale=2.0 时 min=11sp 实际渲染为 22sp，仍超过 25%/33% 列宽，最终落到省略号。
 * 竖排让宽度 = match_parent，文字宽度不再被列宽约束，与"用户主动要大字号"的诉求一致。
 *
 * 调用时机：Fragment.onViewCreated（Configuration.fontScale 已由 BaseBlindActivity.attachBaseContext 注入）。
 * Activity recreate 后会重走 onViewCreated，无需运行时响应式切换。
 */
fun MaterialButtonToggleGroup.applyAdaptiveOrientation(fontScale: Float) {
    val vertical = fontScale >= 1.5f
    orientation = if (vertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
    children.filterIsInstance<MaterialButton>().forEach { btn ->
        val lp = btn.layoutParams as? LinearLayout.LayoutParams ?: return@forEach
        if (vertical) {
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
            lp.weight = 0f
        } else {
            lp.width = 0
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
            lp.weight = 1f
        }
        btn.layoutParams = lp
    }
}
