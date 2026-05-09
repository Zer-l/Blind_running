package com.guiderun.app.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guiderun.app.R
import com.guiderun.app.domain.model.UserRole

@Composable
fun RoleSelectScreen(
    onNavigateToHome: () -> Unit,
    viewModel: RoleSelectViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 注册流程必须完成，禁止返回上一页
    BackHandler(enabled = true) { viewModel.onBackAttempted() }

    LaunchedEffect(uiState.success) {
        if (uiState.success) {
            viewModel.onNavigated()
            onNavigateToHome()
        }
    }

    if (uiState.showBackWarning) {
        AlertDialog(
            onDismissRequest = viewModel::onBackWarningDismissed,
            title = { Text("请先选择角色") },
            text = { Text("完成角色选择后才能继续使用助盲跑。") },
            confirmButton = {
                TextButton(onClick = viewModel::onBackWarningDismissed) { Text("确定") }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.role_select_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(40.dp))

        RoleCard(
            title = stringResource(R.string.role_blind_runner),
            description = stringResource(R.string.role_blind_runner_desc),
            selected = uiState.selectedRole == UserRole.BLIND_RUNNER,
            onClick = { viewModel.onRoleSelected(UserRole.BLIND_RUNNER) },
        )
        Spacer(Modifier.height(16.dp))
        RoleCard(
            title = stringResource(R.string.role_volunteer),
            description = stringResource(R.string.role_volunteer_desc),
            selected = uiState.selectedRole == UserRole.VOLUNTEER,
            onClick = { viewModel.onRoleSelected(UserRole.VOLUNTEER) },
        )

        uiState.error?.let { msg ->
            Spacer(Modifier.height(12.dp))
            Text(text = msg, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(40.dp))

        Button(
            onClick = viewModel::confirm,
            enabled = uiState.selectedRole != null && !uiState.isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(stringResource(R.string.btn_confirm))
            }
        }
    }
}

@Composable
private fun RoleCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    OutlinedCard(
        onClick = onClick,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outline,
        ),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
