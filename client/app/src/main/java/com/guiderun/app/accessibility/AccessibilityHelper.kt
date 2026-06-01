package com.guiderun.app.accessibility

import android.view.View
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat

/**
 * View 无障碍属性快捷扩展。
 *
 * 封装 TalkBack 相关 API（AccessibilityDelegate / LiveRegion / importantForAccessibility），
 * 使视障端 XML 页面不需要逐个写 ViewCompat 样板代码。
 */

/** TalkBack hint shown after content description when focused */
fun View.setAccessibilityHint(hint: String) {
    ViewCompat.setAccessibilityDelegate(this, object : AccessibilityDelegateCompat() {
        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
            super.onInitializeAccessibilityNodeInfo(host, info)
            info.hintText = hint
        }
    })
}

/**
 * Mark view as a live region so TalkBack announces content changes automatically.
 * polite=true: won't interrupt current speech.
 * polite=false (assertive): interrupts immediately — use for critical state changes.
 */
fun View.setLiveRegion(polite: Boolean = true) {
    accessibilityLiveRegion = if (polite)
        View.ACCESSIBILITY_LIVE_REGION_POLITE
    else
        View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE
}

/** Exclude view from TalkBack focus traversal (decorative or redundant elements) */
fun View.excludeFromAccessibility() {
    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
}

/** Mark view as important for accessibility (TalkBack will focus it) */
fun View.includeInAccessibility() {
    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
}

/** Announce text to TalkBack (polite — won't interrupt current speech) */
@Suppress("DEPRECATION")
fun View.announcePolite(text: String) {
    announceForAccessibility(text)
}
