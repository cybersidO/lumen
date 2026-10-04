package com.sidharth.lumen.feature.speech

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import com.sidharth.lumen.core.settings.SpeechSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.readium.navigator.media.common.MediaNavigator
import org.readium.navigator.media.tts.TtsNavigator
import org.readium.navigator.media.tts.TtsNavigatorFactory
import org.readium.navigator.media.tts.android.AndroidTtsEngine
import org.readium.navigator.media.tts.android.AndroidTtsPreferences
import org.readium.navigator.media.tts.android.AndroidTtsPreferencesEditor
import org.readium.navigator.media.tts.android.AndroidTtsSettings
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** The concrete navigator type, spelled once. */
typealias LumenTtsNavigator = TtsNavigator<
    AndroidTtsSettings,
    AndroidTtsPreferences,
    AndroidTtsEngine.Error,
    AndroidTtsEngine.Voice,
    >

/** A voice the user can actually choose. */
data class SpeechVoice(
    val id: String,
    val label: String,
    val languageTag: String?,
    val quality: Int,
    /** True when this voice speaks the language the book is written in. */
    val matchesBook: Boolean,
)

sealed interface SpeechUiState {
    data object Idle : SpeechUiState
    data object Preparing : SpeechUiState
    data class Active(
        val playing: Boolean,
        /** The sentence being spoken, for the on-page highlight. */
        val utteranceLocator: Locator?,
        val utterance: String?,
        val minutesLeftOnTimer: Int?,
    ) : SpeechUiState
    data class Failed(val message: String) : SpeechUiState
}

/**
 * Owns read-aloud for the book on screen (M11).
 *
 * A singleton rather than ViewModel-scoped state because playback outlives the reader
 * composable — the foreground service keeps it going with the screen off, and a
 * configuration change must not stop the voice mid-sentence.
 *
 * The engine is Android's own TTS, driven through Readium's `TtsNavigator`, so the voices
 * are whichever ones the phone has. Voices that need the network are filtered out: Lumen
 * has no INTERNET permission, so choosing one would simply produce silence.
 */
