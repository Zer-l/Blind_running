package com.guiderun.app.ui.volunteer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.guiderun.app.ui.theme.AppRadius
import com.guiderun.app.ui.theme.AppSpacing

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
                title = {
                    Text(
                        text = stringResource(R.string.track_playback_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
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
                uiState.tracks.isEmpty() -> EmptyTrackContent()
                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // 统计卡片
                        TrackStatsRow(
                            avgPaceSeconds = uiState.avgPaceSeconds,
                            maxSpeed = uiState.maxSpeed,
                            modifier = Modifier.padding(
                                horizontal = AppSpacing.MD,
                                vertical = AppSpacing.XS,
                            ),
                        )
                        // 图例
                        TrackLegendRow(
                            modifier = Modifier.padding(horizontal = AppSpacing.MD),
                        )
                        // 地图 - 带圆角和内边距
                        GuideRunMap(
                            state = uiState.mapState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = AppSpacing.MD, vertical = AppSpacing.XS)
                                .clip(RoundedCornerShape(AppRadius.Large)),
                        )
                        // 播放控制
                        PlaybackControlsRow(
                            isPlaying = uiState.isPlaying,
                            speedMultiplier = uiState.speedMultiplier,
                            currentIndex = uiState.currentIndex,
                            totalPoints = uiState.totalPoints,
                            onToggle = { viewModel.togglePlayback() },
                            onSpeedChange = { viewModel.setSpeed(it) },
                            onSeekToStart = { viewModel.seekToStart() },
                            modifier = Modifier.padding(horizontal = AppSpacing.MD, vertical = AppSpacing.SM),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyTrackContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(AppSpacing.XXL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = AppRadius.ExtraLargeShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(96.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Timer,
                contentDescription = null,
                modifier = Modifier.padding(AppSpacing.LG),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(AppSpacing.LG))
        Text(
            text = stringResource(R.string.track_playback_empty),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun TrackStatsRow(
    avgPaceSeconds: Int?,
    maxSpeed: Float?,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
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
                value = avgPaceSeconds?.let { "%d'%02d\"".format(it / 60, it % 60) } ?: "--",
                label = "均速(/km)",
                icon = Icons.Default.Speed,
            )
            StatItem(
                value = maxSpeed?.let { "%.1f m/s".format(it) } ?: "--",
                label = "最高速度",
                icon = Icons.Default.Timer,
            )
        }
    }
}

@Composable
private fun StatItem(
    value: String,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.SM),
    ) {
        Surface(
            shape = AppRadius.MediumShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            modifier = Modifier.size(44.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.padding(AppSpacing.SM),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrackLegendRow(modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.SM, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.XS),
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
        Spacer(modifier = Modifier.width(AppSpacing.XS))
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
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = AppRadius.LargeShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.SM),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 进度文本
            Text(
                text = "%d / %d".format(currentIndex, totalPoints),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(AppSpacing.SM))

            // 控制按钮行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 速度选择
                val speeds = listOf(5, 10, 20)
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.XS)) {
                    speeds.forEach { speed ->
                        Surface(
                            shape = AppRadius.SmallShape,
                            color = if (speed == speedMultiplier) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            onClick = { onSpeedChange(speed) },
                        ) {
                            Text(
                                text = "${speed}x",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (speed == speedMultiplier) FontWeight.Bold else FontWeight.Normal,
                                color = if (speed == speedMultiplier) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = AppSpacing.SM, vertical = AppSpacing.XS),
                            )
                        }
                    }
                }

                // 重新播放
                FilledIconButton(
                    onClick = onSeekToStart,
                    modifier = Modifier.size(44.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "重新播放",
                        modifier = Modifier.size(20.dp),
                    )
                }

                // 播放/暂停
                FilledIconButton(
                    onClick = onToggle,
                    modifier = Modifier.size(56.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
    }
}
