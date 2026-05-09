package com.guiderun.app.ui.volunteer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guiderun.app.R
import com.guiderun.app.ui.shared.map.GuideRunMap

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigatingScreen(
    onNavigateToMet: (String) -> Unit,
    onNavigateToHome: () -> Unit,
    viewModel: NavigatingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

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
        topBar = { TopAppBar(title = { Text(stringResource(R.string.navigating_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            GuideRunMap(
                state = uiState.mapState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            NavigatingBottomPanel(
                address = uiState.request?.meetingLocation?.description ?: "",
                isConfirming = uiState.isConfirmingMet,
                onArrivedClick = viewModel::onArrivedClick,
            )
        }
    }
}

@Composable
private fun NavigatingBottomPanel(
    address: String,
    isConfirming: Boolean,
    onArrivedClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (address.isNotEmpty()) {
            Text(
                text = stringResource(R.string.navigating_destination, address),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Button(
            onClick = onArrivedClick,
            enabled = !isConfirming,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            if (isConfirming) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.navigating_btn_arrived))
            }
        }
    }
}
