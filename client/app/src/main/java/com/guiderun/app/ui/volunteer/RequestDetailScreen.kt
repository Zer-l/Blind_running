package com.guiderun.app.ui.volunteer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guiderun.app.R
import com.guiderun.app.domain.model.RunRequest
import com.guiderun.app.ui.theme.AppRadius
import com.guiderun.app.ui.theme.AppSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestDetailScreen(
    onNavigateToNavigating: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: RequestDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activeRequest by viewModel.activeRequest.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.navEvent.collect { event ->
            when (event) {
                is RequestDetailNavEvent.ToNavigating -> onNavigateToNavigating(event.requestId)
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
                uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                }
                uiState.request != null -> RequestDetailContent(
                    request = uiState.request!!,
                    isAccepting = uiState.isAccepting,
                    hasActiveOrder = activeRequest != null && activeRequest?.id != uiState.request!!.id,
                    onAccept = viewModel::onAccept,
                )
            }
        }
    }
}

@Composable
private fun RequestDetailContent(
    request: RunRequest,
    isAccepting: Boolean,
    hasActiveOrder: Boolean,
    onAccept: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        // 信息卡片
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.MD),
            shape = AppRadius.LargeShape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column(
                modifier = Modifier.padding(AppSpacing.LG),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.LG),
            ) {
                // 跑友信息
                DetailInfoRow(
                    icon = Icons.Default.Person,
                    label = "跑友",
                    value = request.blindRunner?.nickname ?: "",
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconTint = MaterialTheme.colorScheme.primary,
                )

                // 地点
                DetailInfoRow(
                    icon = Icons.Default.LocationOn,
                    label = "集合地点",
                    value = request.meetingLocation.description,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    iconTint = MaterialTheme.colorScheme.secondary,
                )

                // 预计时长
                DetailInfoRow(
                    icon = Icons.Default.AccessTime,
                    label = "预计时长",
                    value = stringResource(R.string.request_detail_duration, request.expectedDurationMinutes),
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    iconTint = MaterialTheme.colorScheme.tertiary,
                )

                // 备注
                request.notes?.let { notes ->
                    DetailInfoRow(
                        icon = Icons.AutoMirrored.Filled.Notes,
                        label = "备注",
                        value = notes,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // 有进行中订单时的提示横幅
        AnimatedVisibility(
            visible = hasActiveOrder,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.MD),
                shape = AppRadius.MediumShape,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
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

        Spacer(Modifier.height(AppSpacing.MD))

        // 接单按钮
        Button(
            onClick = onAccept,
            enabled = !isAccepting && !hasActiveOrder,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = AppSpacing.MD),
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
        Spacer(Modifier.height(AppSpacing.MD))
    }
}

@Composable
private fun DetailInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    containerColor: androidx.compose.ui.graphics.Color,
    iconTint: androidx.compose.ui.graphics.Color,
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.MD),
    ) {
        Surface(
            shape = AppRadius.MediumShape,
            color = containerColor,
            modifier = Modifier.size(44.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.padding(AppSpacing.SM),
                tint = iconTint,
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(AppSpacing.XS),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 3,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}
