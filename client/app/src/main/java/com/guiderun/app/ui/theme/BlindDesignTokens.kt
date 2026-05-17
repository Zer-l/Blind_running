package com.guiderun.app.ui.theme

/**
 * 视障端设计 Token 常量集中地。
 *
 * 与 res/values/dimens_blind.xml 一一对应；XML 中优先引用 @dimen/blind_*，
 * Kotlin 代码（如手势组件、Compose 端 HomeScreen 长按入口）需要数值时引用此处。
 *
 * 字号基准为 scale=1.0；实际 sp 由 BaseBlindActivity.attachBaseContext 注入的
 * fontScale Configuration 自动放大。
 */
object BlindDesignTokens {

    /** 字号基准（sp） */
    object Font {
        const val DisplaySp = 48f
        const val TitleSp = 28f
        const val HeadlineSp = 22f
        const val BodySp = 18f
        const val LabelSp = 16f
        const val HintSp = 14f
    }

    /** 字号缩放档（持久化到 UserPreferences.FONT_SCALE） */
    object FontScale {
        const val Normal = 1.0f
        const val Large = 1.25f
        const val ExtraLarge = 1.5f
        const val Huge = 2.0f
        val Options = listOf(Normal, Large, ExtraLarge, Huge)
        const val Default = Normal
    }

    /** 间距（dp） */
    object Space {
        const val XS = 8
        const val SM = 12
        const val MD = 16
        const val LG = 24
        const val XL = 32
        const val XXL = 48
    }

    /** 触摸目标尺寸（dp）；视障端按钮建议 ≥ MinTarget，关键操作 ≥ PrimaryButton */
    object Touch {
        const val MinTargetDp = 64
        const val PrimaryButtonDp = 96
        const val EmergencyButtonDp = 128
    }

    /** 描边宽度（dp） */
    object Stroke {
        const val CardDp = 2
        const val FocusRingDp = 4
    }

    /** 圆角（dp） */
    object Radius {
        const val CardDp = 16
        const val ButtonDp = 16
    }

    /** 对比度主题 ID（持久化到 UserPreferences.CONTRAST_THEME） */
    object ContrastTheme {
        const val Black = "BLACK"
        const val White = "WHITE"
        const val Yellow = "YELLOW"
        val Options = listOf(Black, White, Yellow)
        const val Default = Black
    }

    /** 震动强度档（持久化到 UserPreferences.HAPTIC_STRENGTH） */
    object HapticStrength {
        const val Off = 0
        const val Normal = 1
        const val Strong = 2
        const val Default = Normal
    }

    /** 长按手势时长（ms） */
    object Gesture {
        const val ThresholdMs = 2000L
        const val CountdownMs = 5000L
        const val TickIntervalMs = 1000L
    }
}
