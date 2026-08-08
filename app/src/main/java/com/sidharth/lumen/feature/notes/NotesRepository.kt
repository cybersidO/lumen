package com.sidharth.lumen.feature.notes

import com.sidharth.lumen.core.database.BookDao
import com.sidharth.lumen.core.database.NoteListRow
import com.sidharth.lumen.core.database.NotesDao
import com.sidharth.lumen.core.di.IoDispatcher
import com.sidharth.lumen.feature.highlight.HighlightColor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Which kinds of entry the list is showing (§2.6's "type" filter). */
enum class EntryKind { HIGHLIGHT, NOTE, LOOKUP }

data class NotesFilter(
    val query: String = "",
    val bookHash: String? = null,
    val color: HighlightColor? = null,
    val tagId: Long? = null,
    val kinds: Set<EntryKind> = EntryKind.entries.toSet(),
    val from: Long = Long.MIN_VALUE / 2,
    val to: Long = Long.MAX_VALUE / 2,
) {
    val isActive: Boolean
        get() = query.isNotBlank() || bookHash != null || color != null || tagId != null ||
            kinds.size != EntryKind.entries.size ||
            from != Long.MIN_VALUE / 2 || to != Long.MAX_VALUE / 2
}

@Singleton
class NotesRepository @Inject constructor(
    private val notesDao: NotesDao,
    private val bookDao: BookDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    fun observeEntries(filter: NotesFilter): Flow<List<NoteListRow>> {
        val kinds = filter.kinds.map { it.name }
        return notesDao.observeEntries(
            query = ftsQuery(filter.query),
            rawQuery = filter.query.trim().takeIf { it.isNotBlank() },
            bookHash = filter.bookHash,
            colorId = filter.color?.id,
            tagId = filter.tagId,
            from = filter.from,
            to = filter.to,
            // Room cannot bind an empty IN list, so a "no filter" sentinel is passed
            // alongside; the query short-circuits on it.
            kinds = if (kinds.size == EntryKind.entries.size) null else "filtered",
            kindList = kinds.ifEmpty { listOf("NONE") },
        )
    }

    fun observeBookFacets() = notesDao.observeBookFacets()

    fun observeTags() = notesDao.observeTags()

    /**
     * Turns what the user typed into an FTS4 MATCH expression.
     *
     * Everything is quoted as a phrase and a prefix `*` appended, so typing half a word
     * finds it and stray punctuation (`"`, `*`, `-`) can never produce a syntax error that
     * would surface as an empty result list with no explanation.
     */
    internal fun ftsQuery(raw: String): String? {
        val cleaned = raw.trim()
        if (cleaned.isBlank()) return null
        val terms = cleaned
            .split(WHITESPACE)
            .map { it.filter(Char::isLetterOrDigit) }
            .filter { it.isNotEmpty() }
        if (terms.isEmpty()) return null
        return terms.joinToString(" ") { "\"$it\"*" }
    }

    // --- export -----------------------------------------------------------------------

    /** Assembles the export model; the formatters themselves stay pure. */
    suspend fun exportModel(): Pair<List<ExportBook>, List<ExportLookup>> =
        withContext(ioDispatcher) {
            val books = bookDao.observeAllOnce()
            val exportBooks = books.map { book ->
                val rows = notesDao.highlightsForExport(book.contentHash)
                ExportBook(
                    title = book.title,
                    author = book.author,
                    contentHash = book.contentHash,
                    highlights = rows.map { row ->
                        ExportHighlight(
                            quote = row.primaryText,
                            note = row.secondaryText,
                            tags = notesDao.tagNamesFor(row.entryId),
                            colorName = HighlightColor.fromId(row.colorId ?: 0).displayName,
                            chapterTitle = row.chapterTitle,
                            progressFraction = row.progressFraction ?: 0f,
                            createdAt = row.createdAt,
                        )
                    },
                )
            }

            val titlesByHash = books.associate { it.contentHash to it.title }
            val lookups = notesDao.allLookups().map { event ->
                ExportLookup(
                    word = event.word,
                    lemma = event.lemma,
                    bookTitle = event.bookContentHash?.let(titlesByHash::get),
                    chapterTitle = event.chapterTitle,
                    sentence = event.sentence,
                    found = event.found,
                    createdAt = event.createdAt,
                )
            }

            exportBooks to lookups
        }

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
