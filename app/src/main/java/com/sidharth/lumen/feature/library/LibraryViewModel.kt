package com.sidharth.lumen.feature.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sidharth.lumen.core.books.BookImporter
import com.sidharth.lumen.core.books.BookFolderStore
import com.sidharth.lumen.core.books.DeviceBookSearch
import com.sidharth.lumen.core.books.FolderScanner
import com.sidharth.lumen.core.books.FoundBook
import kotlinx.coroutines.Job
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

/** The folder-scan flow (M10): pick a folder → scanning → a list to tick → importing. */
sealed interface ScanUiState {
    data object Idle : ScanUiState
    /** No folders remembered yet — the first run asks for one. */
    data object NeedsFolder : ScanUiState
    /** Whole-device search was asked for but "all files access" is not granted yet. */
    data object NeedsAllFilesAccess : ScanUiState
    data class Scanning(val foldersSeen: Int) : ScanUiState
    data class Found(val books: List<FoundBook>, val selected: Set<Uri>) : ScanUiState
    data class Importing(val done: Int, val total: Int, val currentName: String) : ScanUiState
    data class Finished(val imported: Int, val alreadyPresent: Int, val failed: Int) : ScanUiState
}

/**
 * One-shot snackbar messages. Modelled as data rather than formatted strings so the
 * ViewModel never touches Context or resources, and the screen owns the wording.
 */
sealed interface LibraryMessage {
    data class BatchImported(
        override val id: Long,
        val imported: Int,
        val alreadyPresent: Int,
        val failed: Int,
    ) : LibraryMessage

    val id: Long

