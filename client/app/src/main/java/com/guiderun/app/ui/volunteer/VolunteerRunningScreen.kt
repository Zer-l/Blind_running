package com.guiderun.app.ui.volunteer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
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
import com.guiderun.app.ui.shared.map.CameraTarget
import com.guiderun.app.ui.shared.map.GuideRunMap
import com.guiderun.app.ui.shared.map.GuideRunMapState
import com.guiderun.app.ui.shared.map.PolylineConfig
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.SM),
                    ) {
                        Text(
                            text = stringResource(R.string.volunteer_running_title),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        // 暂停提示嵌入标题和电话按钮之间
                        AnimatedVisibility(
                            visible = uiState.isPaused,
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) {
                            Surface(
                                shape = AppRadius.SmallShape,
                                color = MaterialTheme.colorScheme.errorContainer,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = AppSpacing.SM, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Pause,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                    Text(
                                        text = stringResource(R.string.running_paused_label),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                }
                            }
                        }
                    }
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
            // 三个指标横排：距离、时长、配速
            StatsRow(
                distanceMeters = uiState.totalDistanceMeters,
                durationSeconds = uiState.totalDurationSeconds,
                currentPace = uiState.displayPaceSeconds,
                isPaused = uiState.isPaused,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.MD, vertical = AppSpacing.SM),
            )

            // 地图实时渲染跑步轨迹
            // cameraTarget 只在首次设置一次，避免每次刷新都重置相机
            var initialCameraSet by remember { mutableStateOf(false) }
            val initialLocation = uiState.initialLocation
            val lastPoint = uiState.trackPoints.lastOrNull()

            // 在 LaunchedEffect 中设置初始相机标记，避免在 remember 块内产生副作用
            LaunchedEffect(lastPoint, initialLocation) {
                if (!initialCameraSet && (lastPoint != null || initialLocation != null)) {
                    initialCameraSet = true
                }
            }

            val mapState = remember(uiState.trackPoints, initialLocation, initialCameraSet) {
                val camera = when {
                    initialCameraSet && lastPoint != null -> {
                        CameraTarget(lastPoint.first, lastPoint.second, zoom = 17f)
                    }
                    initialCameraSet && initialLocation != null -> {
                        CameraTarget(initialLocation.first, initialLocation.second, zoom = 17f)
                    }
                    else -> null
                }
                GuideRunMapState(
                    cameraTarget = camera,
                    polylines = if (uiState.trackPoints.size >= 2) {
                        listOf(
                            PolylineConfig(
                                points = uiState.trackPoints,
                                colorHex = "#2196F3",
                                width = 12f,
                            )
                        )
                    } else emptyList(),
                    // 当前定位点图标
                    animatedMarker = lastPoint,
                )
            }
            GuideRunMap(
                state = mapState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = AppSpacing.MD, vertical = AppSpacing.XS)
                    .clip(RoundedCornerShape(AppRadius.Large)),
            )

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

            // 申请结束按钮
            Button(
                onClick = { showEndConfirm = true },
                enabled = !uiState.endRequestPending,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = AppSpacing.MD, vertical = AppSpacing.SM),
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
            Spacer(Modifier.height(AppSpacing.SM))
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
private fun StatsRow(
    distanceMeters: Int,
    durationSeconds: Int,
    currentPace: Int?,
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
                .padding(AppSpacing.MD),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StatItem(
                label = stringResource(R.string.volunteer_running_distance),
                value = "%.2f".format(distanceMeters / 1000.0),
                dimmed = isPaused,
            )
            StatItem(
                label = stringResource(R.string.volunteer_running_duration),
                value = formatDuration(durationSeconds),
                dimmed = isPaused,
            )
            StatItem(
                label = stringResource(R.string.volunteer_running_pace),
                value = currentPace?.let { PaceCalculator.formatPace(it) } ?: "--'--\"",
                dimmed = isPaused,
            )
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    dimmed: Boolean = false,
) {
    val valueColor = if (dimmed) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.XS),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = valueColor,
        )
    }
}

private fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
