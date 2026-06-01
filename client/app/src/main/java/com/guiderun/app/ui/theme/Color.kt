package com.guiderun.app.ui.theme

import androidx.compose.ui.graphics.Color

// 语义色（所有主题通用，不随主题切换而变化）
val SuccessGreen = Color(0xFF4CAF50)
val WarningAmber = Color(0xFFFFA726)
val ErrorRed = Color(0xFFE53935)

// 中性色（所有主题通用）
val NeutralGray100 = Color(0xFFF5F5F5)
val NeutralGray200 = Color(0xFFEEEEEE)
val NeutralGray300 = Color(0xFFE0E0E0)
val NeutralGray400 = Color(0xFFBDBDBD)
val NeutralGray500 = Color(0xFF9E9E9E)
val NeutralGray600 = Color(0xFF757575)
val NeutralGray700 = Color(0xFF616161)
val NeutralGray800 = Color(0xFF424242)
val NeutralGray900 = Color(0xFF212121)

/**
 * 品牌主题配色方案。
 *
 * 包含 primary / secondary 各三档（light / mid / dark），由 [Theme.kt] 的
 * lightColorSchemeFrom / darkColorSchemeFrom 映射到 Material3 ColorScheme 角色。
 * 一套 AppColorScheme 可同时生成 Light 和 Dark 两个 Material3 ColorScheme。
 */
data class AppColorScheme(
    val id: String,
    val name: String,
    // 主色
    val primary: Color,
    val primaryLight: Color,
    val primaryDark: Color,
    // 辅助色
    val secondary: Color,
    val secondaryLight: Color,
    val secondaryDark: Color,
)

// ==================== 4 套预设主题 ====================

/** 方案 1：活力橙（默认） */
val ThemeOrange = AppColorScheme(
    id = "orange",
    name = "活力橙",
    primary = Color(0xFFEF6C00),
    primaryLight = Color(0xFFFFA726),
    primaryDark = Color(0xFFE65100),
    secondary = Color(0xFF00897B),
    secondaryLight = Color(0xFF4DB6AC),
    secondaryDark = Color(0xFF00695C),
)

/** 方案 2：信任蓝 */
val ThemeBlue = AppColorScheme(
    id = "blue",
    name = "信任蓝",
    primary = Color(0xFF1E88E5),
    primaryLight = Color(0xFF64B5F6),
    primaryDark = Color(0xFF1565C0),
    secondary = Color(0xFFEF6C00),
    secondaryLight = Color(0xFFFFA726),
    secondaryDark = Color(0xFFE65100),
)

/** 方案 3：自然青 */
val ThemeTeal = AppColorScheme(
    id = "teal",
    name = "自然青",
    primary = Color(0xFF00897B),
    primaryLight = Color(0xFF4DB6AC),
    primaryDark = Color(0xFF00695C),
    secondary = Color(0xFFEF6C00),
    secondaryLight = Color(0xFFFFA726),
    secondaryDark = Color(0xFFE65100),
)

/** 方案 4：沉稳灰 */
val ThemeGray = AppColorScheme(
    id = "gray",
    name = "沉稳灰",
    primary = Color(0xFF455A64),
    primaryLight = Color(0xFF78909C),
    primaryDark = Color(0xFF37474F),
    secondary = Color(0xFF00897B),
    secondaryLight = Color(0xFF4DB6AC),
    secondaryDark = Color(0xFF00695C),
)

/** 所有预设主题列表 */
val PresetThemes = listOf(ThemeOrange, ThemeBlue, ThemeTeal, ThemeGray)

/** 根据 id 获取主题，找不到时返回默认 */
fun getPresetTheme(id: String): AppColorScheme =
    PresetThemes.firstOrNull { it.id == id } ?: ThemeOrange
