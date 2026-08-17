package com.sidharth.lumen.feature.notes

import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sidharth.lumen.R
import com.sidharth.lumen.core.database.NoteListRow
import com.sidharth.lumen.feature.highlight.HighlightColor

const val NotesSearchFieldTag = "notes_search_field"
const val NotesListTag = "notes_list"
const val NotesReviewButtonTag = "notes_review_button"
fun notesEntryTag(kind: String, id: Long) = "notes_entry_${kind}_$id"

/**
 * §2.6's cross-book list: highlights, notes and looked-up words in one place, searchable
 * and filterable, with every entry a way back into the book it came from.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    onBack: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenBook: (bookId: Long, highlightId: Long?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotesViewModel = hiltViewModel(),
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val books by viewModel.books.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val jump by viewModel.jump.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val dueCount by viewModel.dueCount.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showFilters by remember { mutableStateOf(false) }
    var exportMenuOpen by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf<android.net.Uri?>(null) }

    LaunchedEffect(jump) {
        jump?.let {
            onOpenBook(it.bookId, it.highlightId)
            viewModel.consumeJump()
        }
    }

    val messageText = message?.let { resolveNotesMessage(it) }
    LaunchedEffect(message?.id) {
        val current = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(messageText.orEmpty())
        viewModel.consumeMessage(current.id)
    }

    // Markdown writes many files, so it asks for a directory; CSV/JSON and the backup are
    // single documents.
    val markdownDir = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        viewModel.exportMarkdown(uri) { dir, name ->
            createInTree(context, dir, name, "text/markdown")
        }
    }
    val csvTarget = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        viewModel.exportSingleFile(ExportFormat.CSV) {
            context.contentResolver.openOutputStream(uri)
        }
    }
    val jsonTarget = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        viewModel.exportSingleFile(ExportFormat.JSON) {
            context.contentResolver.openOutputStream(uri)
        }
    }
    val backupTarget = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        viewModel.backup { context.contentResolver.openOutputStream(uri) }
    }
    val restoreSource = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> confirmRestore = uri }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notes_title)) },
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
                actions = {
                    // The due count is the whole point of the badge: it is the reason to
                    // open review at all, so it sits on the screen the user is already on.
                    if (dueCount > 0) {
                        TextButton(
                            onClick = onOpenReview,
                            modifier = Modifier.testTag(NotesReviewButtonTag),
                        ) {
                            Text(pluralStringResource(R.plurals.review_due_badge, dueCount, dueCount))
                        }
                    } else {
                        IconButton(
                            onClick = onOpenReview,
                            modifier = Modifier.testTag(NotesReviewButtonTag),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.School,
                                contentDescription = stringResource(R.string.review_open),
                            )
                        }
                    }
                    IconButton(onClick = { showFilters = !showFilters }) {
                        Icon(
                            imageVector = Icons.Outlined.FilterList,
                            contentDescription = stringResource(R.string.notes_filters),
                            tint = if (filter.isActive) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    Box {
                        IconButton(onClick = { exportMenuOpen = true }) {
                            Icon(
                                imageVector = Icons.Outlined.IosShare,
                                contentDescription = stringResource(R.string.notes_export),
                            )
                        }
                        DropdownMenu(
                            expanded = exportMenuOpen,
                            onDismissRequest = { exportMenuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.notes_export_markdown)) },
                                onClick = { exportMenuOpen = false; markdownDir.launch(null) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.notes_export_csv)) },
                                onClick = {
                                    exportMenuOpen = false
                                    csvTarget.launch("lumen-highlights.csv")
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.notes_export_json)) },
                                onClick = {
                                    exportMenuOpen = false
                                    jsonTarget.launch("lumen-highlights.json")
                                },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(Icons.Outlined.Backup, contentDescription = null)
                                },
                                text = { Text(stringResource(R.string.notes_backup)) },
                                onClick = {
                                    exportMenuOpen = false
                                    backupTarget.launch("lumen-backup.zip")
                                },
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.SettingsBackupRestore,
                                        contentDescription = null,
                                    )
                                },
                                text = { Text(stringResource(R.string.notes_restore)) },
                                onClick = {
                                    exportMenuOpen = false
                                    restoreSource.launch(arrayOf("application/zip", "*/*"))
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())

            OutlinedTextField(
                value = filter.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag(NotesSearchFieldTag),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.notes_search_hint)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (filter.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.library_search_clear),
                            )
                        }
                    }
                },
            )

            if (showFilters) {
                FilterBar(
                    filter = filter,
                    books = books.map { it.contentHash to it.title },
                    tags = tags.map { it.id to it.name },
                    onKind = viewModel::toggleKind,
                    onBook = viewModel::setBook,
                    onColor = viewModel::setColor,
                    onTag = viewModel::setTag,
                    onClear = viewModel::clearFilters,
                )
            }

            if (entries.isEmpty()) {
                EmptyState(filter.isActive)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag(NotesListTag),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(entries, key = { "${it.kind}-${it.entryId}" }) { row ->
                        EntryRow(row, onClick = { viewModel.requestJump(row) })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }

    confirmRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text(stringResource(R.string.notes_restore_title)) },
            // Says plainly that this replaces rather than merges, before it happens.
            text = { Text(stringResource(R.string.notes_restore_warning)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRestore = null
                        viewModel.restore { context.contentResolver.openInputStream(uri) }
                    },
                ) { Text(stringResource(R.string.notes_restore_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = null }) {
                    Text(stringResource(R.string.delete_cancel))
                }
            },
        )
    }
}

