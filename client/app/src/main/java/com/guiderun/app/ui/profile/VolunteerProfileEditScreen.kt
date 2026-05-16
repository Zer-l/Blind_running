package com.guiderun.app.ui.profile

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guiderun.app.domain.model.Gender
import com.guiderun.app.ui.theme.AppRadius
import com.guiderun.app.ui.theme.AppSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolunteerProfileEditScreen(
    onSaved: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    viewModel: VolunteerProfileEditViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var nickname by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf<Gender?>(null) }
    var averagePace by remember { mutableStateOf("") }
    var runningLevel by remember { mutableStateOf("") }
    var hasGuideExperience by remember { mutableStateOf(false) }

    LaunchedEffect(uiState) {
        nickname = uiState.nickname
        gender = uiState.gender
        averagePace = uiState.averagePaceSeconds
        runningLevel = uiState.runningLevel
        hasGuideExperience = uiState.hasGuideExperience
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { snackbarHostState.showSnackbar(it) }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is VolunteerProfileEditEvent.Saved -> onSaved()
                is VolunteerProfileEditEvent.Error -> { /* handled in uiState */ }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "编辑志愿者资料",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onSaved) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(AppSpacing.MD),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.MD),
        ) {
            // 基本信息卡片
            ProfileSectionCard(
                title = "基本信息",
                icon = Icons.Default.Person,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                iconTint = MaterialTheme.colorScheme.primary,
            ) {
                OutlinedTextField(
                    value = nickname,
                    onValueChange = {
                        nickname = it
                        viewModel.updateNickname(it)
                    },
                    label = { Text("昵称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = AppRadius.MediumShape,
                )

                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.SM)) {
                    Text(
                        text = "性别",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.SM)) {
                        GenderChip(
                            label = "男",
                            selected = gender == Gender.MALE,
                            onClick = {
                                gender = if (gender == Gender.MALE) null else Gender.MALE
                                viewModel.updateGender(gender)
                            },
                        )
                        GenderChip(
                            label = "女",
                            selected = gender == Gender.FEMALE,
                            onClick = {
                                gender = if (gender == Gender.FEMALE) null else Gender.FEMALE
                                viewModel.updateGender(gender)
                            },
                        )
                    }
                }
            }

            // 跑步能力卡片
            ProfileSectionCard(
                title = "跑步能力",
                icon = Icons.Default.Speed,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                iconTint = MaterialTheme.colorScheme.secondary,
            ) {
                OutlinedTextField(
                    value = averagePace,
                    onValueChange = {
                        averagePace = it
                        viewModel.updateAveragePaceSeconds(it)
                    },
                    label = { Text("平均配速（秒/公里）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = AppRadius.MediumShape,
                )

                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.SM)) {
                    Text(
                        text = "跑步等级",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.SM)) {
                        LevelChip(
                            label = "入门",
                            selected = runningLevel == "BEGINNER",
                            onClick = {
                                runningLevel = if (runningLevel == "BEGINNER") "" else "BEGINNER"
                                viewModel.updateRunningLevel(runningLevel)
                            },
                        )
                        LevelChip(
                            label = "进阶",
                            selected = runningLevel == "INTERMEDIATE",
                            onClick = {
                                runningLevel = if (runningLevel == "INTERMEDIATE") "" else "INTERMEDIATE"
                                viewModel.updateRunningLevel(runningLevel)
                            },
                        )
                        LevelChip(
                            label = "高级",
                            selected = runningLevel == "ADVANCED",
                            onClick = {
                                runningLevel = if (runningLevel == "ADVANCED") "" else "ADVANCED"
                                viewModel.updateRunningLevel(runningLevel)
                            },
                        )
                    }
                }
            }

            // 陪跑经验卡片
            ProfileSectionCard(
                title = "陪跑经验",
                icon = Icons.Default.Star,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                iconTint = MaterialTheme.colorScheme.tertiary,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "是否有陪跑经验",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = if (hasGuideExperience) "有陪跑经验" else "暂无陪跑经验",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = hasGuideExperience,
                        onCheckedChange = {
                            hasGuideExperience = it
                            viewModel.updateHasGuideExperience(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }

            // 保存按钮
            Button(
                onClick = { viewModel.save() },
                enabled = !uiState.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = AppRadius.LargeShape,
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        text = "保存",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun GenderChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = AppRadius.SmallShape,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}

@Composable
private fun LevelChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = AppRadius.SmallShape,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.secondary,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondary,
        ),
    )
}

@Composable
private fun ProfileSectionCard(
    title: String,
    icon: ImageVector,
    containerColor: androidx.compose.ui.graphics.Color,
    iconTint: androidx.compose.ui.graphics.Color,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AppRadius.LargeShape,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
        ),
    ) {
        Column(
            modifier = Modifier.padding(AppSpacing.MD),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.MD),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.SM),
            ) {
                Surface(
                    shape = AppRadius.MediumShape,
                    color = iconTint.copy(alpha = 0.1f),
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.padding(AppSpacing.XS),
                        tint = iconTint,
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            content()
        }
    }
}
