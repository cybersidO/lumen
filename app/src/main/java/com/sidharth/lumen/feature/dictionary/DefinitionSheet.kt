package com.sidharth.lumen.feature.dictionary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sidharth.lumen.R
import com.sidharth.lumen.core.dictionary.DictionaryEntry

const val DefinitionSheetTag = "definition_sheet"
const val DefinitionWebSearchTag = "definition_web_search"

/** What the sheet is currently showing. */
sealed interface LookupUiState {
    data class Loading(val word: String) : LookupUiState
    data class Loaded(val result: LookupResult) : LookupUiState
}

/**
 * §2.4's lookup sheet: headword, pronunciation, part of speech, senses.
 *
 * It is a bottom sheet over the reader precisely so the reading position is untouched —
 * the navigator underneath is never rebuilt, so dismissing returns to the same page and
 * the same scroll offset.
 */
@Composable
fun DefinitionSheet(
    state: LookupUiState,
    onSearchWeb: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
            .testTag(DefinitionSheetTag),
    ) {
        when (state) {
            is LookupUiState.Loading -> {
                Text(
                    text = state.word,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                CircularProgressIndicator(
                    modifier = Modifier.padding(top = 16.dp).align(Alignment.CenterHorizontally),
                )
            }

            is LookupUiState.Loaded ->
                if (state.result.found) {
                    Found(state.result, onSearchWeb)
                } else {
                    NotFound(state.result.query, onSearchWeb)
                }
        }
    }
}

@Composable
private fun Found(result: LookupResult, onSearchWeb: (String) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        itemsIndexed(result.entries) { index, entry ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
            EntryBlock(entry)
        }

        item {
            Text(
                text = result.attribution,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 20.dp),
            )
            WebSearchButton(result.query, onSearchWeb, primary = false)
        }
    }
}

@Composable
private fun EntryBlock(entry: DictionaryEntry) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = entry.headword,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        entry.pronunciation?.let { ipa ->
            Text(
                text = "/$ipa/",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 10.dp, bottom = 2.dp),
            )
        }
    }

    // Says plainly that the tapped word was inflected and this is its base form, rather
    // than silently showing a different headword than the one the reader tapped.
    entry.resolvedFrom?.let { original ->
        Text(
            text = stringResource(R.string.dictionary_resolved_from, original),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 2.dp),
        )
    }

    // Senses grouped by part of speech, which is how a dictionary reads.
    entry.senses.groupBy { it.pos }.forEach { (pos, senses) ->
        Text(
            text = pos.displayName,
            style = MaterialTheme.typography.labelLarge,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
        )
        senses.forEachIndexed { index, sense ->
            Row(modifier = Modifier.padding(bottom = 6.dp)) {
                Text(
                    text = "${index + 1}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Column {
                    Text(
                        text = sense.definition,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    sense.examples.take(2).forEach { example ->
                        Text(
                            text = "“$example”",
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotFound(word: String, onSearchWeb: (String) -> Unit) {
    Text(
        text = word,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
        text = stringResource(R.string.dictionary_not_found),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
    WebSearchButton(word, onSearchWeb, primary = true)
}

/**
 * §2.4's escape hatch, "clearly marked as leaving the app" — hence the external-link icon
 * and the explicit wording. It is an ACTION_VIEW intent handed to the browser, so it needs
 * no INTERNET permission of Lumen's own and nothing about the reader goes anywhere.
 */
@Composable
private fun WebSearchButton(word: String, onSearchWeb: (String) -> Unit, primary: Boolean) {
    OutlinedButton(
        onClick = { onSearchWeb(word) },
        modifier = Modifier
            .padding(top = if (primary) 20.dp else 12.dp)
            .testTag(DefinitionWebSearchTag),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
            contentDescription = null,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(stringResource(R.string.dictionary_search_web))
    }
}
