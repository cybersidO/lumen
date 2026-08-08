package com.sidharth.lumen.feature.notes

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sidharth.lumen.core.database.BookDao
import com.sidharth.lumen.core.database.HighlightDao
import com.sidharth.lumen.core.database.NoteListRow
import com.sidharth.lumen.core.database.TagEntity
import com.sidharth.lumen.core.di.IoDispatcher
import com.sidharth.lumen.feature.highlight.HighlightColor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Which export the user picked. */
enum class ExportFormat { MARKDOWN, CSV, JSON }

sealed interface NotesMessage {
    val id: Long

    data class Exported(override val id: Long, val fileCount: Int, val format: ExportFormat) :
        NotesMessage

    data class BackedUp(override val id: Long, val bytes: Long) : NotesMessage
    data class Restored(override val id: Long, val highlights: Int) : NotesMessage
    data class Failed(override val id: Long, val reason: String) : NotesMessage
}

/** Where a tapped entry should send the reader. */
data class JumpTarget(val bookId: Long, val highlightId: Long?)

@HiltViewModel
class NotesViewModel @Inject constructor(
    private val repository: NotesRepository,
    private val backupManager: BackupManager,
    private val bookDao: BookDao,
    private val highlightDao: HighlightDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _filter = MutableStateFlow(NotesFilter())
    val filter: StateFlow<NotesFilter> = _filter

    @OptIn(ExperimentalCoroutinesApi::class)
    val entries: StateFlow<List<NoteListRow>> = _filter
        .flatMapLatest { repository.observeEntries(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val books = repository.observeBookFacets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tags: StateFlow<List<TagEntity>> = repository.observeTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<NotesMessage?>(null)
    val message: StateFlow<NotesMessage?> = _message

    private val _jump = MutableStateFlow<JumpTarget?>(null)
    val jump: StateFlow<JumpTarget?> = _jump

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    // --- filtering ---------------------------------------------------------------------

    fun setQuery(value: String) { _filter.value = _filter.value.copy(query = value) }

    fun setBook(hash: String?) { _filter.value = _filter.value.copy(bookHash = hash) }

    fun setColor(color: HighlightColor?) { _filter.value = _filter.value.copy(color = color) }

    fun setTag(tagId: Long?) { _filter.value = _filter.value.copy(tagId = tagId) }

    fun toggleKind(kind: EntryKind) {
        val current = _filter.value.kinds
        // Never allow an empty set — that would show nothing with no way back except
        // guessing which chip to press.
        val updated = if (kind in current) {
            (current - kind).ifEmpty { EntryKind.entries.toSet() }
        } else {
            current + kind
        }
        _filter.value = _filter.value.copy(kinds = updated)
    }

    fun setDateRange(from: Long, to: Long) {
        _filter.value = _filter.value.copy(from = from, to = to)
    }

    fun clearFilters() { _filter.value = NotesFilter() }

    // --- jump to the book ---------------------------------------------------------------

    /** §2.6: "Tapping any entry jumps to that exact spot in the book." */
    fun requestJump(row: NoteListRow) {
        val hash = row.bookContentHash ?: run {
            postMessage { NotesMessage.Failed(it, "That entry isn't linked to a book any more.") }
            return
        }
        viewModelScope.launch {
            val book = withContext(ioDispatcher) { bookDao.findByHash(hash) }
            if (book == null) {
                // An orphaned highlight — the book was deleted but the highlight kept.
                postMessage {
                    NotesMessage.Failed(it, "That book isn't in your library any more.")
                }
                return@launch
            }
            _jump.value = JumpTarget(
                bookId = book.id,
                highlightId = row.entryId.takeIf { row.kind == "HIGHLIGHT" },
            )
        }
    }

    fun consumeJump() { _jump.value = null }

    // --- export and backup ---------------------------------------------------------------

    /**
     * Markdown writes one file per book into the chosen directory (§2.6); CSV and JSON are
     * single documents, so the caller supplies a single target Uri instead.
     */
    fun exportMarkdown(directory: Uri, openDir: (Uri, String) -> java.io.OutputStream?) {
        runExport(ExportFormat.MARKDOWN) {
            val (books, _) = repository.exportModel()
            var written = 0
            books.filter { it.highlights.isNotEmpty() }.forEach { book ->
                openDir(directory, Exporters.markdownFileName(book))?.use { out ->
                    out.write(Exporters.toMarkdown(book).toByteArray())
                    written++
                }
            }
            written
        }
    }

    fun exportSingleFile(format: ExportFormat, open: () -> java.io.OutputStream?) {
        runExport(format) {
            val (books, lookups) = repository.exportModel()
            val text = when (format) {
                ExportFormat.CSV -> Exporters.toCsv(books)
                ExportFormat.JSON -> Exporters.toJson(books, lookups)
                ExportFormat.MARKDOWN -> error("Markdown exports per book")
            }
            open()?.use { it.write(text.toByteArray()) }
            1
        }
    }

    private fun runExport(format: ExportFormat, block: suspend () -> Int) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val result = runCatching { block() }
            _busy.value = false
            result
                .onSuccess { count -> postMessage { NotesMessage.Exported(it, count, format) } }
                .onFailure { error ->
                    postMessage { NotesMessage.Failed(it, error.message ?: "Export failed.") }
                }
        }
    }

    fun backup(open: () -> java.io.OutputStream?) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val stream = open()
            val result = if (stream == null) {
                BackupResult.Failed("Couldn't open that location for writing.")
            } else {
                stream.use { backupManager.writeBackup(it) }
            }
            _busy.value = false
            when (result) {
                is BackupResult.Success -> postMessage { NotesMessage.BackedUp(it, result.bytes) }
                is BackupResult.Failed -> postMessage { NotesMessage.Failed(it, result.message) }
            }
        }
    }

    fun restore(open: () -> java.io.InputStream?) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val stream = open()
            val result = if (stream == null) {
                RestoreResult.Failed("Couldn't open that file.")
            } else {
                stream.use { backupManager.restoreBackup(it) }
            }
            _busy.value = false
            when (result) {
                is RestoreResult.Success ->
                    postMessage { NotesMessage.Restored(it, result.highlights) }

                is RestoreResult.Failed -> postMessage { NotesMessage.Failed(it, result.message) }
            }
        }
    }

    fun consumeMessage(id: Long) {
        if (_message.value?.id == id) _message.value = null
    }

    private fun postMessage(build: (Long) -> NotesMessage) {
        _message.value = build(System.nanoTime())
    }
}
