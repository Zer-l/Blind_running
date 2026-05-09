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
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.volunteer_running_title)) },
                actions = {
                    if (uiState.callEnabled) {
                        IconButton(onClick = { viewModel.initiateCall() }) {
                            Icon(Icons.Default.Phone, contentDescription = stringResource(R.string.volunteer_running_call))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StatsGrid(
                distanceMeters = uiState.totalDistanceMeters,
                durationSeconds = uiState.totalDurationSeconds,
                currentPace = uiState.currentPaceSeconds,
                avgPace = uiState.avgPaceSeconds,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { showEndConfirm = true },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                Text(
                    text = stringResource(R.string.volunteer_running_end),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            if (showEndConfirm) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showEndConfirm = false },
                    title = { Text(stringResource(R.string.volunteer_running_end_confirm_title)) },
                    text = { Text(stringResource(R.string.volunteer_running_end_confirm_message)) },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            showEndConfirm = false
                            viewModel.endRun()
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
private fun StatsGrid(
    distanceMeters: Int,
    durationSeconds: Int,
    currentPace: Int?,
    avgPace: Int?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StatItem(
            label = stringResource(R.string.volunteer_running_distance),
            value = "%.2f km".format(distanceMeters / 1000.0),
            fontSize = 48,
        )
        Spacer(Modifier.height(32.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StatItem(
                label = stringResource(R.string.volunteer_running_duration),
                value = formatDuration(durationSeconds),
            )
            StatItem(
                label = stringResource(R.string.volunteer_running_pace),
                value = currentPace?.let { PaceCalculator.formatPace(it) } ?: "--'--\"",
            )
            StatItem(
                label = stringResource(R.string.volunteer_running_avg_pace),
                value = avgPace?.let { PaceCalculator.formatPace(it) } ?: "--'--\"",
            )
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, fontSize: Int = 32) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = fontSize.sp,
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
