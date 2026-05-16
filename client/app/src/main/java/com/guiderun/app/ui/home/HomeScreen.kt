package com.guiderun.app.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.guiderun.app.R
import com.guiderun.app.domain.model.RunRequest
import com.guiderun.app.domain.model.RunRequestStatus
import com.guiderun.app.domain.model.UserRole
import com.guiderun.app.ui.theme.AppRadius
import com.guiderun.app.ui.theme.AppSpacing

@Composable
fun HomeScreen(
    onLoggedOut: () -> Unit,
    onEnterBlindFlow: () -> Unit,
    onEnterBlindSettings: () -> Unit = {},
    onEnterBlindHistory: () -> Unit = {},
    onEnterVolunteerFlow: () -> Unit,
    onNavigateToProfile: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    onResumeActiveOrder: (RunRequest) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val activeRequest by viewModel.activeRequest.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refreshUser()
        }
    }

    LaunchedEffect(uiState.loggedOut) {
        if (uiState.loggedOut) {
            viewModel.onNavigated()
            onLoggedOut()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (uiState.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(AppSpacing.XL),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(48.dp))

                // 应用标题
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )

                Spacer(Modifier.height(AppSpacing.XXL))

                // 用户信息卡片
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppRadius.LargeShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AppSpacing.LG),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.home_welcome, uiState.nickname),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(AppSpacing.XS))
                        Text(
                            text = stringResource(R.string.home_current_role, uiState.activeRole),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        )
                    }
                }

                activeRequest?.let { request ->
                    Spacer(Modifier.height(AppSpacing.MD))
                    ActiveOrderBanner(
                        request = request,
                        onClick = { onResumeActiveOrder(request) },
                    )
                }

                Spacer(Modifier.height(AppSpacing.XXL))

                // 功能按钮区域
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.SM),
                ) {
                    // 视障端入口：有进行中订单时禁用「开始跑步」，引导用户通过横幅恢复
                    if (uiState.activeRoleEnum == UserRole.BLIND_RUNNER) {
                        HomeMenuItem(
                            icon = Icons.AutoMirrored.Filled.DirectionsRun,
                            title = stringResource(R.string.home_btn_enter_blind),
                            subtitle = if (activeRequest != null)
                                stringResource(R.string.home_btn_disabled_has_active)
                            else stringResource(R.string.home_btn_start_running_desc),
                            onClick = onEnterBlindFlow,
                            isPrimary = true,
                            enabled = activeRequest == null,
                        )
                        HomeMenuItem(
                            icon = Icons.Default.Settings,
                            title = stringResource(R.string.home_btn_settings),
                            subtitle = stringResource(R.string.home_btn_settings_desc),
                            onClick = onEnterBlindSettings,
                        )
                        HomeMenuItem(
                            icon = Icons.Default.History,
                            title = stringResource(R.string.home_btn_history),
                            subtitle = stringResource(R.string.home_btn_history_desc),
                            onClick = onEnterBlindHistory,
                        )
                    }

                    // 志愿者端入口：有进行中订单时禁用「开始接单」，引导用户通过横幅恢复
                    if (uiState.activeRoleEnum == UserRole.VOLUNTEER) {
                        HomeMenuItem(
                            icon = Icons.Default.VolunteerActivism,
                            title = stringResource(R.string.home_btn_enter_volunteer),
                            subtitle = if (activeRequest != null)
                                stringResource(R.string.home_btn_disabled_has_active)
                            else stringResource(R.string.home_btn_enter_volunteer_desc),
                            onClick = onEnterVolunteerFlow,
                            isPrimary = true,
                            enabled = activeRequest == null,
                        )
                        HomeMenuItem(
                            icon = Icons.Default.History,
                            title = stringResource(R.string.home_btn_history),
                            subtitle = stringResource(R.string.home_btn_history_desc),
                            onClick = onNavigateToHistory,
                        )
                        HomeMenuItem(
                            icon = Icons.Default.Settings,
                            title = stringResource(R.string.home_btn_settings),
                            subtitle = stringResource(R.string.home_btn_settings_desc),
                            onClick = onNavigateToProfile,
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                // 退出登录
                OutlinedButton(
                    onClick = viewModel::logout,
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppRadius.LargeShape,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(AppSpacing.SM))
                    Text(stringResource(R.string.btn_logout))
                }

                Spacer(Modifier.height(AppSpacing.MD))
            }
        }
    }
}

@Composable
private fun ActiveOrderBanner(
    request: RunRequest,
    onClick: () -> Unit,
) {
    val statusText = when (request.status) {
        RunRequestStatus.MATCHING  -> "等待匹配中"
        RunRequestStatus.ACCEPTED  -> "已匹配志愿者"
        RunRequestStatus.EN_ROUTE  -> "志愿者前往中"
        RunRequestStatus.MET       -> "已汇合"
        RunRequestStatus.RUNNING   -> "跑步中"
        RunRequestStatus.FINISHED  -> "等待评价"
        else                       -> "进行中"
    }
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                role = Role.Button
                contentDescription = "您有进行中的订单：$statusText，双击恢复"
            },
        shape = AppRadius.LargeShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.MD),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = AppRadius.MediumShape,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.DirectionsRun,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiary,
                    modifier = Modifier.padding(AppSpacing.SM),
                )
            }
            Spacer(Modifier.width(AppSpacing.MD))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "您有进行中的订单",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    text = "$statusText · 点击恢复",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun HomeMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    isPrimary: Boolean = false,
    enabled: Boolean = true,
) {
    if (isPrimary) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            shape = AppRadius.LargeShape,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(AppSpacing.SM))
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                )
            }
        }
    } else {
        OutlinedCard(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = AppRadius.LargeShape,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppSpacing.MD),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = AppRadius.MediumShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.padding(AppSpacing.SM),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.width(AppSpacing.MD))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
