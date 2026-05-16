package com.guiderun.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * 统一间距系统
 * 所有页面间距必须使用这些 token，禁止硬编码 dp 值
 */
object AppSpacing {
    val XS = 4.dp
    val SM = 8.dp
    val MD = 16.dp
    val LG = 24.dp
    val XL = 32.dp
    val XXL = 48.dp
}

/**
 * 统一圆角系统
 * 卡片、按钮、输入框等统一使用
 */
object AppRadius {
    val Small = 12.dp
    val Medium = 20.dp
    val Large = 28.dp
    val ExtraLarge = 36.dp

    val SmallShape = RoundedCornerShape(Small)
    val MediumShape = RoundedCornerShape(Medium)
    val LargeShape = RoundedCornerShape(Large)
    val ExtraLargeShape = RoundedCornerShape(ExtraLarge)
}

/**
 * 统一阴影层级
 * 控制卡片和表面的深度感
 */
object AppElevation {
    val None = 0.dp
    val Low = 1.dp
    val Medium = 3.dp
    val High = 6.dp
}

/**
 * 统一图标尺寸
 */
object AppIconSize {
    val Small = 16.dp
    val Medium = 24.dp
    val Large = 32.dp
    val ExtraLarge = 48.dp
    val Hero = 64.dp
}

/**
 * 统一按钮尺寸
 */
object AppButtonSize {
    val Height = 56.dp
    val IconSize = 48.dp
}
