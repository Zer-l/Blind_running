package com.guiderun.app.ui.theme

import androidx.annotation.StyleRes
import com.guiderun.app.R

/**
 * 视障端对比度主题 ID → 资源 ID 映射器。
 *
 * 由 BaseBlindActivity.onCreate 在 super 之前调用 `setTheme(BlindThemeResolver.resolve(...))` 切换。
 * 切换主题后必须 recreate() 才能生效。
 */
object BlindThemeResolver {

    @StyleRes
    fun resolve(themeId: String?): Int = when (themeId) {
        BlindDesignTokens.ContrastTheme.White -> R.style.BlindTheme_HighContrastWhite
        BlindDesignTokens.ContrastTheme.Yellow -> R.style.BlindTheme_HighContrastYellow
        else -> R.style.BlindTheme_HighContrastBlack
    }
}