@Composable
private fun EntryRow(row: NoteListRow, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(notesEntryTag(row.kind, row.entryId))
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        // Colour dot for highlights; kind letter for the rest, so the list scans quickly.
        Box(
            modifier = Modifier.padding(end = 14.dp, top = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (row.kind) {
                "HIGHLIGHT" -> Box(
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(HighlightColor.fromId(row.colorId ?: 0).swatch),
                )

                else -> Text(
                    text = if (row.kind == "LOOKUP") "W" else "N",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Column(Modifier.fillMaxWidth()) {
            Text(
                text = row.primaryText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            row.secondaryText?.takeIf { it.isNotBlank() }?.let { secondary ->
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Text(
                text = listOfNotNull(row.bookTitle, row.chapterTitle).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun FilterBar(
    filter: NotesFilter,
    books: List<Pair<String, String>>,
    tags: List<Pair<Long, String>>,
    onKind: (EntryKind) -> Unit,
    onBook: (String?) -> Unit,
    onColor: (HighlightColor?) -> Unit,
    onTag: (Long?) -> Unit,
    onClear: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EntryKind.entries.forEach { kind ->
                FilterChip(
                    selected = kind in filter.kinds,
                    onClick = { onKind(kind) },
                    label = { Text(kind.name.lowercase().replaceFirstChar(Char::uppercase)) },
                )
            }
        }
        Row(
            Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HighlightColor.entries.forEach { color ->
                FilterChip(
                    selected = filter.color == color,
                    onClick = { onColor(if (filter.color == color) null else color) },
                    label = { Text(color.displayName) },
                )
            }
        }
        if (books.isNotEmpty()) {
            Row(
                Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                books.forEach { (hash, title) ->
                    FilterChip(
                        selected = filter.bookHash == hash,
                        onClick = { onBook(if (filter.bookHash == hash) null else hash) },
                        label = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
        }
        if (tags.isNotEmpty()) {
            Row(
                Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tags.forEach { (id, name) ->
                    FilterChip(
                        selected = filter.tagId == id,
                        onClick = { onTag(if (filter.tagId == id) null else id) },
                        label = { Text("#$name") },
                    )
                }
            }
        }
        if (filter.isActive) {
            TextButton(onClick = onClear, modifier = Modifier.padding(top = 4.dp)) {
                Text(stringResource(R.string.notes_clear_filters))
            }
        }
    }
}

@Composable
private fun EmptyState(filtered: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(
                if (filtered) R.string.notes_empty_filtered else R.string.notes_empty,
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun resolveNotesMessage(message: NotesMessage): String = when (message) {
    is NotesMessage.Exported -> when (message.format) {
        ExportFormat.MARKDOWN -> pluralStringResource(
            R.plurals.notes_msg_exported_markdown,
            message.fileCount,
            message.fileCount,
        )

        else -> stringResource(R.string.notes_msg_exported_file)
    }

    is NotesMessage.BackedUp ->
        stringResource(R.string.notes_msg_backed_up, message.bytes / 1024)

    is NotesMessage.Restored -> pluralStringResource(
        R.plurals.notes_msg_restored,
        message.highlights,
        message.highlights,
    )

    is NotesMessage.Failed -> message.reason
}

/**
 * Creates a file inside a user-picked directory tree, returning a stream to write it.
 *
 * Uses the platform `DocumentsContract` rather than `androidx.documentfile` — the latter
 * would be a new dependency for what the framework already exposes.
 *
 * An existing file of the same name is deleted first. Without that, SAF auto-renames on
 * collision and re-exporting into an Obsidian vault would accumulate "Book (1).md",
 * "Book (2).md" instead of updating the note.
 */
private fun createInTree(
    context: android.content.Context,
    tree: android.net.Uri,
    name: String,
    mimeType: String,
): java.io.OutputStream? = runCatching {
    val resolver = context.contentResolver
    val treeDocId = DocumentsContract.getTreeDocumentId(tree)
    val parent = DocumentsContract.buildDocumentUriUsingTree(tree, treeDocId)
    val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeDocId)

    resolver.query(
        children,
        arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        ),
        null,
        null,
        null,
    )?.use { cursor ->
        while (cursor.moveToNext()) {
            if (cursor.getString(1) == name) {
                val existing = DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0))
                runCatching { DocumentsContract.deleteDocument(resolver, existing) }
                break
            }
        }
    }

    val created = DocumentsContract.createDocument(resolver, parent, mimeType, name)
        ?: return null
    resolver.openOutputStream(created)
}.getOrNull()
