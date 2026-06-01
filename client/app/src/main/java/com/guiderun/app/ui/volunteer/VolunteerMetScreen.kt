package com.guiderun.app.ui.volunteer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guiderun.app.R
import com.guiderun.app.ui.shared.map.GuideRunMap
import com.guiderun.app.ui.theme.AppRadius
import com.guiderun.app.ui.theme.AppSpacing

/**
 * 志愿者已汇合页（状态：MET）。
 *
 * 显示集合点地图（志愿者实时位置蓝点）+ 底部状态卡（含呼吸动画）。
 * 等待视障端长按确认汇合后，服务端推 RUNNING WS，跳转至跑步页。
 * 订单被取消时弹 InterruptDialog 提示后回首页；按返回键仅允许"留在此页"或"回首页"，不能取消订单。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolunteerMetScreen(
    onNavigateToHome: () -> Unit,
    onNavigateToRunning: (String) -> Unit,
    viewModel: VolunteerMetViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showInterruptDialog by remember { mutableStateOf(false) }

    BackHandler { showInterruptDialog = true }

    if (uiState.showCancelledDialog) {
        InterruptDialog(
            title = stringResource(R.string.cancelled_dialog_title),
            message = stringResource(R.string.cancelled_dialog_message_blind),
            onDismissRequest = viewModel::onCancelledDialogDismiss,
            stayLabel = stringResource(R.string.cancelled_dialog_btn_home),
            onStay = viewModel::onCancelledDialogDismiss,
        )
    }

    if (showInterruptDialog) {
        InterruptDialog(
            title = stringResource(R.string.interrupt_title_leave_met),
            message = stringResource(R.string.interrupt_message_leave_met)
                + "\n" + stringResource(R.string.interrupt_hint_resume),
            onDismissRequest = { showInterruptDialog = false },
            stayLabel = stringResource(R.string.interrupt_btn_stay),
            onStay = { showInterruptDialog = false },
            homeLabel = stringResource(R.string.interrupt_btn_back_home),
            onHome = {
                showInterruptDialog = false
                onNavigateToHome()
            },
        )
    }

    LaunchedEffect(Unit) {
        viewModel.navEvent.collect { event ->
            when (event) {
                VolunteerMetNavEvent.ToHome -> onNavigateToHome()
                is VolunteerMetNavEvent.ToRunning -> onNavigateToRunning(event.requestId)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.met_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                actions = {
                    CallPeerButton(phone = uiState.request?.blindRunner?.phone)
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // 地图区域 - 带圆角和内边距
            GuideRunMap(
                state = uiState.mapState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = AppSpacing.MD, vertical = AppSpacing.XS)
                    .clip(RoundedCornerShape(AppRadius.Large)),
            )

            // 状态卡片 - 与 VolunteerNavigatingScreen 底部面板统一
            MetStatusCard(
                statusMessage = uiState.statusMessage.ifEmpty { stringResource(R.string.met_waiting) },
            )
        }
    }
}

@Composable
private fun MetStatusCard(statusMessage: String) {
    // 呼吸动画
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alpha",
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.MD, vertical = AppSpacing.SM),
        shape = AppRadius.LargeShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.LG),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppSpacing.MD),
        ) {
            // 图标 + 状态行
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.MD),
            ) {
                // 呼吸动画图标
                Surface(
                    shape = AppRadius.MediumShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .size(48.dp)
                        .alpha(alpha),
                ) {
                    Icon(
                        imageVector = Icons.Default.People,
                        contentDescription = null,
                        modifier = Modifier.padding(AppSpacing.SM),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "请等待视障跑友确认汇合",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 提示条
            Surface(
                shape = AppRadius.MediumShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(AppSpacing.SM),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.SM),
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "汇合后将自动开始跑步",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
