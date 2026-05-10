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
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guiderun.app.domain.model.Gender

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolunteerProfileEditScreen(
    onSaved: () -> Unit,
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
                title = { Text("编辑志愿者资料") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 基本信息卡片
            ProfileSectionCard(
                title = "基本信息",
                icon = Icons.Default.Person,
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
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "性别",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = gender == Gender.MALE,
                            onClick = {
                                gender = if (gender == Gender.MALE) null else Gender.MALE
                                viewModel.updateGender(gender)
                            },
                            label = { Text("男") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                        FilterChip(
                            selected = gender == Gender.FEMALE,
                            onClick = {
                                gender = if (gender == Gender.FEMALE) null else Gender.FEMALE
                                viewModel.updateGender(gender)
                            },
                            label = { Text("女") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }
                }
            }

            // 跑步能力卡片
            ProfileSectionCard(
                title = "跑步能力",
                icon = Icons.Default.Speed,
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
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "跑步等级",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = runningLevel == "BEGINNER",
                            onClick = {
                                runningLevel = if (runningLevel == "BEGINNER") "" else "BEGINNER"
                                viewModel.updateRunningLevel(runningLevel)
                            },
                            label = { Text("入门") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                        FilterChip(
                            selected = runningLevel == "INTERMEDIATE",
                            onClick = {
                                runningLevel = if (runningLevel == "INTERMEDIATE") "" else "INTERMEDIATE"
                                viewModel.updateRunningLevel(runningLevel)
                            },
                            label = { Text("进阶") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                        FilterChip(
                            selected = runningLevel == "ADVANCED",
                            onClick = {
                                runningLevel = if (runningLevel == "ADVANCED") "" else "ADVANCED"
                                viewModel.updateRunningLevel(runningLevel)
                            },
                            label = { Text("高级") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }
                }
            }

            // 陪跑经验卡片
            ProfileSectionCard(
                title = "陪跑经验",
                icon = Icons.Default.Star,
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
                shape = MaterialTheme.shapes.medium,
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("保存", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun ProfileSectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            content()
        }
    }
}
