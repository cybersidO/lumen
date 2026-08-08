package com.sidharth.lumen.feature.dictionary

import com.sidharth.lumen.core.database.LookupEventDao
import com.sidharth.lumen.core.database.LookupEventEntity
import com.sidharth.lumen.core.dictionary.Dictionary
import com.sidharth.lumen.core.dictionary.DictionaryEntry
import com.sidharth.lumen.core.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Where in the book a lookup happened; all of it optional so lookups work anywhere. */
data class LookupContext(
    val bookContentHash: String? = null,
    val resourceHref: String? = null,
    val chapterTitle: String? = null,
    /** Surrounding text, from which the sentence is extracted. */
    val surroundingText: String? = null,
)

data class LookupResult(
    val query: String,
    val entries: List<DictionaryEntry>,
    val dictionaryName: String,
    val attribution: String,
) {
    val found: Boolean get() = entries.isNotEmpty()
}

@Singleton
class LookupRepository @Inject constructor(
    private val dictionary: Dictionary,
    private val lookupEventDao: LookupEventDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * Looks a word up and records it. The log is written for misses too — a word the
     * dictionary could not resolve is exactly the kind of thing worth revisiting, and
     * hiding it would make the log quietly dishonest.
     */
    suspend fun lookup(word: String, context: LookupContext): LookupResult =
        withContext(ioDispatcher) {
            val entries = dictionary.lookup(word)
            lookupEventDao.insert(
                LookupEventEntity(
                    word = word.trim(),
                    lemma = entries.firstOrNull()?.headword,
                    bookContentHash = context.bookContentHash,
                    resourceHref = context.resourceHref,
                    chapterTitle = context.chapterTitle,
                    sentence = context.surroundingText?.let { SentenceExtractor.around(it, word) },
                    dictionaryId = dictionary.id,
                    found = entries.isNotEmpty(),
                    createdAt = System.currentTimeMillis(),
                ),
            )
            LookupResult(
                query = word.trim(),
                entries = entries,
                dictionaryName = dictionary.displayName,
                attribution = dictionary.attribution,
            )
        }
}

/**
 * Pulls the sentence containing a word out of a longer passage.
 *
 * Kept pure and separate so it can be tested without a device — the log is only useful if
 * the stored context is actually the right sentence.
 */
object SentenceExtractor {

    private const val MAX_SENTENCE_CHARS = 400

    /**
     * The sentence in [text] containing [word], trimmed to something readable. Falls back
     * to a window around the word when there is no sentence punctuation to work with,
     * and to the whole passage when the word cannot be found at all.
     */
    fun around(text: String, word: String): String? {
        val haystack = text.trim()
        if (haystack.isEmpty()) return null

        val needle = word.trim().trim { !it.isLetterOrDigit() }
        val index = if (needle.isEmpty()) {
            -1
        } else {
            haystack.indexOf(needle, ignoreCase = true)
        }
        if (index < 0) return haystack.take(MAX_SENTENCE_CHARS).ifBlank { null }

        var start = 0
        for (i in index - 1 downTo 1) {
            if (isSentenceBreak(haystack, i)) {
                start = i + 1
                break
            }
        }

        var end = haystack.length
        for (i in index + needle.length until haystack.length) {
            if (isSentenceBreak(haystack, i)) {
                end = i + 1
                break
            }
        }

        return haystack.substring(start, end)
            .trim()
            .replace(WHITESPACE, " ")
            .take(MAX_SENTENCE_CHARS)
            .ifBlank { null }
    }

    /**
     * A sentence break is terminal punctuation at the end of the text or followed by
     * whitespace — except after an abbreviation or an initial.
     *
     * "Dr. Chandra" and "J. R. R. Tolkien" both look exactly like sentence ends to the
     * naive rule, and splitting there stores half a sentence as the review prompt.
     */
    private fun isSentenceBreak(text: String, index: Int): Boolean {
        val char = text[index]
        if (char !in ".!?") return false
        if (index + 1 < text.length && !text[index + 1].isWhitespace()) return false
        // Only a full stop is ambiguous; "!" and "?" never end an abbreviation.
        if (char != '.') return true

        val wordBefore = text.substring(0, index)
            .takeLastWhile { !it.isWhitespace() }
            .trimStart { !it.isLetter() }
        // A single letter is an initial, not a sentence end.
        if (wordBefore.length == 1 && wordBefore[0].isLetter()) return false
        return wordBefore.lowercase() !in ABBREVIATIONS
    }

    private val ABBREVIATIONS = setOf(
        "mr", "mrs", "ms", "dr", "prof", "st", "rev", "hon", "sr", "jr",
        "vs", "etc", "e.g", "i.e", "cf", "al", "no", "vol", "fig", "approx",
    )

    private val WHITESPACE = Regex("\\s+")
}