    data class Imported(override val id: Long, val title: String) : LibraryMessage
    data class AlreadyPresent(override val id: Long, val title: String) : LibraryMessage
    data class ImportFailed(override val id: Long, val reason: String) : LibraryMessage
    data class Deleted(
        override val id: Long,
        val title: String,
        val keptHighlights: Int,
    ) : LibraryMessage

}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val importer: BookImporter,
    private val scanner: FolderScanner,
    private val folderStore: BookFolderStore,
    private val deviceSearch: DeviceBookSearch,
    storage: BookStorage,
) : ViewModel() {

    private val _scanState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val scanState: StateFlow<ScanUiState> = _scanState
    private var scanJob: Job? = null

    /** Folders the user has granted; shown in Settings and used by every rescan. */
    val bookFolders = folderStore.folders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    fun import(uri: Uri) = import(listOf(uri))

    /**
     * Imports one or many files picked in the system picker (M10 made the picker
     * multi-select). Sequential on purpose: two imports hashing at once would just fight
     * over the disk, and the progress bar has one thing to show.
     */
    fun import(uris: List<Uri>) {
        if (uris.isEmpty() || _importState.value is ImportUiState.Running) return
        viewModelScope.launch {
            var imported = 0
            var present = 0
            var failed = 0
            var lastTitle: String? = null
            var lastFailure: String? = null
            for (uri in uris) {
                _importState.value = ImportUiState.Running(ImportProgress.Copying(0, null))
                when (val result = importer.import(uri) { progress ->
                    _importState.value = ImportUiState.Running(progress)
                }) {
                    is ImportResult.Imported -> { imported++; lastTitle = result.title }
                    is ImportResult.AlreadyPresent -> { present++; lastTitle = result.title }
                    is ImportResult.Failed -> { failed++; lastFailure = result.message }
                }
            }
            _importState.value = ImportUiState.Idle
            postMessage { id ->
                when {
                    uris.size == 1 && imported == 1 -> LibraryMessage.Imported(id, lastTitle.orEmpty())
                    uris.size == 1 && present == 1 -> LibraryMessage.AlreadyPresent(id, lastTitle.orEmpty())
                    uris.size == 1 -> LibraryMessage.ImportFailed(id, lastFailure ?: "Import failed.")
                    else -> LibraryMessage.BatchImported(id, imported, present, failed)
                }
            }
        }
    }

    // --- folder scan (M10) ---------------------------------------------------------------

    /**
     * The search button (1.4.0). Sweeps the whole device when "all files access" is
     * granted, and falls back to the remembered folders when it is not — so the feature
     * still works for anyone who declines the permission.
     */
    fun search() {
        // Thorough search is the headline, so a missing grant offers to fix itself rather
        // than quietly doing the narrower folder search instead.
        if (deviceSearch.hasAccess()) searchWholeDevice() else _scanState.value = ScanUiState.NeedsAllFilesAccess
    }

    /** Called after the user returns from the grant screen. */
    fun searchAfterGrant() {
        if (deviceSearch.hasAccess()) searchWholeDevice() else _scanState.value = ScanUiState.NeedsAllFilesAccess
    }

    fun allFilesIntent() = deviceSearch.grantIntent()
        ?: deviceSearch.fallbackGrantIntent()

    fun allFilesFallbackIntent() = deviceSearch.fallbackGrantIntent()

    private fun searchWholeDevice() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _scanState.value = ScanUiState.Scanning(0)
            val books = runCatching {
                deviceSearch.search { n -> _scanState.value = ScanUiState.Scanning(n) }
            }.getOrElse { emptyList() }
            _scanState.value = ScanUiState.Found(books, books.map { it.uri }.toSet())
        }
    }

    /**
     * Walks every remembered folder. Asks for a folder only the first time, or when every
     * previous grant has gone stale.
     */
    fun searchRememberedFolders() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            val folders = folderStore.current()
            if (folders.isEmpty()) {
                _scanState.value = ScanUiState.NeedsFolder
                return@launch
            }
            _scanState.value = ScanUiState.Scanning(0)
            var seen = 0
            val found = buildList {
                for (folder in folders) {
                    val books = runCatching {
                        scanner.scan(folder.uri) { n ->
                            _scanState.value = ScanUiState.Scanning(seen + n)
                        }
                    }.getOrElse { emptyList() }
                    seen += 1
                    addAll(books)
                }
            }
            // The same book can sit in two granted folders; show it once.
            val unique = found.distinctBy { it.uri.toString() }
            _scanState.value = ScanUiState.Found(unique, unique.map { it.uri }.toSet())
        }
    }

    /** Remembers a newly granted folder, then searches everything. */
    fun addFolderAndSearch(treeUri: Uri) {
        viewModelScope.launch {
            if (!folderStore.add(treeUri)) {
                _scanState.value = ScanUiState.Idle
                postMessage { LibraryMessage.ImportFailed(it, FOLDER_REFUSED) }
                return@launch
            }
            searchRememberedFolders()
        }
    }

    fun forgetFolder(uri: Uri) {
        viewModelScope.launch { folderStore.remove(uri) }
    }

    fun toggleFound(uri: Uri) {
        val state = _scanState.value as? ScanUiState.Found ?: return
        val selected = if (uri in state.selected) state.selected - uri else state.selected + uri
        _scanState.value = state.copy(selected = selected)
    }

    fun selectAllFound(select: Boolean) {
        val state = _scanState.value as? ScanUiState.Found ?: return
        _scanState.value = state.copy(selected = if (select) state.books.map { it.uri }.toSet() else emptySet())
    }

    fun importFound() {
        val state = _scanState.value as? ScanUiState.Found ?: return
        val chosen = state.books.filter { it.uri in state.selected }
        if (chosen.isEmpty()) return
        scanJob = viewModelScope.launch {
            var imported = 0
            var present = 0
            var failed = 0
            chosen.forEachIndexed { index, book ->
                _scanState.value = ScanUiState.Importing(index, chosen.size, book.displayName)
                when (importer.import(book.uri)) {
                    is ImportResult.Imported -> imported++
                    is ImportResult.AlreadyPresent -> present++
                    is ImportResult.Failed -> failed++
                }
            }
            _scanState.value = ScanUiState.Finished(imported, present, failed)
        }
    }

    fun dismissScan() {
        scanJob?.cancel()
        _scanState.value = ScanUiState.Idle
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

    private companion object {
        const val FOLDER_REFUSED =
            "Android won't grant that folder — it refuses Download itself and the top of " +
                "storage. Pick a subfolder, such as Download/Books."
    }
}

data class PendingDeletion(
    val book: Book,
    val highlightCount: Int,
)
