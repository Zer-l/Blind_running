package com.guiderun.app.ui.volunteer

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guiderun.app.R
import com.guiderun.app.ui.common.CallPeerButton
import com.guiderun.app.ui.common.InterruptDialog
import com.guiderun.app.ui.shared.map.GuideRunMap

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetScreen(
    onNavigateToHome: () -> Unit,
    onNavigateToRunning: (String) -> Unit,
    viewModel: MetViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showInterruptDialog by remember { mutableStateOf(false) }

    // MET 状态服务端不允许 cancel/abandon，仅显示「留在此页/返回首页」
    BackHandler { showInterruptDialog = true }

    if (showInterruptDialog) {
        InterruptDialog(
            title = stringResource(R.string.interrupt_title_leave_met),
            message = stringResource(R.string.interrupt_message_leave_met)
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
                MetNavEvent.ToHome -> onNavigateToHome()
                is MetNavEvent.ToRunning -> onNavigateToRunning(event.requestId)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.met_title)) },
                actions = {
                    CallPeerButton(phone = uiState.request?.blindRunner?.phone)
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            GuideRunMap(
                state = uiState.mapState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )

            // 状态卡片
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.People,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = uiState.statusMessage.ifEmpty { stringResource(R.string.met_waiting) },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}
