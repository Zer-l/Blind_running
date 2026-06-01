package com.guiderun.app.ui.volunteer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guiderun.app.R
import com.guiderun.app.ui.theme.AppRadius
import com.guiderun.app.ui.theme.AppSpacing

/**
 * 志愿者评价页。
 *
 * 星级（1-5）+ 标签多选（FlowRow）+ 文字评论三个区块。
 * 按返回键弹 InterruptDialog 提示可跳过；右上角也有跳过按钮。
 * 提交前校验：rating > 0 才启用按钮；提交中 loading 防重复点击。
 * requestId 由 ViewModel 通过 SavedStateHandle 读取，Screen 参数仅用于编译期强制传值。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VolunteerReviewScreen(
    // requestId 由 ViewModel 通过 SavedStateHandle 读取；保留参数让 AppNavGraph 调用方在编译期就必须传值
    @Suppress("UNUSED_PARAMETER") requestId: String,
    onNavigateToHome: () -> Unit,
    viewModel: VolunteerReviewViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showInterruptDialog by remember { mutableStateOf(false) }

    BackHandler { showInterruptDialog = true }

    if (showInterruptDialog) {
        InterruptDialog(
            title = stringResource(R.string.interrupt_title_leave_review),
            message = stringResource(R.string.interrupt_message_leave_review),
            onDismissRequest = { showInterruptDialog = false },
            cancelLabel = stringResource(R.string.interrupt_btn_leave),
            onCancel = {
                showInterruptDialog = false
                viewModel.skip()
            },
            stayLabel = stringResource(R.string.interrupt_btn_continue_review),
            onStay = { showInterruptDialog = false },
        )
    }

    LaunchedEffect(Unit) {
        viewModel.navEvent.collect { event ->
            when (event) {
                VolunteerReviewNavEvent.ToHome -> onNavigateToHome()
            }
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onErrorShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.volunteer_review_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                actions = {
                    CallPeerButton(phone = uiState.peerPhone)
                    TextButton(onClick = viewModel::skip) {
                        Text(stringResource(R.string.volunteer_review_skip))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.MD),
        ) {
            // 评分卡片
            RatingCard(
                rating = uiState.rating,
                onRatingChange = viewModel::setRating,
            )

            // 标签卡片
            TagsCard(
                selectedTags = uiState.selectedTags,
                onToggleTag = viewModel::toggleTag,
            )

            // 评论输入
            OutlinedTextField(
                value = uiState.comment,
                onValueChange = viewModel::setComment,
                label = { Text(stringResource(R.string.volunteer_review_comment_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .padding(horizontal = AppSpacing.MD),
                maxLines = 4,
                shape = AppRadius.MediumShape,
            )

            Spacer(Modifier.weight(1f))

            // 提交按钮
            Button(
                onClick = viewModel::submit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = AppSpacing.MD),
                shape = AppRadius.LargeShape,
                enabled = !uiState.isSubmitting && uiState.rating > 0,
            ) {
                if (uiState.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.volunteer_review_submit),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.height(AppSpacing.MD))
        }
    }
}

@Composable
private fun RatingCard(
    rating: Int,
    onRatingChange: (Int) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.MD),
        shape = AppRadius.LargeShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.XL),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppSpacing.MD),
        ) {
            Text(
                text = stringResource(R.string.volunteer_review_rating_label),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            StarRatingRow(
                rating = rating,
                onRatingChange = onRatingChange,
            )
            Text(
                text = ratingText(rating),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun StarRatingRow(
    rating: Int,
    onRatingChange: (Int) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.XS)) {
        (1..5).forEach { star ->
            val isSelected = star <= rating
            IconButton(
                onClick = { onRatingChange(star) },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = if (isSelected) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "$star 星",
                    modifier = Modifier.size(36.dp),
                    tint = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsCard(
    selectedTags: Set<String>,
    onToggleTag: (String) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.MD),
        shape = AppRadius.LargeShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(AppSpacing.MD)) {
            Text(
                text = stringResource(R.string.volunteer_review_tags_label),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(AppSpacing.MD))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.SM),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.SM),
            ) {
                val tags = listOf(
                    R.string.volunteer_review_tag_punctual,
                    R.string.volunteer_review_tag_cooperative,
                    R.string.volunteer_review_tag_clear_communication,
                    R.string.volunteer_review_tag_friendly,
                    R.string.volunteer_review_tag_pace_stable,
                    R.string.volunteer_review_tag_good_stamina,
                    R.string.volunteer_review_tag_listens_well,
                    R.string.volunteer_review_tag_positive,
                    R.string.volunteer_review_tag_persistent,
                    R.string.volunteer_review_tag_trusting,
                    R.string.volunteer_review_tag_feedback_timely,
                    R.string.volunteer_review_tag_safe_aware,
                )
                tags.forEach { resId ->
                    val tag = stringResource(resId)
                    FilterChip(
                        selected = tag in selectedTags,
                        onClick = { onToggleTag(tag) },
                        label = { Text(tag) },
                        shape = AppRadius.SmallShape,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }
        }
    }
}

private fun ratingText(rating: Int): String = when (rating) {
    1 -> "非常不满意"
    2 -> "不满意"
    3 -> "一般"
    4 -> "满意"
    5 -> "非常满意"
    else -> "请点击星星评分"
}
