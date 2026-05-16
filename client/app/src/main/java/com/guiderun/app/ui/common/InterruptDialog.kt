package com.guiderun.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.guiderun.app.ui.theme.AppRadius
import com.guiderun.app.ui.theme.AppSpacing

/**
 * 通用中断确认对话框。
 *
 * 三种动作可选组合：取消订单 / 留在此页 / 返回首页（订单后台继续）。
 * 调用方根据当前订单状态决定哪些按钮可见。
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.SM),
            ) {
                Surface(
                    shape = AppRadius.MediumShape,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.padding(AppSpacing.XS),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.XS),
            ) {
                // 主操作按钮（留在此页）- 填满宽度
                Surface(
                    onClick = onStay,
                    shape = AppRadius.MediumShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stayLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = AppSpacing.SM),
                    )
                }

                // 次要操作行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.SM),
                ) {
                    // 返回首页
                    if (homeLabel != null && onHome != null) {
                        Surface(
                            onClick = onHome,
                            shape = AppRadius.MediumShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.weight(1f),
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = AppSpacing.SM),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Home,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(AppSpacing.XS))
                                Text(
                                    text = homeLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    // 取消订单（危险操作）
                    if (cancelLabel != null && onCancel != null) {
                        Surface(
                            onClick = onCancel,
                            shape = AppRadius.MediumShape,
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = cancelLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = AppSpacing.SM),
                            )
                        }
                    }
                }
            }
        },
    )
}
