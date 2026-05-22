package com.guiderun.app.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun HomeScreen(
    onLoggedOut: () -> Unit,
    onEnterBlindHome: () -> Unit,
    onEnterBlindFlow: () -> Unit,
    onEnterBlindSettings: () -> Unit = {},
    onEnterBlindHistory: () -> Unit = {},
    onQuickStartBlindFlow: () -> Unit = {},
    onEnterVolunteerFlow: () -> Unit,
    onNavigateToProfile: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    onResumeActiveOrder: (RunRequest) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val activeRequest by viewModel.activeRequest.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val quickStartEnabled by viewModel.quickStartEnabled.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refreshUser()
        }
    }

    // 视障端用户的设计系统统一在 XML（BlindActivity + BlindHomeFragment）。
    // 加载完成后检测到 BLIND_RUNNER 即跳走，不渲染 Compose HomeScreen 的视障端入口。
    LaunchedEffect(uiState.activeRoleEnum, uiState.isLoading) {
        if (uiState.isLoading) return@LaunchedEffect
        if (uiState.activeRoleEnum == UserRole.BLIND_RUNNER) {
            onEnterBlindHome()
        }
    }

    // 志愿者端首次进入首页一次性申请核心权限。
    // 视障端用户不会走到这（上面 LaunchedEffect 已跳走），权限由 BaseBlindActivity.onCreate 批量申请。
    val context = LocalContext.current
    val basePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* 静默处理；细粒度反馈由后续单点入口给出 */ }
    var basePermissionRequested by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(uiState.isLoading, uiState.activeRoleEnum) {
        if (uiState.isLoading || basePermissionRequested) return@LaunchedEffect
        if (uiState.activeRoleEnum != UserRole.VOLUNTEER) return@LaunchedEffect
        val missing = buildList {
            if (ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        basePermissionRequested = true
        if (missing.isNotEmpty()) {
            basePermissionLauncher.launch(missing.toTypedArray())
        }
    }

    LaunchedEffect(uiState.loggedOut) {
        if (uiState.loggedOut) {
            viewModel.onNavigated()
            onLoggedOut()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 视障端用户已被 LaunchedEffect 路由到 BlindActivity，避免 Compose 一闪而过的视觉断裂
        if (uiState.isLoading || uiState.activeRoleEnum == UserRole.BLIND_RUNNER) {
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
                    // 视障端用户在加载完成后已被 LaunchedEffect 路由到 BlindActivity，
                    // 此处仅渲染志愿者入口（compact 双角色场景下，志愿者切到视障会通过 LaunchedEffect 自动跳转）
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
    onLongPress: (() -> Unit)? = null,
) {
    if (isPrimary) {
        if (onLongPress != null) {
            // 视障端一键发起：长按 2s 触发 onLongPress，短按 onClick
            // 用 Surface + pointerInput 自定义手势，因为 Button 的 onClick 与 pointerInput 长按检测冲突
            BlindPrimaryButton(
                icon = icon,
                title = title,
                subtitle = subtitle,
                onClick = onClick,
                onLongPress = onLongPress,
                enabled = enabled,
            )
        } else {
            Button(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp),
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

/**
 * 视障端主入口按钮：短按 onClick + 长按 2 秒 onLongPress。
 *
 * 长按 2 秒明显长于 Material 默认 500ms，避免视障用户偶尔停顿造成误触。
 * 长按触发时给一次 LongPress haptic 反馈，等用户抬起再退出当前手势。
 */
@Composable
private fun BlindPrimaryButton(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    enabled: Boolean,
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    val released = withTimeoutOrNull(LONG_PRESS_MS) {
                        waitForUpOrCancellation()
                    }
                    if (released != null) {
                        onClick()
                    } else {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongPress()
                        waitForUpOrCancellation()
                    }
                }
            }
            .semantics {
                role = Role.Button
                contentDescription = "$title。$subtitle。双击进入正常发起，长按 2 秒一键发起"
            },
        shape = AppRadius.LargeShape,
        color = if (enabled) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (enabled) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.LG, vertical = AppSpacing.MD),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(AppSpacing.SM))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = 0.8f),
                )
            }
        }
    }
}

private const val LONG_PRESS_MS: Long = 2_000L
