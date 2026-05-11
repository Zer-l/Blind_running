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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guiderun.app.R
import com.guiderun.app.ui.common.CallPeerButton
import com.guiderun.app.ui.common.InterruptDialog

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VolunteerReviewScreen(
    requestId: String,
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
                title = { Text(stringResource(R.string.volunteer_review_title)) },
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
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.volunteer_review_rating_label),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    StarRatingRow(
                        rating = uiState.rating,
                        onRatingChange = viewModel::setRating,
                    )
                    Text(
                        text = ratingText(uiState.rating),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.volunteer_review_tags_label),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val tags = listOf(
                            R.string.volunteer_review_tag_punctual,
                            R.string.volunteer_review_tag_cooperative,
                            R.string.volunteer_review_tag_clear_communication,
                            R.string.volunteer_review_tag_friendly,
                        )
                        tags.forEach { resId ->
                            val tag = stringResource(resId)
                            FilterChip(
                                selected = tag in uiState.selectedTags,
                                onClick = { viewModel.toggleTag(tag) },
                                label = { Text(tag) },
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = uiState.comment,
                onValueChange = viewModel::setComment,
                label = { Text(stringResource(R.string.volunteer_review_comment_hint)) },
                modifier = Modifier.fillMaxWidth().height(120.dp).padding(horizontal = 16.dp),
                maxLines = 4,
            )

            Spacer(Modifier.weight(1f))

            Button(
                onClick = viewModel::submit,
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
                enabled = !uiState.isSubmitting && uiState.rating > 0,
            ) {
                if (uiState.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.volunteer_review_submit),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StarRatingRow(
    rating: Int,
    onRatingChange: (Int) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (1..5).forEach { star ->
            IconButton(
                onClick = { onRatingChange(star) },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = if (star <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "$star 星",
                    modifier = Modifier.size(36.dp),
                    tint = if (star <= rating) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
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
