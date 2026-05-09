package com.guiderun.app.ui.volunteer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guiderun.app.R
import com.guiderun.app.ui.shared.map.GuideRunMap

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackPlaybackScreen(
    requestId: String,
    onBack: () -> Unit,
    viewModel: TrackPlaybackViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onErrorShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.track_playback_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                uiState.tracks.isEmpty() -> Text(
                    text = stringResource(R.string.track_playback_empty),
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Stats cards
                        TrackStatsRow(
                            avgPaceSeconds = uiState.avgPaceSeconds,
                            maxSpeed = uiState.maxSpeed,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        // Legend
                        TrackLegendRow(modifier = Modifier.padding(horizontal = 16.dp))
                        // Map
                        GuideRunMap(
                            state = uiState.mapState,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                        // Playback controls
                        PlaybackControlsRow(
                            isPlaying = uiState.isPlaying,
                            speedMultiplier = uiState.speedMultiplier,
                            currentIndex = uiState.currentIndex,
                            totalPoints = uiState.totalPoints,
                            onToggle = { viewModel.togglePlayback() },
                            onSpeedChange = { viewModel.setSpeed(it) },
                            onSeekToStart = { viewModel.seekToStart() },
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackStatsRow(
    avgPaceSeconds: Int?,
    maxSpeed: Float?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        StatItem(
            value = avgPaceSeconds?.let { "%d'%02d\"".format(it / 60, it % 60) } ?: "--",
            label = "均速(/km)",
        )
        StatItem(
            value = maxSpeed?.let { "%.1f m/s".format(it) } ?: "--",
            label = "最高速度",
        )
    }
}

@Composable
private fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TrackLegendRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendItem(color = Color(0xFFE53935), label = "<4'")
        LegendItem(color = Color(0xFFFB8C00), label = "4-5'")
        LegendItem(color = Color(0xFFFDD835), label = "5-6'")
        LegendItem(color = Color(0xFF7CB342), label = "6-8'")
        LegendItem(color = Color(0xFF00C853), label = ">8'")
        Text(
            text = "/km",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlaybackControlsRow(
    isPlaying: Boolean,
    speedMultiplier: Int,
    currentIndex: Int,
    totalPoints: Int,
    onToggle: () -> Unit,
    onSpeedChange: (Int) -> Unit,
    onSeekToStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Progress text
        Text(
            text = "%d / %d".format(currentIndex, totalPoints),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Restart
            FilledIconButton(
                onClick = onSeekToStart,
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "重新播放")
            }
            Spacer(modifier = Modifier.width(16.dp))
            // Play/Pause
            FilledIconButton(
                onClick = onToggle,
                modifier = Modifier.size(64.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    modifier = Modifier.size(36.dp),
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            // Speed selector
            val speeds = listOf(5, 10, 20)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                speeds.forEach { speed ->
                    TextButton(
                        onClick = { onSpeedChange(speed) },
                    ) {
                        Text(
                            text = "${speed}x",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (speed == speedMultiplier) FontWeight.Bold else FontWeight.Normal,
                            color = if (speed == speedMultiplier) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }
    }
}

