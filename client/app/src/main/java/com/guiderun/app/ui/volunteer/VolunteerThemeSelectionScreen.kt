package com.guiderun.app.ui.volunteer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guiderun.app.ui.theme.AppColorScheme
import com.guiderun.app.ui.theme.AppRadius
import com.guiderun.app.ui.theme.AppSpacing
import com.guiderun.app.ui.theme.PresetThemes

/**
 * 主题配色选择页。
 *
 * 列表展示 [PresetThemes] 中所有预设主题（颜色圆点 + 名称 + 气质描述），
 * 当前选中项通过 VolunteerSettingsViewModel.uiState.currentThemeId 标记勾选状态。
 * 点击即时通过 DataStore 持久化，App 主题在下次重组时随 collectAsStateWithLifecycle 刷新。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolunteerThemeSelectionScreen(
    onBack: () -> Unit,
    viewModel: VolunteerSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "主题配色",
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = AppSpacing.MD),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.SM),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = AppSpacing.SM),
        ) {
            item {
                Text(
                    text = "选择您喜欢的配色方案",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = AppSpacing.SM),
                )
            }

            items(PresetThemes) { theme ->
                ThemeOption(
                    theme = theme,
                    isSelected = theme.id == uiState.currentThemeId,
                    onClick = { viewModel.setTheme(theme.id) },
                )
            }
        }
    }
}

@Composable
private fun ThemeOption(
    theme: AppColorScheme,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = AppRadius.LargeShape,
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.MD),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.MD),
        ) {
            // 颜色预览
            Row(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.XS),
            ) {
                ColorDot(color = theme.primary)
                ColorDot(color = theme.primaryLight)
                ColorDot(color = theme.secondary)
            }

            // 主题名称
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = theme.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(
                    text = when (theme.id) {
                        "orange" -> "活力、行动力"
                        "blue" -> "专业、可靠"
                        "teal" -> "健康、户外"
                        "gray" -> "内敛、沉稳"
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            // 选中指示
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "已选中",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun ColorDot(color: Color) {
    Surface(
        shape = CircleShape,
        color = color,
        modifier = Modifier.size(28.dp),
    ) {}
}
