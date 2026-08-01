package com.sidharth.lumen.feature.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sidharth.lumen.R
import com.sidharth.lumen.core.books.ImportProgress

const val LibraryImportButtonTag = "library_import_button"
const val LibrarySettingsButtonTag = "library_settings_button"
const val LibrarySearchFieldTag = "library_search_field"
const val LibrarySortButtonTag = "library_sort_button"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenSettings: () -> Unit,
    onOpenBook: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val message by viewModel.messages.collectAsStateWithLifecycle()
    val pendingDeletion by viewModel.pendingDeletion.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var sortMenuOpen by remember { mutableStateOf(false) }

    // Any file type is offered, then validated by actually parsing it. Filtering the
    // picker on "application/epub+zip" hides EPUBs from the many providers that report
    // them as octet-stream, which looks like the file is missing.
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::import) }

    val messageText = message?.let { resolveMessage(it) }
    LaunchedEffect(message?.id) {
        val current = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(messageText.orEmpty())
        viewModel.consumeMessage(current.id)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.library_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
                actions = {
                    Box {
                        IconButton(
                            onClick = { sortMenuOpen = true },
                            modifier = Modifier.testTag(LibrarySortButtonTag),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.SwapVert,
                                contentDescription = stringResource(R.string.library_sort),
                            )
                        }
                        SortMenu(
                            expanded = sortMenuOpen,
                            current = state.sort,
                            onDismiss = { sortMenuOpen = false },
                            onSelect = {
                                viewModel.setSort(it)
                                sortMenuOpen = false
                            },
                        )
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag(LibrarySettingsButtonTag),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.settings_open),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { picker.launch(arrayOf("*/*")) },
                modifier = Modifier.testTag(LibraryImportButtonTag),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.library_import),
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            ImportBanner(importState)

            SearchField(
                query = state.query,
                onQueryChange = viewModel::setQuery,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            when {
                state.isEmptyLibrary -> EmptyLibrary()
                state.hasNoMatches -> NoMatches(state.query)
                else -> BookGrid(
                    books = state.books,
                    onOpen = { book -> onOpenBook(book.id) },
                    onLongPress = viewModel::requestDelete,
                )
            }
        }
    }

    pendingDeletion?.let { pending ->
        DeleteBookDialog(
            pending = pending,
            onDismiss = viewModel::cancelDelete,
            onConfirm = viewModel::confirmDelete,
        )
    }
}

@Composable
private fun resolveMessage(message: LibraryMessage): String = when (message) {
    is LibraryMessage.Imported ->
        stringResource(R.string.library_msg_imported, message.title)

    is LibraryMessage.AlreadyPresent ->
        stringResource(R.string.library_msg_already_present, message.title)

    is LibraryMessage.ImportFailed -> message.reason

    is LibraryMessage.Deleted -> if (message.keptHighlights > 0) {
        pluralStringResource(
            R.plurals.library_msg_deleted_kept,
            message.keptHighlights,
            message.title,
            message.keptHighlights,
        )
    } else {
        stringResource(R.string.library_msg_deleted, message.title)
    }

}

@Composable
private fun BookGrid(
    books: List<Book>,
    onOpen: (Book) -> Unit,
    onLongPress: (Book) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 110.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(books, key = { it.id }) { book ->
            BookCard(
                book = book,
                onClick = { onOpen(book) },
                onLongClick = { onLongPress(book) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .testTag(LibrarySearchFieldTag),
        singleLine = true,
        placeholder = { Text(stringResource(R.string.library_search)) },
        leadingIcon = {
            Icon(imageVector = Icons.Outlined.Search, contentDescription = null)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.library_search_clear),
                    )
                }
            }
        },
    )
}

@Composable
private fun SortMenu(
    expanded: Boolean,
    current: LibrarySort,
    onDismiss: () -> Unit,
    onSelect: (LibrarySort) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        LibrarySort.entries.forEach { option ->
            DropdownMenuItem(
                text = { Text(stringResource(option.labelRes())) },
                leadingIcon = {
                    RadioButton(selected = option == current, onClick = null)
                },
                onClick = { onSelect(option) },
            )
        }
    }
}

private fun LibrarySort.labelRes(): Int = when (this) {
    LibrarySort.RECENT -> R.string.library_sort_recent
    LibrarySort.TITLE -> R.string.library_sort_title
    LibrarySort.AUTHOR -> R.string.library_sort_author
}

@Composable
private fun ImportBanner(state: ImportUiState) {
    if (state !is ImportUiState.Running) return

    val label = when (val progress = state.progress) {
        is ImportProgress.Copying -> progress.totalBytes?.let { total ->
            stringResource(
                R.string.library_importing_copying,
                "${progress.copiedBytes / 1_048_576}/${total / 1_048_576} MB",
            )
        } ?: stringResource(R.string.library_importing)

        ImportProgress.Parsing -> stringResource(R.string.library_importing_parsing)
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        val progress = state.progress
        if (progress is ImportProgress.Copying && progress.totalBytes != null && progress.totalBytes > 0) {
            LinearProgressIndicator(
                progress = { progress.copiedBytes.toFloat() / progress.totalBytes },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun EmptyLibrary() {
    CenteredMessage(
        title = stringResource(R.string.library_empty_title),
        body = stringResource(R.string.library_empty_body),
    )
}

@Composable
private fun NoMatches(query: String) {
    CenteredMessage(
        title = stringResource(R.string.library_no_matches, query),
        body = null,
    )
}

@Composable
private fun CenteredMessage(title: String, body: String?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.LibraryBooks,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        if (body != null) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
