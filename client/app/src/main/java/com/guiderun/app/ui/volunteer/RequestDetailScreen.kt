package com.guiderun.app.ui.volunteer

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Person
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestDetailScreen(
    onNavigateToNavigating: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: RequestDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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
                title = { Text(stringResource(R.string.request_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                uiState.request != null -> RequestDetailContent(
                    request = uiState.request!!,
                    isAccepting = uiState.isAccepting,
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
    onAccept: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        // 信息卡片
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 跑友信息
                DetailInfoRow(
                    icon = Icons.Default.Person,
                    label = stringResource(R.string.request_detail_runner, ""),
                    value = request.blindRunner?.nickname ?: "",
                )

                // 地点
                DetailInfoRow(
                    icon = Icons.Default.LocationOn,
                    label = "",
                    value = request.meetingLocation.description,
                )

                // 预计时长
                DetailInfoRow(
                    icon = Icons.Default.AccessTime,
                    label = "",
                    value = stringResource(R.string.request_detail_duration, request.expectedDurationMinutes),
                )

                // 备注
                request.notes?.let { notes ->
                    DetailInfoRow(
                        icon = Icons.AutoMirrored.Filled.Notes,
                        label = "",
                        value = notes,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // 接单按钮
        Button(
            onClick = onAccept,
            enabled = !isAccepting,
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
        ) {
            if (isAccepting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(
                    text = stringResource(R.string.request_detail_btn_accept),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun DetailInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp).padding(top = 2.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Column {
            if (label.isNotEmpty()) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
