package com.guiderun.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * 根据 [AppColorScheme] 生成 Light ColorScheme。
 *
 * surfaceVariant / onSurfaceVariant 使用暖色调（#F5EDE4），与品牌橙色系统一风格，
 * 区别于 Material3 默认的冷灰色，使界面整体更温暖。
 */
fun lightColorSchemeFrom(app: AppColorScheme) = lightColorScheme(
    primary = app.primary,
    onPrimary = Color.White,
    primaryContainer = app.primaryLight.copy(alpha = 0.3f),
    onPrimaryContainer = app.primaryDark,
    secondary = app.secondary,
    onSecondary = Color.White,
    secondaryContainer = app.secondaryLight.copy(alpha = 0.3f),
    onSecondaryContainer = app.secondaryDark,
    tertiary = Color(0xFF7C5800),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDEA1),
    onTertiaryContainer = Color(0xFF271900),
    background = Color(0xFFFFFBFF),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFF5EDE4),
    onSurfaceVariant = Color(0xFF52443B),
    error = ErrorRed,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF85736A),
    outlineVariant = Color(0xFFD8C2B8),
)

/**
 * 根据 [AppColorScheme] 生成 Dark ColorScheme
 */
fun darkColorSchemeFrom(app: AppColorScheme) = darkColorScheme(
    primary = app.primaryLight,
    onPrimary = app.primaryDark,
    primaryContainer = app.primary,
    onPrimaryContainer = app.primaryLight,
    secondary = app.secondaryLight,
    onSecondary = app.secondaryDark,
    secondaryContainer = app.secondary,
    onSecondaryContainer = app.secondaryLight,
    tertiary = Color(0xFFFFC76B),
    onTertiary = Color(0xFF422C00),
    tertiaryContainer = Color(0xFF5E4100),
    onTertiaryContainer = Color(0xFFFFDEA1),
    background = Color(0xFF1C1B1F),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF1C1B1F),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF52443B),
    onSurfaceVariant = Color(0xFFD8C2B8),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFFA08D83),
    outlineVariant = Color(0xFF52443B),
)

/**
 * 志愿者端顶层 Compose 主题。
 *
 * appColorScheme 由 MainViewModel.themeId（DataStore 持久化）驱动，
 * 支持 4 套预设主题动态切换。SideEffect 更新系统状态栏图标颜色（浅主题深色图标）。
 * 视障端主题使用独立的 XML style（BlindTheme_*），不经过此函数。
 */
@Composable
fun GuideRunTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    appColorScheme: AppColorScheme = ThemeOrange,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        darkColorSchemeFrom(appColorScheme)
    } else {
        lightColorSchemeFrom(appColorScheme)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
