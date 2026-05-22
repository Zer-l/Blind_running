package com.guiderun.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 跑步历史卡片 / 通知等场景共用的轻量日期格式化工具。
 *
 * 使用 [SimpleDateFormat] 而非 java.time，因为 minSdk 仍可能低于 26（避免 desugar 依赖）。
 * 每次调用新建实例——SimpleDateFormat 非线程安全，复用反而需要锁。
 */
object DateFormat {

    /** "yyyy.MM.dd HH:mm" — 跑步历史卡片用 */
    fun historyDateTime(epochMillis: Long): String {
        if (epochMillis <= 0) return ""
        return SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.CHINA).format(Date(epochMillis))
    }
}
