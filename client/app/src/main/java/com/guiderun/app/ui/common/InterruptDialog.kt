package com.guiderun.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 订单中断确认对话框（Compose）。
 *
 * 三种动作可选组合：取消订单 / 留在此页 / 返回首页（订单后台继续）。
 * 调用方根据当前订单状态决定哪些按钮可见。
 *
 * 服务端状态机限制：
 * - MET 不允许 cancel/abandon → 隐藏 cancelLabel
 * - RUNNING 不允许 cancel → 隐藏 cancelLabel
 *
 * 「返回首页」点击后调用方应让用户返回主入口（首页横幅 + 前台服务通知保活），订单不取消。
 */
@Composable
fun InterruptDialog(
    title: String,
    message: String,
    onDismissRequest: () -> Unit,
    cancelLabel: String? = null,
    onCancel: (() -> Unit)? = null,
    stayLabel: String = "留在此页",
    onStay: () -> Unit = onDismissRequest,
    homeLabel: String? = null,
    onHome: (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                text = message,
                fontSize = 18.sp,
                lineHeight = 26.sp,
            )
        },
        confirmButton = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (cancelLabel != null && onCancel != null) {
                    TextButton(onClick = onCancel) {
                        Text(
                            text = cancelLabel,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                if (homeLabel != null && onHome != null) {
                    TextButton(onClick = onHome) {
                        Text(
                            text = homeLabel,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                TextButton(onClick = onStay) {
                    Text(
                        text = stayLabel,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
    )
}
