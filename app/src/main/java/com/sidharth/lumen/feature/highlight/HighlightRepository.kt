package com.sidharth.lumen.feature.highlight

import com.sidharth.lumen.core.database.HighlightDao
import com.sidharth.lumen.core.database.HighlightEntity
import com.sidharth.lumen.core.database.HighlightTagEntity
import com.sidharth.lumen.core.database.NoteDao
import com.sidharth.lumen.core.database.NoteEntity
import com.sidharth.lumen.core.database.TagDao
import com.sidharth.lumen.core.database.TagEntity
import com.sidharth.lumen.core.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator
import javax.inject.Inject
import javax.inject.Singleton

/** A highlight as the reader works with it: entity plus its note and tags. */
data class Highlight(
    val id: Long,
    val bookContentHash: String,
    val color: HighlightColor,
    val quoteExact: String,
    val chapterTitle: String?,
    val progressFraction: Float,
    val locator: Locator?,
    val note: String?,
    val tags: List<String>,
)

@Singleton
class HighlightRepository @Inject constructor(
    private val highlightDao: HighlightDao,
    private val noteDao: NoteDao,
    private val tagDao: TagDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /** Lightweight stream for decoration rendering; notes/tags load on demand. */
    fun observeEntities(bookContentHash: String): Flow<List<HighlightEntity>> =
        highlightDao.observeForBook(bookContentHash)

    /**
     * Creates a highlight from the selection's locator. The three durable anchor parts
     * (§2.5) are extracted here: href, progression within the resource, and the
     * text-quote selector with ~32 chars of context clipped from Readium's before/after.
     */
    suspend fun create(
        bookContentHash: String,
        locator: Locator,
        color: HighlightColor,
        chapterTitle: String?,
    ): Long = withContext(ioDispatcher) {
        val text = locator.text
        highlightDao.insert(
            HighlightEntity(
                bookContentHash = bookContentHash,
                colorId = color.id,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                resourceHref = locator.href.toString(),
                resourceProgression = locator.locations.progression,
                locatorJson = locator.toJSON().toString(),
                quotePrefix = (text.before ?: "").takeLast(QUOTE_CONTEXT_CHARS),
                quoteExact = text.highlight ?: "",
                quoteSuffix = (text.after ?: "").take(QUOTE_CONTEXT_CHARS),
                chapterTitle = chapterTitle,
                progressFraction = (locator.locations.totalProgression ?: 0.0)
                    .toFloat()
                    .coerceIn(0f, 1f),
            ),
        )
    }

    suspend fun load(id: Long): Highlight? = withContext(ioDispatcher) {
        val entity = highlightDao.findById(id) ?: return@withContext null
        Highlight(
            id = entity.id,
            bookContentHash = entity.bookContentHash,
            color = HighlightColor.fromId(entity.colorId),
            quoteExact = entity.quoteExact,
            chapterTitle = entity.chapterTitle,
            progressFraction = entity.progressFraction,
            locator = entity.locatorJson?.let { json ->
                runCatching { Locator.fromJSON(JSONObject(json)) }.getOrNull()
            },
            note = noteDao.forHighlight(entity.id)?.body,
            tags = tagDao.forHighlight(entity.id).map { it.name },
        )
    }

    suspend fun recolor(id: Long, color: HighlightColor) = withContext(ioDispatcher) {
        highlightDao.findById(id)?.let { entity ->
            highlightDao.update(
                entity.copy(colorId = color.id, updatedAt = System.currentTimeMillis()),
            )
        }
    }

    /** Blank note text means "remove the note", so clearing a note needs no second path. */
    suspend fun setNote(highlightId: Long, body: String) = withContext(ioDispatcher) {
        val entity = highlightDao.findById(highlightId) ?: return@withContext
        val existing = noteDao.forHighlight(highlightId)
        val now = System.currentTimeMillis()
        when {
            body.isBlank() && existing != null -> noteDao.deleteById(existing.id)
            body.isBlank() -> Unit
            existing == null -> noteDao.insert(
                NoteEntity(
                    highlightId = highlightId,
                    bookContentHash = entity.bookContentHash,
                    body = body.trim(),
                    createdAt = now,
                    updatedAt = now,
                ),
            )

            else -> noteDao.update(existing.copy(body = body.trim(), updatedAt = now))
        }
    }

    /** Replaces the highlight's tag set. Names are trimmed; blanks dropped; case kept. */
    suspend fun setTags(highlightId: Long, names: List<String>) = withContext(ioDispatcher) {
        tagDao.unlinkAllFor(highlightId)
        names.map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEach { name ->
            val existing = tagDao.findByName(name)
            val tagId = existing?.id ?: tagDao.insert(TagEntity(name = name))
                .takeIf { it > 0 } ?: tagDao.findByName(name)?.id ?: return@forEach
            tagDao.link(HighlightTagEntity(highlightId = highlightId, tagId = tagId))
        }
    }

    /**
     * No FK cascades by design, so the cleanup is explicit: the note and tag links go
     * with the highlight.
     */
    suspend fun delete(id: Long) = withContext(ioDispatcher) {
        noteDao.deleteForHighlight(id)
        tagDao.unlinkAllFor(id)
        highlightDao.deleteById(id)
    }

    private companion object {
        /** §2.5: ~32 chars of context each side. */
        const val QUOTE_CONTEXT_CHARS = 32
    }
}
