package com.sidharth.lumen.feature.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sidharth.lumen.core.database.BookDao
import com.sidharth.lumen.core.database.HighlightEntity
import com.sidharth.lumen.core.di.ApplicationScope
import com.sidharth.lumen.core.di.IoDispatcher
import com.sidharth.lumen.core.readium.PublicationRepository
import com.sidharth.lumen.core.settings.AppSettings
import com.sidharth.lumen.core.settings.BrightnessSettings
import com.sidharth.lumen.core.settings.ReaderTypography
import com.sidharth.lumen.core.settings.ReadingSettings
import com.sidharth.lumen.core.settings.SettingsRepository
import com.sidharth.lumen.core.settings.ThemeMode
import com.sidharth.lumen.feature.highlight.Highlight
import com.sidharth.lumen.feature.highlight.HighlightColor
import com.sidharth.lumen.feature.highlight.HighlightRepository
import com.sidharth.lumen.feature.dictionary.LookupContext
import com.sidharth.lumen.feature.dictionary.LookupRepository
import com.sidharth.lumen.feature.dictionary.LookupUiState
import com.sidharth.lumen.feature.speech.SpeechController
import com.sidharth.lumen.feature.speech.SpeechUiState
import com.sidharth.lumen.core.settings.SpeechSettings
import com.sidharth.lumen.feature.stats.ReadingPace
import com.sidharth.lumen.feature.stats.ReadingSessionRecorder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.positionsByReadingOrder
import java.io.File
import javax.inject.Inject

sealed interface ReaderUiState {
    data object Loading : ReaderUiState

