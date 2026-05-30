package com.guiderun.app.ui.volunteer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guiderun.app.R
import com.guiderun.app.domain.model.RunRequest
import com.guiderun.app.ui.shared.map.GuideRunMap
import com.guiderun.app.ui.shared.map.GuideRunMapState
import com.guiderun.app.ui.theme.AppRadius
import com.guiderun.app.ui.theme.AppSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolunteerRequestDetailScreen(
    onNavigateToNavigating: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: VolunteerRequestDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activeRequest by viewModel.activeRequest.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.navEvent.collect { event ->
            when (event) {
                is VolunteerRequestDetailNavEvent.ToNavigating -> onNavigateToNavigating(event.requestId)
            }
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.onErrorShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.request_detail_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
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
            when {
                uiState.isLoading && uiState.request == null -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                }
                uiState.request != null -> {
                    val request = uiState.request!!
                    // 上方：地图（与 VolunteerNavigatingScreen 一致风格）
                    // 接单前先看到双方位置，比单纯一个距离值更直观
                    GuideRunMap(
                        state = uiState.mapState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = AppSpacing.MD, vertical = AppSpacing.XS)
                            .clip(RoundedCornerShape(AppRadius.Large)),
                    )

                    // 下方：信息卡 + 接受按钮（紧凑布局）
                    RequestDetailBottomCard(
                        request = request,
                        isAccepting = uiState.isAccepting,
                        hasActiveOrder = activeRequest != null && activeRequest?.id != request.id,
                        onAccept = viewModel::onAccept,
                    )
                }
            }
        }
    }
}

@Composable
private fun RequestDetailBottomCard(
    request: RunRequest,
    isAccepting: Boolean,
    hasActiveOrder: Boolean,
    onAccept: () -> Unit,
) {
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
            modifier = Modifier.padding(AppSpacing.LG),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.MD),
        ) {
            // 跑友姓名 = 订单核心信息：与 VolunteerNavigatingScreen 一致，titleMedium + SemiBold 突出
            DetailInfoRow(
                icon = Icons.Default.Person,
                label = stringResource(R.string.request_detail_label_runner),
                value = request.blindRunner?.nickname ?: "",
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                iconTint = MaterialTheme.colorScheme.primary,
                isPrimary = true,
            )
            DetailInfoRow(
                icon = Icons.Default.LocationOn,
                label = stringResource(R.string.request_detail_label_location),
                value = request.meetingLocation.description,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                iconTint = MaterialTheme.colorScheme.secondary,
            )
            DetailInfoRow(
                icon = Icons.Default.AccessTime,
                label = stringResource(R.string.request_detail_label_duration),
                value = stringResource(R.string.request_detail_duration_minutes_value, request.expectedDurationMinutes),
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                iconTint = MaterialTheme.colorScheme.tertiary,
            )
            request.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                DetailInfoRow(
                    icon = Icons.AutoMirrored.Filled.Notes,
                    label = stringResource(R.string.request_detail_label_notes),
                    value = notes,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 有进行中订单的提示横幅
            AnimatedVisibility(
                visible = hasActiveOrder,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Surface(
                    shape = AppRadius.MediumShape,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(AppSpacing.MD),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.SM),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = stringResource(R.string.request_detail_blocked_by_active),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }

            // 接受按钮
            Button(
                onClick = onAccept,
                enabled = !isAccepting && !hasActiveOrder,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = AppRadius.LargeShape,
            ) {
                if (isAccepting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.request_detail_btn_accept),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/**
 * 订单信息行：与 VolunteerNavigatingScreen 共用视觉规范。
 * @param isPrimary 跑友姓名等核心信息传 true，使用 titleMedium + SemiBold 突出；其它行默认 bodyMedium。
 */
@Composable
private fun DetailInfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    containerColor: Color,
    iconTint: Color,
    isPrimary: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.MD),
    ) {
        Surface(
            shape = AppRadius.MediumShape,
            color = containerColor,
            modifier = Modifier.size(48.dp), // 与 Navigating 一致：48dp 圆角图标容器
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.padding(AppSpacing.SM),
                tint = iconTint,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            if (isPrimary) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
