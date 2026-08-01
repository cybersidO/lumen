package com.sidharth.lumen.feature.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sidharth.lumen.core.database.BookDao
import com.sidharth.lumen.core.di.ApplicationScope
import com.sidharth.lumen.core.di.IoDispatcher
import com.sidharth.lumen.core.readium.PublicationRepository
import com.sidharth.lumen.core.settings.AppSettings
import com.sidharth.lumen.core.settings.ReaderTypography
import com.sidharth.lumen.core.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import java.io.File
import javax.inject.Inject

sealed interface ReaderUiState {
    data object Loading : ReaderUiState

    data class Ready(
        val bookId: Long,
        val title: String,
        val publication: Publication,
        /** Where to open. Null means "the beginning" — a book never opened before. */
        val initialLocator: Locator?,
    ) : ReaderUiState

    data class Failed(val message: String) : ReaderUiState
}

/** Chrome visibility and the live position readout; not persisted. */
data class ReaderChromeState(
    val visible: Boolean = false,
    val progressFraction: Float = 0f,
    val chapterTitle: String? = null,
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bookDao: BookDao,
    private val publications: PublicationRepository,
    private val settingsRepository: SettingsRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : ViewModel() {

    private val bookId: Long = checkNotNull(savedStateHandle["bookId"]) {
        "ReaderViewModel requires a bookId argument"
    }

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState

    private val _chrome = MutableStateFlow(ReaderChromeState())
    val chrome: StateFlow<ReaderChromeState> = _chrome

    val settings: StateFlow<AppSettings> = settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AppSettings.Defaults,
    )

    /** Table of contents, flattened once the publication is open. */
    private val _tableOfContents = MutableStateFlow<List<TocEntry>>(emptyList())
    val tableOfContents: StateFlow<List<TocEntry>> = _tableOfContents

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val book = withContext(ioDispatcher) { bookDao.findById(bookId) }
            if (book == null) {
                _uiState.value = ReaderUiState.Failed("That book is no longer in your library.")
                return@launch
            }

            publications.open(bookId, File(book.filePath))
                .onSuccess { publication ->
                    _tableOfContents.value = flattenToc(publication.tableOfContents)
                    _chrome.value = _chrome.value.copy(progressFraction = book.progressFraction)
                    _uiState.value = ReaderUiState.Ready(
                        bookId = bookId,
                        title = book.title,
                        publication = publication,
                        initialLocator = book.currentLocatorJson?.let(::parseLocator),
                    )
                    withContext(ioDispatcher) {
                        bookDao.markOpened(bookId, System.currentTimeMillis())
                    }
                }
                .onFailure { error ->
                    _uiState.value = ReaderUiState.Failed(
                        error.message ?: "This book couldn't be opened.",
                    )
                }
        }
    }

    /**
     * Called on every page turn. The locator — not a page number — is what gets stored,
     * so the position survives a font-size change that repaginates the whole book.
     */
    fun onLocatorChanged(locator: Locator) {
        val progress = locator.locations.totalProgression?.toFloat() ?: return
        _chrome.value = _chrome.value.copy(
            progressFraction = progress.coerceIn(0f, 1f),
            chapterTitle = locator.title?.takeIf { it.isNotBlank() } ?: _chrome.value.chapterTitle,
        )
        viewModelScope.launch(ioDispatcher) {
            bookDao.saveProgress(
                id = bookId,
                locatorJson = locator.toJSON().toString(),
                progressFraction = progress.coerceIn(0f, 1f),
            )
        }
    }

    fun toggleChrome() {
        _chrome.value = _chrome.value.copy(visible = !_chrome.value.visible)
    }

    fun hideChrome() {
        _chrome.value = _chrome.value.copy(visible = false)
    }

    fun updateTypography(transform: (ReaderTypography) -> ReaderTypography) {
        viewModelScope.launch { settingsRepository.updateTypography(transform) }
    }

    fun nudgeFontSize(steps: Int) {
        updateTypography { current ->
            current.withFontSizeClamped(
                current.fontSizePercent + steps * ReaderTypography.FONT_SIZE_STEP_PERCENT,
            )
        }
    }

    override fun onCleared() {
        // The publication owns open file handles. Closing outlives this ViewModel, so it
        // runs on the application scope — viewModelScope is already cancelled here.
        val id = bookId
        applicationScope.launch { publications.close(id) }
    }

    private fun parseLocator(json: String): Locator? = runCatching {
        Locator.fromJSON(JSONObject(json))
    }.getOrNull()
}

/** A flattened TOC row; [depth] drives indentation rather than a nested tree. */
data class TocEntry(
    val title: String,
    val link: Link,
    val depth: Int,
)

internal fun flattenToc(links: List<Link>, depth: Int = 0): List<TocEntry> =
    links.flatMap { link ->
        val title = link.title?.trim().orEmpty()
        val self = if (title.isNotEmpty()) {
            listOf(TocEntry(title = title, link = link, depth = depth))
        } else {
            emptyList()
        }
        self + flattenToc(link.children, depth + 1)
    }
