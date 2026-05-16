package com.guiderun.app.ui.volunteer

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guiderun.app.R
import com.guiderun.app.domain.model.RunRequestStatus
import com.guiderun.app.ui.common.CallPeerButton
import com.guiderun.app.ui.common.InterruptDialog
import com.guiderun.app.ui.shared.map.GuideRunMap
import com.guiderun.app.ui.theme.AppRadius
import com.guiderun.app.ui.theme.AppSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigatingScreen(
    onNavigateToMet: (String) -> Unit,
    onNavigateToHome: () -> Unit,
    viewModel: NavigatingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showInterruptDialog by remember { mutableStateOf(false) }

    BackHandler { showInterruptDialog = true }

    if (showInterruptDialog) {
        val status = uiState.request?.status
        val canInterrupt = status == RunRequestStatus.ACCEPTED || status == RunRequestStatus.EN_ROUTE
        InterruptDialog(
            title = stringResource(R.string.interrupt_title_leave_matched),
            message = stringResource(R.string.interrupt_message_leave_matched)
                + "\n" + stringResource(R.string.interrupt_hint_resume),
            onDismissRequest = { showInterruptDialog = false },
            cancelLabel = if (canInterrupt) stringResource(R.string.interrupt_btn_cancel_order) else null,
            onCancel = if (canInterrupt) ({
                showInterruptDialog = false
                viewModel.interruptByUser()
            }) else null,
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
                is NavigatingNavEvent.ToMet -> onNavigateToMet(event.requestId)
                NavigatingNavEvent.ToHome -> onNavigateToHome()
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
                        text = stringResource(R.string.navigating_title),
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
            // 地图区域 - 带圆角和内边距
            GuideRunMap(
                state = uiState.mapState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = AppSpacing.MD, vertical = AppSpacing.XS)
                    .clip(RoundedCornerShape(AppRadius.Large)),
            )

            // 底部信息面板 - 与 MetScreen 统一风格
            NavigatingBottomCard(
                runnerName = uiState.request?.blindRunner?.nickname ?: "",
                address = uiState.request?.meetingLocation?.description ?: "",
                isConfirming = uiState.isConfirmingMet,
                onArrivedClick = viewModel::onArrivedClick,
            )
        }
    }
}

@Composable
private fun NavigatingBottomCard(
    runnerName: String,
    address: String,
    isConfirming: Boolean,
    onArrivedClick: () -> Unit,
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
            // 跑友信息行
            if (runnerName.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.MD),
                ) {
                    Surface(
                        shape = AppRadius.MediumShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.padding(AppSpacing.SM),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Column {
                        Text(
                            text = "跑友",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = runnerName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            // 地点信息行
            if (address.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.MD),
                ) {
                    Surface(
                        shape = AppRadius.MediumShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.padding(AppSpacing.SM),
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "集合地点",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = address,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // 到达按钮
            Button(
                onClick = onArrivedClick,
                enabled = !isConfirming,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = AppRadius.LargeShape,
            ) {
                if (isConfirming) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(AppSpacing.SM))
                    Text(
                        text = stringResource(R.string.navigating_btn_arrived),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
