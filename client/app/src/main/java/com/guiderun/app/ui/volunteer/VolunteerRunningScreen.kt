package com.guiderun.app.ui.volunteer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Speed
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guiderun.app.R
import com.guiderun.app.ui.common.CallPeerButton
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

    LaunchedEffect(uiState.infoMessage) {
        uiState.infoMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onInfoShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.volunteer_running_title)) },
                actions = {
                    CallPeerButton(phone = uiState.request?.blindRunner?.phone)
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            // 距离大字
            DistanceDisplay(
                distanceMeters = uiState.totalDistanceMeters,
                isPaused = uiState.isPaused,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
            )

            // 统计卡片
            StatsCard(
                durationSeconds = uiState.totalDurationSeconds,
                currentPace = uiState.displayPaceSeconds,
                avgPace = uiState.avgPaceSeconds,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )

            Spacer(Modifier.weight(1f))

            // 等待视障端确认提示
            if (uiState.endRequestPending) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Text(
                        text = stringResource(R.string.volunteer_running_end_waiting),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            // 结束按钮
            Button(
                onClick = { showEndConfirm = true },
                enabled = !uiState.endRequestPending,
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                Text(
                    text = if (uiState.endRequestPending)
                        stringResource(R.string.volunteer_running_end_pending)
                    else stringResource(R.string.volunteer_running_end),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(16.dp))

            if (showEndConfirm) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showEndConfirm = false },
                    title = { Text(stringResource(R.string.volunteer_running_end_confirm_title)) },
                    text = { Text(stringResource(R.string.volunteer_running_end_confirm_message)) },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            showEndConfirm = false
                            viewModel.requestEndRun()
                        }) { Text(stringResource(R.string.confirm)) }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { showEndConfirm = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                    },
                )
            }
        }
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
        if (isPaused) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
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
            Spacer(Modifier.height(12.dp))
        }
        Text(
            text = "%.2f".format(distanceMeters / 1000.0),
            fontSize = 64.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
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
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            RunningStatItem(
                icon = Icons.Default.Timer,
                label = stringResource(R.string.volunteer_running_duration),
                value = formatDuration(durationSeconds),
            )
            RunningStatItem(
                icon = Icons.Default.Speed,
                label = stringResource(R.string.volunteer_running_pace),
                value = currentPace?.let { PaceCalculator.formatPace(it) } ?: "--'--\"",
            )
            RunningStatItem(
                icon = Icons.AutoMirrored.Filled.DirectionsRun,
                label = stringResource(R.string.volunteer_running_avg_pace),
                value = avgPace?.let { PaceCalculator.formatPace(it) } ?: "--'--\"",
            )
        }
    }
}

@Composable
private fun RunningStatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
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
