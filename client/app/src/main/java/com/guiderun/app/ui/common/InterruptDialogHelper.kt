package com.guiderun.app.ui.common

import android.app.Activity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.guiderun.app.R

/**
 * 视障端 XML 页面用的中断确认对话框（与 Compose 端的 [InterruptDialog] 语义对齐）。
 *
 * 三种动作可选组合：取消订单 / 留在此页 / 返回首页（订单后台继续）。
 * 调用方按当前订单状态决定哪些按钮可见。
 *
 * 服务端状态机限制：
 * - MET 不允许 cancel/abandon → 不传 cancelLabel
 * - RUNNING 不允许 cancel → 不传 cancelLabel
 *
 * 字号通过 [R.style.BlindInterruptDialog] 主题放大（24sp 标题 / 20sp 正文 / 20sp 按钮），
 * 满足低视力用户的可读性要求。
 */
fun showInterruptDialog(
    activity: Activity,
    title: String,
    message: String,
    cancelLabel: String? = null,
    onCancel: (() -> Unit)? = null,
    stayLabel: String = activity.getString(R.string.interrupt_btn_stay),
    onStay: (() -> Unit)? = null,
    homeLabel: String? = null,
    onHome: (() -> Unit)? = null,
) {
    val builder = MaterialAlertDialogBuilder(activity, R.style.BlindInterruptDialog)
        .setTitle(title)
        .setMessage(message)
        .setCancelable(true)

    // setNegativeButton 标记破坏性动作，便于 TalkBack 朗读语义
    if (cancelLabel != null && onCancel != null) {
        builder.setNegativeButton(cancelLabel) { _, _ -> onCancel() }
    }
    builder.setPositiveButton(stayLabel) { _, _ -> onStay?.invoke() }
    if (homeLabel != null && onHome != null) {
        builder.setNeutralButton(homeLabel) { _, _ -> onHome() }
    }

    builder.show()
}
