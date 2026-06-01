package com.guiderun.app.ui.volunteer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.guiderun.app.R
import com.guiderun.app.domain.model.RunRequest
import com.guiderun.app.domain.model.RunRequestStatus
import com.guiderun.app.ui.shared.HomeViewModel
import com.guiderun.app.ui.theme.AppRadius
import com.guiderun.app.ui.theme.AppSpacing

/**
 * 志愿者端首页（HomeScreen）。
 *
 * 职责：展示欢迎信息、进行中订单横幅（点击恢复流程）、三个功能入口（接单/历史/设置）。
 * 生命周期感知：repeatOnLifecycle(RESUMED) 刷新用户信息，保证从后台回来昵称始终最新。
 * 权限策略：首次 UI 加载完成后一次性请求定位+通知权限（rememberSaveable 防重复弹）；
 * 视障端不进此页，由 BaseBlindActivity.onCreate 批量申请。
 */
@Composable
fun VolunteerHomeScreen(
    onLoggedOut: () -> Unit,
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

    // 志愿者端首次进入首页一次性申请核心权限。
    // 视障端不进入本页（MainActivity 已按 StartTarget.BlindHome 直接启动 BlindActivity 并 finish），
    // 视障端权限由 BaseBlindActivity.onCreate 批量申请。
    val context = LocalContext.current
    val basePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* 静默处理；细粒度反馈由后续单点入口给出 */ }
    var basePermissionRequested by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(uiState.isLoading) {
        if (uiState.isLoading || basePermissionRequested) return@LaunchedEffect
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
                        // nickname/role 任一为空时回退到匿名/默认文案，避免"你好，"/"当前角色："这类断尾
                        val welcomeText = if (uiState.nickname.isNotBlank()) {
                            stringResource(R.string.home_welcome, uiState.nickname)
                        } else {
                            stringResource(R.string.home_welcome_anonymous)
                        }
                        val roleText = stringResource(
                            R.string.home_current_role,
                            uiState.activeRole.ifBlank { stringResource(R.string.home_role_default_volunteer) },
                        )
                        Text(
                            text = welcomeText,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(AppSpacing.XS))
                        Text(
                            text = roleText,
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

                Spacer(Modifier.height(AppSpacing.MD))

                // 功能按钮区域
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.SM),
                ) {
                    HomeMenuItem(
                        icon = Icons.Default.VolunteerActivism,
                        title = stringResource(R.string.home_btn_enter_volunteer),
                        subtitle = if (activeRequest != null)
                            stringResource(R.string.home_btn_disabled_has_active)
                        else "",
                        onClick = onEnterVolunteerFlow,
                        isPrimary = true,
                        enabled = activeRequest == null,
                    )
                    HomeMenuItem(
                        icon = Icons.Default.History,
                        title = stringResource(R.string.home_btn_history),
                        onClick = onNavigateToHistory,
                    )
                    HomeMenuItem(
                        icon = Icons.Default.Settings,
                        title = stringResource(R.string.home_btn_settings),
                        onClick = onNavigateToProfile,
                    )
                }

                Spacer(Modifier.weight(1f))

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

/**
 * 统一卡片结构：圆形图标容器 + 标题/副标题 + ChevronRight。
 * 主次仅靠颜色区分（primary=填充实心，secondary=Outlined 描边），
 * 保证"开始跑步"与下方"历史/设置"对齐方式、间距、图标尺寸完全一致，
 * 文字天然左对齐（Column.weight(1f) 撑满左侧 + 默认 Start 对齐）。
 */
@Composable
private fun HomeMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String = "",
    onClick: () -> Unit,
    isPrimary: Boolean = false,
    enabled: Boolean = true,
) {
    val containerColor = if (isPrimary) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (isPrimary) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val iconBg = if (isPrimary) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val iconTint = if (isPrimary) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.primary
    }
    val subtitleColor = if (isPrimary) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val chevronTint = contentColor.copy(alpha = 0.6f)

    val cardModifier = Modifier
        .fillMaxWidth()
        .heightIn(min = if (isPrimary) 72.dp else 64.dp)

    val rowContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.MD),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = AppRadius.MediumShape,
                color = iconBg,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.padding(AppSpacing.SM),
                    tint = iconTint,
                )
            }
            Spacer(Modifier.width(AppSpacing.MD))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = subtitleColor,
                    )
                }
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = chevronTint,
            )
        }
    }

    if (isPrimary) {
        Card(
            onClick = onClick,
            enabled = enabled,
            modifier = cardModifier,
            shape = AppRadius.LargeShape,
            colors = CardDefaults.cardColors(containerColor = containerColor),
        ) { rowContent() }
    } else {
        OutlinedCard(
            onClick = onClick,
            enabled = enabled,
            modifier = cardModifier,
            shape = AppRadius.LargeShape,
        ) { rowContent() }
    }
}

