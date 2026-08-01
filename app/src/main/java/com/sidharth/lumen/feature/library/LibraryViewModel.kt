package com.sidharth.lumen.feature.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sidharth.lumen.core.books.BookImporter
import com.sidharth.lumen.core.books.BookStorage
import com.sidharth.lumen.core.books.ImportProgress
import com.sidharth.lumen.core.books.ImportResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val books: List<Book> = emptyList(),
    val query: String = "",
    val sort: LibrarySort = LibrarySort.RECENT,
    val isLoaded: Boolean = false,
) {
    val isEmptyLibrary: Boolean get() = isLoaded && books.isEmpty() && query.isEmpty()
    val hasNoMatches: Boolean get() = isLoaded && books.isEmpty() && query.isNotEmpty()
}

/** Import is a foreground, interruptible job the user can watch. */
sealed interface ImportUiState {
    data object Idle : ImportUiState
    data class Running(val progress: ImportProgress) : ImportUiState
}

/**
 * One-shot snackbar messages. Modelled as data rather than formatted strings so the
 * ViewModel never touches Context or resources, and the screen owns the wording.
 */
sealed interface LibraryMessage {
    val id: Long

    data class Imported(override val id: Long, val title: String) : LibraryMessage
    data class AlreadyPresent(override val id: Long, val title: String) : LibraryMessage
    data class ImportFailed(override val id: Long, val reason: String) : LibraryMessage
    data class Deleted(
        override val id: Long,
        val title: String,
        val keptHighlights: Int,
    ) : LibraryMessage

    data class ReaderNotBuilt(override val id: Long) : LibraryMessage
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val importer: BookImporter,
    storage: BookStorage,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val sort = MutableStateFlow(LibrarySort.RECENT)

    private val _importState = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val importState: StateFlow<ImportUiState> = _importState

    private val _messages = MutableStateFlow<LibraryMessage?>(null)
    val messages: StateFlow<LibraryMessage?> = _messages

    private val _pendingDeletion = MutableStateFlow<PendingDeletion?>(null)
    val pendingDeletion: StateFlow<PendingDeletion?> = _pendingDeletion

    val uiState: StateFlow<LibraryUiState> =
        combine(repository.books, query, sort) { books, currentQuery, currentSort ->
            LibraryUiState(
                books = LibraryQuery.apply(books, currentQuery, currentSort),
                query = currentQuery,
                sort = currentSort,
                isLoaded = true,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LibraryUiState(),
        )

    init {
        // A leftover staging file means a previous import was killed mid-copy.
        storage.clearStaging()
    }

    fun setQuery(value: String) { query.value = value }

    fun setSort(value: LibrarySort) { sort.value = value }

    fun import(uri: Uri) {
        if (_importState.value is ImportUiState.Running) return
        viewModelScope.launch {
            _importState.value = ImportUiState.Running(ImportProgress.Copying(0, null))
            val result = importer.import(uri) { progress ->
                _importState.value = ImportUiState.Running(progress)
            }
            _importState.value = ImportUiState.Idle
            postMessage { id ->
                when (result) {
                    is ImportResult.Imported -> LibraryMessage.Imported(id, result.title)
                    is ImportResult.AlreadyPresent -> LibraryMessage.AlreadyPresent(id, result.title)
                    is ImportResult.Failed -> LibraryMessage.ImportFailed(id, result.message)
                }
            }
        }
    }

    /**
     * Tapping a book is wired but has nothing to open yet. It says so instead of silently
     * doing nothing, and deliberately does *not* stamp lastOpenedAt — that would reorder
     * the library to record a read that never happened.
     */
    fun requestOpen(book: Book) {
        postMessage(LibraryMessage::ReaderNotBuilt)
    }

    /** Opens the "what about the highlights?" prompt; nothing is deleted until confirmed. */
    fun requestDelete(book: Book) {
        viewModelScope.launch {
            _pendingDeletion.value = PendingDeletion(book, repository.highlightCount(book))
        }
    }

    fun cancelDelete() { _pendingDeletion.value = null }

    fun confirmDelete(disposition: HighlightDisposition) {
        val pending = _pendingDeletion.value ?: return
        _pendingDeletion.value = null
        viewModelScope.launch {
            repository.delete(pending.book, disposition)
            postMessage { id ->
                LibraryMessage.Deleted(
                    id = id,
                    title = pending.book.title,
                    keptHighlights = if (disposition == HighlightDisposition.KEEP) {
                        pending.highlightCount
                    } else {
                        0
                    },
                )
            }
        }
    }

    fun consumeMessage(id: Long) {
        _messages.update { current -> current?.takeIf { it.id != id } }
    }

    private fun postMessage(build: (id: Long) -> LibraryMessage) {
        _messages.value = build(System.nanoTime())
    }
}

data class PendingDeletion(
    val book: Book,
    val highlightCount: Int,
)
