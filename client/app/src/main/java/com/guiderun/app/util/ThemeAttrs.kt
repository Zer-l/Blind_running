package com.guiderun.app.util

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt

/**
 * 解析当前主题下某个 ?attr/xxx 的颜色值。
 *
 * 视障端用 ?attr/blindXxx 实现 3 套对比度主题切换，Fragment 在代码中动态着色时
 * 必须先解析主题 attr，而不是直接引用 @color/blind_xxx（那样会硬绑黑底主题）。
 */
@ColorInt
fun Context.resolveThemeColor(@AttrRes attrRes: Int): Int {
    val tv = TypedValue()
    theme.resolveAttribute(attrRes, tv, true)
    return tv.data
}
