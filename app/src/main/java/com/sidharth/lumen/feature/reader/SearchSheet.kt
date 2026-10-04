package com.sidharth.lumen.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.sidharth.lumen.R

const val SearchFieldTag = "reader_search_field"
const val SearchResultsTag = "reader_search_results"
fun searchResultTag(index: Int) = "reader_search_result_$index"

/**
 * Search within the open book (M13).
 *
 * Results arrive a page at a time from Readium's iterator, so the first few appear
 * immediately on a long book and the rest load as the list is scrolled — rather than the
 * reader waiting for the whole publication to be scanned.
 */
@Composable
fun SearchSheet(
    state: BookSearchState,
    onQueryChange: (String) -> Unit,
    onLoadMore: () -> Unit,
    onSelect: (SearchHit) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf((state as? BookSearchState.Results)?.query.orEmpty()) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { focus.requestFocus() }

    // Pull the next page as the end of the list comes into view.
    LaunchedEffect(listState, state) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { last ->
                val results = state as? BookSearchState.Results ?: return@collect
                if (results.hasMore && !results.loadingMore &&
                    last != null && last >= results.hits.lastIndex - 3
                ) {
                    onLoadMore()
                }
            }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 16.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            label = { Text(stringResource(R.string.search_in_book)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = ""; onQueryChange("") }) {
                        Icon(Icons.Outlined.Close, stringResource(R.string.search_clear))
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    keyboard?.hide()
                    onQueryChange(query)
                },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus)
                .testTag(SearchFieldTag),
        )

        Spacer(Modifier.height(12.dp))

        when (state) {
            BookSearchState.Idle -> Hint(stringResource(R.string.search_hint))

            BookSearchState.Searching -> Box(
                Modifier.fillMaxWidth().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            is BookSearchState.Failed -> Hint(state.message)

            is BookSearchState.Results -> {
                if (state.isEmpty) {
                    Hint(stringResource(R.string.search_no_results, state.query))
                } else {
                    Text(
                        text = pluralStringResource(
                            R.plurals.search_result_count, state.hits.size, state.hits.size,
                        ) + if (state.hasMore) "+" else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).testTag(SearchResultsTag),
                    ) {
                        items(state.hits.size, key = { it }) { index ->
                            HitRow(
                                hit = state.hits[index],
                                modifier = Modifier.testTag(searchResultTag(index)),
                                onClick = { onSelect(state.hits[index]) },
                            )
                        }
                        if (state.loadingMore) {
                            item {
                                Box(
                                    Modifier.fillMaxWidth().padding(12.dp),
                                    contentAlignment = Alignment.Center,
                                ) { CircularProgressIndicator(Modifier.height(20.dp)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HitRow(hit: SearchHit, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        hit.chapter?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
        }
        Text(
            text = hit.annotated(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
        )
    }
}

/** The match in bold inside its own sentence, so a hit is recognisable at a glance. */
@Composable
private fun SearchHit.annotated(): AnnotatedString = buildAnnotatedString {
    append(before.takeLast(60).trimStart())
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(highlight) }
    append(after.take(80).trimEnd())
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 16.dp),
    )
}
