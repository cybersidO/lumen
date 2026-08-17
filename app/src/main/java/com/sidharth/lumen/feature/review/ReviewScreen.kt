package com.sidharth.lumen.feature.review

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sidharth.lumen.R
import com.sidharth.lumen.core.database.ReviewCard
import com.sidharth.lumen.core.database.ReviewSourceType

const val ReviewCardTag = "review_card"
const val ReviewRevealTag = "review_reveal"
const val ReviewKnownTag = "review_known"
const val ReviewAgainTag = "review_again"
const val ReviewDoneTag = "review_done"

/**
 * §2.6's review mode: saved words and highlights as a flashcard queue — the prompt with
 * the term blanked, revealed on tap, then marked known or review-again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (state.total > 0) {
                            stringResource(
                                R.string.review_progress,
                                state.reviewed + 1,
                                state.total,
                            )
                        } else {
                            stringResource(R.string.review_title)
                        },
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            if (state.total > 0) {
                LinearProgressIndicator(
                    progress = { state.reviewed.toFloat() / state.total },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            val card = state.current
            when {
                state.loading -> Box(Modifier.fillMaxSize())
                card == null -> AllCaughtUp(state.reviewed)
                else -> CardFace(
                    card = card,
                    revealed = state.revealed,
                    onReveal = viewModel::reveal,
                    onKnown = { viewModel.answer(ReviewResult.KNOWN) },
                    onAgain = { viewModel.answer(ReviewResult.AGAIN) },
                )
            }
        }
    }
}

@Composable
private fun CardFace(
    card: ReviewCard,
    revealed: Boolean,
    onReveal: () -> Unit,
    onKnown: () -> Unit,
    onAgain: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clickable(enabled = !revealed, onClick = onReveal)
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
            .testTag(ReviewCardTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = listOfNotNull(card.bookTitle, card.chapterTitle).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 20.dp),
        )

        // A word card prompts with its sentence, term blanked; a highlight prompts with
        // the quote itself, which is already the thing worth recalling.
        val isWord = card.sourceType == ReviewSourceType.LOOKUP
        val prompt = if (isWord && card.context != null) {
            ReviewSchedule.blankTerm(card.context, card.prompt)
        } else {
            card.prompt
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(20.dp),
        ) {
            Text(
                text = prompt,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        AnimatedVisibility(visible = revealed, enter = fadeIn(), exit = fadeOut()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 24.dp),
            ) {
                Text(
                    text = card.prompt,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
                if (!isWord && card.context != null) {
                    Text(
                        text = card.context,
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                Text(
                    text = stringResource(
                        R.string.review_interval,
                        ReviewSchedule.intervalLabel(card.intervalIndex),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }

        if (revealed) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            ) {
                OutlinedButton(onClick = onAgain, modifier = Modifier.testTag(ReviewAgainTag)) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                    Text(
                        text = stringResource(R.string.review_again),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Button(
                    onClick = onKnown,
                    modifier = Modifier.testTag(ReviewKnownTag),
                    colors = ButtonDefaults.buttonColors(),
                ) {
                    Icon(Icons.Outlined.Check, contentDescription = null)
                    Text(
                        text = stringResource(R.string.review_known),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        } else {
            OutlinedButton(
                onClick = onReveal,
                modifier = Modifier.padding(top = 32.dp).testTag(ReviewRevealTag),
            ) {
                Text(stringResource(R.string.review_reveal))
            }
        }
    }
}

@Composable
private fun AllCaughtUp(reviewed: Int) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp).testTag(ReviewDoneTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.review_caught_up),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        if (reviewed > 0) {
            Text(
                text = pluralStringResource(R.plurals.review_session_summary, reviewed, reviewed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
