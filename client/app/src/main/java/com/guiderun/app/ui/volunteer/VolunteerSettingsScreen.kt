package com.guiderun.app.ui.volunteer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.guiderun.app.R
import com.guiderun.app.ui.shared.HomeViewModel
import com.guiderun.app.ui.theme.AppRadius
import com.guiderun.app.ui.theme.AppSpacing

/**
 * 志愿者设置页。
 *
 * 包含：个人资料入口、主题配色入口、退出登录（危险操作，使用 error 色区分）。
 * 退出登录通过 HomeViewModel 统一处理（清 Token + 更新 UiState.loggedOut），
 * LaunchedEffect 监听 loggedOut 完成后执行 onLogout() 回调，避免 ViewModel 直接依赖导航。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolunteerSettingsScreen(
    onBack: () -> Unit,
    onNavigateToProfile: () -> Unit = {},
    onNavigateToTheme: () -> Unit = {},
    onLogout: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.loggedOut) {
        if (uiState.loggedOut) {
            viewModel.onNavigated()
            onLogout()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "个人设置",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(AppSpacing.MD),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.SM),
        ) {
            // 个人资料
            SettingsEntry(
                icon = Icons.Default.Person,
                title = "个人资料",
                onClick = onNavigateToProfile,
            )

            // 主题配色
            SettingsEntry(
                icon = Icons.Default.Palette,
                title = "主题配色",
                onClick = onNavigateToTheme,
            )

            // 退出登录
            SettingsEntry(
                icon = Icons.AutoMirrored.Filled.Logout,
                title = stringResource(R.string.btn_logout),
                titleColor = MaterialTheme.colorScheme.error,
                onClick = { viewModel.logout() },
            )
        }
    }
}

@Composable
private fun SettingsEntry(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String = "",
    titleColor: androidx.compose.ui.graphics.Color = Color.Unspecified,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = AppRadius.LargeShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.MD),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.MD),
        ) {
            Surface(
                shape = AppRadius.MediumShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.padding(AppSpacing.SM),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = titleColor,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
