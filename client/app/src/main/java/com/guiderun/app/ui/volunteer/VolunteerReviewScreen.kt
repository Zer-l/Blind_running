package com.guiderun.app.ui.volunteer

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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guiderun.app.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VolunteerReviewScreen(
    requestId: String,
    onNavigateToHome: () -> Unit,
    viewModel: VolunteerReviewViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

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
                    TextButton(onClick = viewModel::skip) {
                        Text(stringResource(R.string.volunteer_review_skip))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            RatingRow(rating = uiState.rating, onRatingChange = viewModel::setRating)

            Column {
                Text(stringResource(R.string.volunteer_review_tags_label), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val tags = listOf(
                        R.string.volunteer_review_tag_friendly,
                        R.string.volunteer_review_tag_punctual,
                        R.string.volunteer_review_tag_patient,
                        R.string.volunteer_review_tag_professional,
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

            OutlinedTextField(
                value = uiState.comment,
                onValueChange = viewModel::setComment,
                label = { Text(stringResource(R.string.volunteer_review_comment_hint)) },
                modifier = Modifier.fillMaxWidth().height(120.dp),
                maxLines = 4,
            )

            Spacer(Modifier.weight(1f))

            Button(
                onClick = viewModel::submit,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = !uiState.isSubmitting,
            ) {
                if (uiState.isSubmitting) CircularProgressIndicator()
                else Text(stringResource(R.string.volunteer_review_submit))
            }
        }
    }
}

@Composable
private fun RatingRow(rating: Int, onRatingChange: (Int) -> Unit) {
    Column {
        Text(stringResource(R.string.volunteer_review_rating_label), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..5).forEach { star ->
                FilterChip(
                    selected = star <= rating,
                    onClick = { onRatingChange(star) },
                    label = { Text("$star") },
                )
            }
        }
    }
}