@Singleton
class SpeechController @Inject constructor(
    private val application: Application,
) {
    private val mutex = Mutex()

    private val _state = MutableStateFlow<SpeechUiState>(SpeechUiState.Idle)
    val state: StateFlow<SpeechUiState> = _state.asStateFlow()

    private val _voices = MutableStateFlow<List<SpeechVoice>>(emptyList())
    val voices: StateFlow<List<SpeechVoice>> = _voices.asStateFlow()

    /** The live navigator, exposed so the media session can adapt it. */
    @Volatile
    var navigator: LumenTtsNavigator? = null
        private set

    /** Content hash of the book currently loaded for speech, if any. */
    @Volatile
    var bookContentHash: String? = null
        private set

    private var scope: CoroutineScope? = null
    private var collectJob: Job? = null
    private var timerJob: Job? = null
    private var onStopped: (() -> Unit)? = null

    /**
     * Prepares the navigator for [publication], starting at [start]. Idempotent for the
     * same book: calling it again while that book is loaded is a no-op, so re-entering
     * the reader does not restart the sentence.
     */
    suspend fun prepare(
        scope: CoroutineScope,
        contentHash: String,
        publication: Publication,
        start: Locator?,
        settings: SpeechSettings,
        onStopRequested: () -> Unit,
    ): Boolean = mutex.withLock {
        if (bookContentHash == contentHash && navigator != null) return@withLock true
        releaseLocked()

        _state.value = SpeechUiState.Preparing
        this.scope = scope
        this.onStopped = onStopRequested

        val factory = TtsNavigatorFactory(application, publication)
        if (factory == null) {
            _state.value = SpeechUiState.Failed(ERROR_UNSUPPORTED)
            return@withLock false
        }

        val listener = object : TtsNavigator.Listener {
            // Fired when the engine itself wants to stop (end of publication, or an
            // unrecoverable engine error). Surfaced rather than swallowed.
            override fun onStopRequested() {
                onStopRequested()
            }
        }

        val created = factory.createNavigator(listener, start, settings.toReadium())
        val nav = created.getOrNull()
        if (nav == null) {
            _state.value = SpeechUiState.Failed(ERROR_START)
            return@withLock false
        }

        navigator = nav
        bookContentHash = contentHash
        // The book's own language decides which voices are worth offering first: a phone
        // carries voices for dozens of languages, and a list led by Arabic for an English
        // novel reads as broken even though every entry is real.
        val bookLanguage = publication.metadata.languages.firstOrNull()
            ?.substringBefore('-')?.lowercase()
        _voices.value = nav.voices
            // No INTERNET permission, so a network voice would just fail silently.
            .filterNot { it.requiresNetwork }
            .map { voice -> voice.toSpeechVoice(bookLanguage) }
            .sortedWith(
                compareByDescending<SpeechVoice> { it.matchesBook }
                    .thenByDescending { it.quality }
                    .thenBy { it.label },
            )

        collectJob = scope.launch {
            nav.playback.collect { playback ->
                val location = nav.location.value
                val current = _state.value
                _state.value = SpeechUiState.Active(
                    playing = playback.playWhenReady &&
                        playback.state !is MediaNavigator.State.Ended,
                    utteranceLocator = location.utteranceLocator,
                    utterance = location.utterance,
                    minutesLeftOnTimer = (current as? SpeechUiState.Active)?.minutesLeftOnTimer,
                )
            }
        }
        true
    }

    fun play() {
        val nav = navigator ?: return
        // The service is what lets Android keep the voice going once the screen is off.
        // Started only when playback actually begins, so nothing sits in the foreground
        // while the user is merely looking at the settings sheet.
        ContextCompat.startForegroundService(
            application,
            Intent(application, SpeechService::class.java),
        )
        nav.play()
    }

    fun pause() { navigator?.pause() }

    fun toggle() {
        val active = _state.value as? SpeechUiState.Active ?: return
        if (active.playing) pause() else play()
    }

    fun next() { navigator?.skipToNextUtterance() }

    fun previous() { navigator?.skipToPreviousUtterance() }

    fun applySettings(settings: SpeechSettings) {
        navigator?.submitPreferences(settings.toReadium())
    }

    /**
     * Starts (or clears) the sleep timer. The countdown is exposed so the sheet can show
     * the minutes left rather than a silent promise.
     */
    fun startSleepTimer(minutes: Int) {
        timerJob?.cancel()
        if (minutes <= 0) {
            updateTimer(null)
            return
        }
        val owner = scope ?: return
        timerJob = owner.launch {
            var left = minutes
            while (left > 0) {
                updateTimer(left)
                delay(TimeUnit.MINUTES.toMillis(1))
                left--
            }
            updateTimer(null)
            pause()
            onStopped?.invoke()
        }
    }

    private fun updateTimer(minutes: Int?) {
        val active = _state.value as? SpeechUiState.Active ?: return
        _state.value = active.copy(minutesLeftOnTimer = minutes)
    }

    suspend fun release() = mutex.withLock { releaseLocked() }

    private fun releaseLocked() {
        application.stopService(Intent(application, SpeechService::class.java))
        timerJob?.cancel(); timerJob = null
        collectJob?.cancel(); collectJob = null
        navigator?.close()
        navigator = null
        bookContentHash = null
        _voices.value = emptyList()
        _state.value = SpeechUiState.Idle
    }

    private fun AndroidTtsEngine.Voice.toSpeechVoice(bookLanguage: String?): SpeechVoice {
        val tag = language?.code
        val base = tag?.substringBefore('-')?.lowercase()
        return SpeechVoice(
            id = id.value,
            label = readableLabel(id.value, tag),
            languageTag = tag?.let { java.util.Locale.forLanguageTag(it).displayName.ifBlank { it } },
            quality = quality.ordinal,
            matchesBook = bookLanguage != null && base == bookLanguage,
        )
    }

    /**
     * Android voice ids look like `en-gb-x-gba-local` or `en-US-language`. Neither is
     * something to show a reader, so the language is spelled out and whatever
     * distinguishes the variant is kept as a short suffix.
     */
    private fun readableLabel(id: String, tag: String?): String {
        val display = tag?.let { java.util.Locale.forLanguageTag(it).displayName }?.takeIf { it.isNotBlank() }
        val variant = id
            .removePrefix(tag.orEmpty())
            .trim('-', '#', '_')
            .removePrefix("x-")
            .removeSuffix("-local")
            .removeSuffix("-language")
            .trim('-')
        return when {
            display == null -> id
            variant.isBlank() -> display
            else -> "$display · $variant"
        }
    }

    private fun SpeechSettings.toReadium() = AndroidTtsPreferences(
        speed = speed.toDouble(),
        pitch = pitch.toDouble(),
    )

    private companion object {
        const val ERROR_UNSUPPORTED = "This book can't be read aloud — Lumen couldn't find any text in it."
        const val ERROR_START = "Read-aloud couldn't start. Your phone may have no speech voices installed."
    }
}