    data class Ready(
        val bookId: Long,
        val contentHash: String,
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
    /** "~12 min left in this chapter", once the reader's pace has settled; null until then. */
    val chapterMinutesLeft: Int? = null,
)

enum class IndicatorKind { BRIGHTNESS, FONT_SIZE }

/** The transient drag readout. Null when nothing should be shown. */
data class IndicatorState(
    val kind: IndicatorKind,
    val fraction: Float,
)

/** How long the indicator lingers after the finger lifts, before its 600ms fade. */
const val INDICATOR_LINGER_MILLIS = 600L

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bookDao: BookDao,
    private val publications: PublicationRepository,
    private val settingsRepository: SettingsRepository,
    private val highlightRepository: HighlightRepository,
    private val lookupRepository: LookupRepository,
    private val sessionRecorder: ReadingSessionRecorder,
    private val speech: SpeechController,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : ViewModel() {

    private val bookId: Long = checkNotNull(savedStateHandle["bookId"]) {
        "ReaderViewModel requires a bookId argument"
    }

    /** Set when arriving from the notes list; -1 means "open where I left off". */
    private val jumpToHighlightId: Long = savedStateHandle.get<Long>("highlightId") ?: -1L

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

    /** Readium's synthetic page positions, one list per reading-order resource (M10). */
    private var positionsByResource: List<List<Locator>> = emptyList()
    private val pace = ReadingPace()
    private var lastPosition: Int? = null

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
                    // Arriving from the notes list overrides the saved reading position,
                    // so the entry the user tapped is what actually opens (§2.6).
                    val jumpLocator = jumpToHighlightId
                        .takeIf { it > 0 }
                        ?.let { id ->
                            withContext(ioDispatcher) {
                                highlightRepository.load(id)?.locator
                            }
                        }

                    _uiState.value = ReaderUiState.Ready(
                        bookId = bookId,
                        contentHash = book.contentHash,
                        title = book.title,
                        publication = publication,
                        initialLocator = jumpLocator
                            ?: book.currentLocatorJson?.let(::parseLocator),
                    )
                    withContext(ioDispatcher) {
                        bookDao.markOpened(bookId, System.currentTimeMillis())
                        // Computed once per open; a font-size change repaginates and
                        // Readium recomputes these lazily, so they are re-read on demand
                        // in onLocatorChanged rather than cached forever.
                        positionsByResource = runCatching { publication.positionsByReadingOrder() }
                            .getOrDefault(emptyList())
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
        val now = System.currentTimeMillis()

        // A real page turn is a change of position; repagination and restore also emit
        // here, and those must not count as reading.
        val position = locator.locations.position
        val turned = position != null && lastPosition != null && position != lastPosition
        lastPosition = position
        if (turned) pace.onPageTurned(now) else pace.markInteraction(now)

        _chrome.value = _chrome.value.copy(
            progressFraction = progress.coerceIn(0f, 1f),
            chapterTitle = locator.title?.takeIf { it.isNotBlank() } ?: _chrome.value.chapterTitle,
            chapterMinutesLeft = chapterMinutesLeft(locator),
        )
        viewModelScope.launch(ioDispatcher) {
            bookDao.saveProgress(
                id = bookId,
                locatorJson = locator.toJSON().toString(),
                progressFraction = progress.coerceIn(0f, 1f),
            )
            if (turned) sessionRecorder.pageTurned(progress, now)
        }
    }

    /**
     * Pages remaining in the current resource, times the reader's measured pace. Null
     * until the pace has a few real turns behind it, so the chrome never shows a number
     * that is only the default guess.
     */
    private fun chapterMinutesLeft(locator: Locator): Int? {
        if (!pace.isSettled) return null
        val position = locator.locations.position ?: return null
        val resource = positionsByResource.firstOrNull { list ->
            list.any { it.href == locator.href }
        } ?: return null
        val index = resource.indexOfFirst { it.locations.position == position }
        if (index < 0) return null
        return pace.minutesFor(resource.size - index - 1)
    }

    // --- reading sessions (M10 stats) ----------------------------------------------------

    /** The reader is on screen and in the foreground. */
    fun onReaderVisible() {
        val state = _uiState.value as? ReaderUiState.Ready ?: return
        viewModelScope.launch {
            sessionRecorder.start(state.contentHash, _chrome.value.progressFraction, System.currentTimeMillis())
        }
    }

    /** The reader left the foreground; flushed through the application scope so a
     *  back-press that also clears the ViewModel still records the session. */
    fun onReaderHidden() {
        applicationScope.launch { sessionRecorder.stop(System.currentTimeMillis()) }
    }

    fun updateReading(transform: (ReadingSettings) -> ReadingSettings) {
        viewModelScope.launch { settingsRepository.updateReading(transform) }
    }

    // --- read-aloud (M11) -----------------------------------------------------------------

    val speechState: StateFlow<SpeechUiState> = speech.state
    val speechVoices = speech.voices

    private val _speechRequested = MutableStateFlow(false)
    /** True once the reader has asked for speech, so the UI can show its bar. */
    val speechRequested: StateFlow<Boolean> = _speechRequested

    /**
     * Prepares and starts read-aloud from the current reading position, so the voice
     * picks up where the eye left off rather than at the top of the chapter.
     */
    fun startSpeech(from: Locator?) {
        val state = _uiState.value as? ReaderUiState.Ready ?: return
        _speechRequested.value = true
        viewModelScope.launch {
            val ready = speech.prepare(
                scope = applicationScope,
                contentHash = state.contentHash,
                publication = state.publication,
                start = from,
                settings = settings.value.speech,
                onStopRequested = { stopSpeech() },
            )
            if (!ready) {
                _speechRequested.value = false
                return@launch
            }
            speech.applySettings(settings.value.speech)
            speech.startSleepTimer(settings.value.speech.sleepTimerMinutes)
            speech.play()
        }
    }

    fun toggleSpeech() = speech.toggle()
    fun speechNext() = speech.next()
    fun speechPrevious() = speech.previous()

    fun stopSpeech() {
        _speechRequested.value = false
        applicationScope.launch { speech.release() }
    }

    fun updateSpeech(transform: (SpeechSettings) -> SpeechSettings) {
        viewModelScope.launch {
            settingsRepository.updateSpeech(transform)
            val updated = transform(settings.value.speech).sanitised()
            speech.applySettings(updated)
            speech.startSleepTimer(updated.sleepTimerMinutes)
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
        _indicator.value = IndicatorState(
            kind = IndicatorKind.FONT_SIZE,
            // The label reads from settings on the next emission; fraction is against the
            // full size range so the bar has a meaningful fill level.
            fraction = fontSizeFraction(
                settings.value.typography.fontSizePercent +
                    steps * ReaderTypography.FONT_SIZE_STEP_PERCENT,
            ),
        )
    }

    // --- gesture-driven brightness -------------------------------------------------

    /**
     * The live value during a drag. Held here, not written to DataStore per movement —
     * a drag emits dozens of values a second and each DataStore edit is a disk write.
     * [commitBrightness] persists once, on finger-up.
     */
    private val _liveBrightness = MutableStateFlow<BrightnessSettings?>(null)
    val liveBrightness: StateFlow<BrightnessSettings?> = _liveBrightness

    private val _indicator = MutableStateFlow<IndicatorState?>(null)
    val indicator: StateFlow<IndicatorState?> = _indicator

    /** Where a fresh drag starts from: the live value if mid-drag, else the saved one. */
    fun brightnessSliderValue(): Float =
        (_liveBrightness.value ?: settings.value.brightness).toSlider()

    fun onBrightnessDrag(sliderValue: Float) {
        val updated = BrightnessSettings.fromSlider(sliderValue)
        _liveBrightness.value = updated
        _indicator.value = IndicatorState(IndicatorKind.BRIGHTNESS, sliderValue)
    }

    fun commitBrightness() {
        val final = _liveBrightness.value ?: return
        viewModelScope.launch { settingsRepository.updateBrightness { final } }
        scheduleIndicatorFade()
    }

    fun onFontSizeDragEnd() {
        scheduleIndicatorFade()
    }

    fun toggleThemeFromGesture() {
        viewModelScope.launch {
            val current = settings.value.themeMode
            // Two-finger tap toggles light/dark (§2.3). AMOLED and Sepia map to their
            // nearest counterpart rather than being silently overwritten on the way back.
            val next = when (current) {
                ThemeMode.LIGHT, ThemeMode.SEPIA -> ThemeMode.DARK
                ThemeMode.DARK, ThemeMode.AMOLED -> ThemeMode.LIGHT
                ThemeMode.SYSTEM -> ThemeMode.DARK
            }
            settingsRepository.setThemeMode(next)
        }
    }

    private var fadeJob: Job? = null

    private fun scheduleIndicatorFade() {
        fadeJob?.cancel()
        fadeJob = viewModelScope.launch {
            delay(INDICATOR_LINGER_MILLIS)
            _indicator.value = null
        }
    }

    private fun fontSizeFraction(percent: Int): Float =
        (percent - ReaderTypography.MIN_FONT_SIZE_PERCENT).toFloat() /
            (ReaderTypography.MAX_FONT_SIZE_PERCENT - ReaderTypography.MIN_FONT_SIZE_PERCENT)

    override fun onCleared() {
        // The publication owns open file handles. Closing outlives this ViewModel, so it
        // runs on the application scope — viewModelScope is already cancelled here.
        val id = bookId
        val now = System.currentTimeMillis()
        applicationScope.launch {
            // Belt and braces: onReaderHidden normally closes the session first, but a
            // ViewModel cleared without a lifecycle stop (rare) must not leave it open.
            sessionRecorder.stop(now)
            publications.close(id)
        }
    }

    private fun parseLocator(json: String): Locator? = runCatching {
        Locator.fromJSON(JSONObject(json))
    }.getOrNull()

    // --- highlights (M4) --------------------------------------------------------------

    /** Entities for the open book; drives decoration rendering. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val highlights: StateFlow<List<HighlightEntity>> = uiState
        .flatMapLatest { state ->
            when (state) {
                is ReaderUiState.Ready -> highlightRepository.observeEntities(state.contentHash)
                else -> flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The locator captured when text selection produced an action mode; null = no bar. */
    private val _activeSelection = MutableStateFlow<Locator?>(null)
    val activeSelection: StateFlow<Locator?> = _activeSelection

    /** Highlight open in the edit sheet, loaded with its note and tags. */
    private val _editingHighlight = MutableStateFlow<Highlight?>(null)
    val editingHighlight: StateFlow<Highlight?> = _editingHighlight

    fun onSelectionChanged(locator: Locator?) {
        _activeSelection.value = locator
    }

    fun clearSelection() {
        _activeSelection.value = null
    }

    /** Returns immediately; the decoration appears via the highlights flow. */
    fun createHighlight(color: HighlightColor, thenEdit: Boolean = false) {
        val state = uiState.value as? ReaderUiState.Ready ?: return
        val locator = _activeSelection.value ?: return
        _activeSelection.value = null
        viewModelScope.launch {
            val id = highlightRepository.create(
                bookContentHash = state.contentHash,
                locator = locator,
                color = color,
                chapterTitle = chrome.value.chapterTitle,
            )
            if (thenEdit) openHighlightEditor(id)
        }
    }

    fun openHighlightEditor(id: Long) {
        viewModelScope.launch { _editingHighlight.value = highlightRepository.load(id) }
    }

    fun closeHighlightEditor() {
        _editingHighlight.value = null
    }

    fun recolorEditingHighlight(color: HighlightColor) {
        val editing = _editingHighlight.value ?: return
        viewModelScope.launch {
            highlightRepository.recolor(editing.id, color)
            _editingHighlight.value = highlightRepository.load(editing.id)
        }
    }

    fun saveEditingNote(body: String) {
        val editing = _editingHighlight.value ?: return
        viewModelScope.launch {
            highlightRepository.setNote(editing.id, body)
            _editingHighlight.value = highlightRepository.load(editing.id)
        }
    }

    fun saveEditingTags(tags: List<String>) {
        val editing = _editingHighlight.value ?: return
        viewModelScope.launch {
            highlightRepository.setTags(editing.id, tags)
            _editingHighlight.value = highlightRepository.load(editing.id)
        }
    }

    // --- dictionary (M5) --------------------------------------------------------------

    private val _lookup = MutableStateFlow<LookupUiState?>(null)
    val lookup: StateFlow<LookupUiState?> = _lookup

    /**
     * Looks up the current selection (or a word passed directly). The reading position is
     * untouched: the navigator is never rebuilt, only a sheet is shown above it.
     */
    fun lookUpSelection() {
        val locator = _activeSelection.value ?: return
        val word = locator.text.highlight?.trim().orEmpty()
        if (word.isBlank()) return
        lookUp(
            word = word,
            surrounding = listOfNotNull(
                locator.text.before,
                locator.text.highlight,
                locator.text.after,
            ).joinToString(""),
            resourceHref = locator.href.toString(),
        )
        _activeSelection.value = null
    }

    fun lookUp(word: String, surrounding: String? = null, resourceHref: String? = null) {
        val state = uiState.value as? ReaderUiState.Ready
        _lookup.value = LookupUiState.Loading(word)
        viewModelScope.launch {
            val result = lookupRepository.lookup(
                word = word,
                context = LookupContext(
                    bookContentHash = state?.contentHash,
                    resourceHref = resourceHref,
                    chapterTitle = chrome.value.chapterTitle,
                    surroundingText = surrounding,
                ),
            )
            // Guard against a second lookup started while this one was in flight.
            if (_lookup.value is LookupUiState.Loading) {
                _lookup.value = LookupUiState.Loaded(result)
            }
        }
    }

    fun dismissLookup() {
        _lookup.value = null
    }

    fun deleteEditingHighlight() {
        val editing = _editingHighlight.value ?: return
        _editingHighlight.value = null
        viewModelScope.launch { highlightRepository.delete(editing.id) }
    }
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
