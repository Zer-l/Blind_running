package com.guiderun.app.ui.volunteer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guiderun.app.R
import com.guiderun.app.domain.model.RunRequest
import com.guiderun.app.domain.model.RunRequestStatus
import com.guiderun.app.ui.theme.AppRadius
import com.guiderun.app.ui.theme.AppSpacing
import com.guiderun.app.util.DateFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolunteerHistoryScreen(
    onBack: () -> Unit,
    onNavigateToTrackPlayback: (String) -> Unit,
    onNavigateToReview: (String) -> Unit = {},
    viewModel: VolunteerHistoryViewModel = hiltViewModel(),
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
                        text = stringResource(R.string.volunteer_history_title),
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
        when {
            uiState.isLoading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            uiState.requests.isEmpty() -> EmptyHistoryContent(padding)
            else -> HistoryList(
                requests = uiState.requests,
                totalRuns = uiState.totalRuns,
                totalDistanceKm = uiState.totalDistanceKm,
                totalDurationHours = uiState.totalDurationHours,
                badges = uiState.badges.map { it.name },
                onItemClick = onNavigateToTrackPlayback,
                onReviewClick = onNavigateToReview,
                padding = padding,
            )
        }
    }
}

@Composable
private fun HistoryList(
    requests: List<RunRequest>,
    totalRuns: Int,
    totalDistanceKm: Float,
    totalDurationHours: Float,
    badges: List<String>,
    onItemClick: (String) -> Unit,
    onReviewClick: (String) -> Unit,
    padding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier.padding(padding),
        contentPadding = PaddingValues(bottom = AppSpacing.XL),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.SM),
    ) {
        // Hero 统计卡片
        item(key = "stats") {
            VolunteerStatsCard(
                totalRuns = totalRuns,
                totalDistanceKm = totalDistanceKm,
                totalDurationHours = totalDurationHours,
            )
        }

        // 徽章区域
        if (badges.isNotEmpty()) {
            item(key = "badges") {
                BadgesSection(badges = badges)
            }
        }

        // 区域标题
        item(key = "section_title") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.MD, vertical = AppSpacing.SM),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "跑步记录",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Surface(
                    shape = AppRadius.SmallShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = "${requests.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = AppSpacing.XS, vertical = AppSpacing.XS),
                    )
                }
            }
        }

        // 历史列表
        items(requests, key = { it.id }) { request ->
            HistoryCard(
                request = request,
                onClick = { onItemClick(request.id) },
                onReviewClick = { onReviewClick(request.id) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BadgesSection(badges: List<String>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.MD),
        shape = AppRadius.LargeShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(AppSpacing.MD)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.SM),
            ) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.secondary,
                )
                Text(
                    text = "我的徽章",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Spacer(modifier = Modifier.height(AppSpacing.MD))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.SM),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.SM),
            ) {
                badges.forEach { name ->
                    BadgeChip(name = name)
                }
            }
        }
    }
}

@Composable
private fun BadgeChip(name: String) {
    Surface(
        shape = AppRadius.SmallShape,
        color = MaterialTheme.colorScheme.secondary,
        contentColor = MaterialTheme.colorScheme.onSecondary,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = AppSpacing.SM, vertical = AppSpacing.XS),
            maxLines = 1,
        )
    }
}

@Composable
private fun VolunteerStatsCard(
    totalRuns: Int,
    totalDistanceKm: Float,
    totalDurationHours: Float,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.MD),
        shape = AppRadius.LargeShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(AppSpacing.LG)) {
            Text(
                text = "我的陪跑统计",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(modifier = Modifier.height(AppSpacing.LG))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StatsItem(
                    value = "$totalRuns",
                    label = "完成次数",
                    icon = Icons.AutoMirrored.Filled.DirectionsRun,
                )
                StatsItem(
                    value = "%.1f".format(totalDistanceKm),
                    label = "总距离(km)",
                    icon = Icons.Default.Timer,
                )
                StatsItem(
                    value = "%.1f".format(totalDurationHours),
                    label = "总时长(h)",
                    icon = Icons.Default.Timer,
                )
            }
        }
    }
}

@Composable
private fun StatsItem(
    value: String,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.XS),
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
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun EmptyHistoryContent(padding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
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
                imageVector = Icons.Default.History,
                contentDescription = null,
                modifier = Modifier.padding(AppSpacing.LG),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(AppSpacing.LG))
        Text(
            text = stringResource(R.string.volunteer_history_empty),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(AppSpacing.XS))
        Text(
            text = "完成陪跑后，记录将显示在这里",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HistoryCard(
    request: RunRequest,
    onClick: () -> Unit,
    onReviewClick: () -> Unit,
) {
    val canReview = request.status.isCompleted() && request.myReviewSubmitted == false

    // 双端统一布局：
    //   [行1] Person 图标 | 用户名（大字突出）         状态 chip
    //   [行2]            | 时间
    //   [行3]            | 地点
    //   [底部]                                     补充评价按钮（仅可评时）
    val timestamp = request.runStartedAt ?: request.createdAt
    val timeLabel = DateFormat.historyDateTime(timestamp)
    val userName = request.blindRunner?.nickname ?: "未知跑友"

    ElevatedCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.MD),
        shape = AppRadius.LargeShape,
    ) {
        Row(
            modifier = Modifier.padding(AppSpacing.MD),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.MD),
        ) {
            // 左侧 Person 图标（志愿者端保留）
            Surface(
                shape = AppRadius.MediumShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.padding(AppSpacing.SM),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            // 右侧主信息列
            Column(modifier = Modifier.weight(1f)) {
                // 行1：用户名（突出，比时间/地点大一号） + 状态 chip 右对齐
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = userName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(AppSpacing.SM))
                    StatusChip(status = request.status)
                }
                Spacer(modifier = Modifier.height(AppSpacing.XS))
                // 行2：时间
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(AppSpacing.XS))
                // 行3：地点
                Text(
                    text = request.meetingLocation.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                // 补评按钮：右对齐
                if (canReview) {
                    Spacer(modifier = Modifier.height(AppSpacing.SM))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        OutlinedButton(
                            onClick = onReviewClick,
                            shape = AppRadius.MediumShape,
                        ) {
                            Text(stringResource(R.string.volunteer_history_btn_review))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: RunRequestStatus) {
    val (containerColor, contentColor, text) = when {
        status.isCompleted() -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            "已完成",
        )
        status == RunRequestStatus.ABORTED -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            "已取消",
        )
        else -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            status.name,
        )
    }

    Surface(
        shape = AppRadius.SmallShape,
        color = containerColor,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = contentColor,
            modifier = Modifier.padding(horizontal = AppSpacing.SM, vertical = AppSpacing.XS),
        )
    }
}
