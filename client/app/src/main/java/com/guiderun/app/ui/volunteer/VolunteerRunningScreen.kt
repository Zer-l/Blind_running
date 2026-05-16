package com.guiderun.app.ui.volunteer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guiderun.app.R
import com.guiderun.app.ui.common.CallPeerButton
import com.guiderun.app.ui.common.InterruptDialog
import com.guiderun.app.ui.theme.AppRadius
import com.guiderun.app.ui.theme.AppSpacing
import com.guiderun.app.util.PaceCalculator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolunteerRunningScreen(
    requestId: String,
    onNavigateToReview: (String) -> Unit,
    onNavigateToHome: () -> Unit,
    viewModel: VolunteerRunningViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showEndConfirm by remember { mutableStateOf(false) }
    var showInterruptDialog by remember { mutableStateOf(false) }

    BackHandler { showInterruptDialog = true }

    if (showInterruptDialog) {
        InterruptDialog(
            title = stringResource(R.string.interrupt_title_leave_running),
            message = stringResource(R.string.interrupt_message_leave_running)
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
                is VolunteerRunningNavEvent.ToReview -> onNavigateToReview(event.requestId)
                VolunteerRunningNavEvent.ToHome -> onNavigateToHome()
            }
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onErrorShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.volunteer_running_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                actions = {
                    CallPeerButton(phone = uiState.request?.blindRunner?.phone)
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // 距离大字
            DistanceDisplay(
                distanceMeters = uiState.totalDistanceMeters,
                isPaused = uiState.isPaused,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.MD, vertical = AppSpacing.XL),
            )

            // 统计卡片
            StatsCard(
                durationSeconds = uiState.totalDurationSeconds,
                currentPace = uiState.displayPaceSeconds,
                avgPace = uiState.avgPaceSeconds,
                isPaused = uiState.isPaused,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.MD),
            )

            Spacer(Modifier.weight(1f))

            // 等待视障端确认提示
            AnimatedVisibility(
                visible = uiState.endRequestPending,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppSpacing.MD, vertical = AppSpacing.SM),
                    shape = AppRadius.MediumShape,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Text(
                        text = stringResource(R.string.volunteer_running_end_waiting),
                        modifier = Modifier.padding(
                            horizontal = AppSpacing.MD,
                            vertical = AppSpacing.MD,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // 结束按钮
            Button(
                onClick = { showEndConfirm = true },
                enabled = !uiState.endRequestPending,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = AppSpacing.MD),
                shape = AppRadius.LargeShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(
                    text = if (uiState.endRequestPending)
                        stringResource(R.string.volunteer_running_end_pending)
                    else stringResource(R.string.volunteer_running_end),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(AppSpacing.MD))
        }
    }

    // 结束跑步确认弹窗
    if (showEndConfirm) {
        InterruptDialog(
            title = stringResource(R.string.end_run_title),
            message = stringResource(R.string.end_run_message),
            onDismissRequest = { showEndConfirm = false },
            stayLabel = stringResource(R.string.end_run_cancel),
            onStay = { showEndConfirm = false },
            cancelLabel = stringResource(R.string.end_run_confirm),
            onCancel = {
                showEndConfirm = false
                viewModel.requestEndRun()
            },
        )
    }
}

@Composable
private fun DistanceDisplay(
    distanceMeters: Int,
    isPaused: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedVisibility(
            visible = isPaused,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Surface(
                shape = AppRadius.SmallShape,
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.padding(bottom = AppSpacing.MD),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = AppSpacing.MD, vertical = AppSpacing.SM),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.SM),
                ) {
                    Icon(
                        imageVector = Icons.Default.Pause,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        text = stringResource(R.string.running_paused_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }

        Text(
            text = "%.2f".format(distanceMeters / 1000.0),
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = if (isPaused) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.primary
            },
        )
        Text(
            text = stringResource(R.string.volunteer_running_distance),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatsCard(
    durationSeconds: Int,
    currentPace: Int?,
    avgPace: Int?,
    isPaused: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = AppRadius.LargeShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = AppSpacing.LG),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            RunningStatItem(
                icon = Icons.Default.Timer,
                label = stringResource(R.string.volunteer_running_duration),
                value = formatDuration(durationSeconds),
                dimmed = isPaused,
            )
            RunningStatItem(
                icon = Icons.Default.Speed,
                label = stringResource(R.string.volunteer_running_pace),
                value = currentPace?.let { PaceCalculator.formatPace(it) } ?: "--'--\"",
                dimmed = isPaused,
            )
            RunningStatItem(
                icon = Icons.AutoMirrored.Filled.DirectionsRun,
                label = stringResource(R.string.volunteer_running_avg_pace),
                value = avgPace?.let { PaceCalculator.formatPace(it) } ?: "--'--\"",
                dimmed = isPaused,
            )
        }
    }
}

@Composable
private fun RunningStatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    dimmed: Boolean = false,
) {
    val accent = if (dimmed) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.primary
    }
    val valueColor = if (dimmed) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.SM),
    ) {
        Surface(
            shape = AppRadius.MediumShape,
            color = accent.copy(alpha = 0.1f),
            modifier = Modifier.size(44.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.padding(AppSpacing.SM),
                tint = accent,
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = valueColor,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
