package com.sidharth.lumen.feature.bookmark

import com.sidharth.lumen.core.database.BookmarkDao
import com.sidharth.lumen.core.database.BookmarkEntity
import com.sidharth.lumen.core.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator
import javax.inject.Inject
import javax.inject.Singleton

/** A saved place, as the reader's list shows it. */
data class Bookmark(
    val id: Long,
    val locator: Locator,
    val page: Int?,
    val chapterTitle: String?,
    val excerpt: String?,
    val createdAt: Long,
)

@Singleton
class BookmarkRepository @Inject constructor(
    private val dao: BookmarkDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    fun observe(bookContentHash: String): Flow<List<Bookmark>> =
        dao.observeForBook(bookContentHash).map { rows -> rows.mapNotNull(::toBookmark) }

    /**
     * Adds or removes the bookmark on the page being read, and says which it did.
     *
     * One call for both directions because the reader has one button: the state of that
     * button *is* whether a bookmark exists here, so deciding it anywhere else would mean
     * two sources of truth for the same star.
     */
    suspend fun toggle(
        bookContentHash: String,
        locator: Locator,
        page: Int?,
        chapterTitle: String?,
    ): Boolean = withContext(ioDispatcher) {
        val href = locator.href.toString()
        val existing = dao.findAt(bookContentHash, href, page)
        if (existing != null) {
            dao.delete(existing.id)
            return@withContext false
        }
        dao.insert(
            BookmarkEntity(
                bookContentHash = bookContentHash,
                locatorJson = locator.toJSON().toString(),
                resourceHref = href,
                page = page,
                chapterTitle = chapterTitle,
                excerpt = excerptFrom(locator),
                createdAt = System.currentTimeMillis(),
            ),
        )
        true
    }

    suspend fun delete(id: Long) = withContext(ioDispatcher) { dao.delete(id) }

    /**
     * The first words at the saved place, so the list reads as places rather than page
     * numbers. Readium fills the locator's text when it can; when it cannot, the list
     * falls back to the chapter and page, which is why this may be null.
     */
    private fun excerptFrom(locator: Locator): String? {
        val text = listOfNotNull(locator.text.highlight, locator.text.after)
            .joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return text.takeIf { it.isNotBlank() }?.take(EXCERPT_CHARS)
    }

    private fun toBookmark(entity: BookmarkEntity): Bookmark? {
        val locator = runCatching { Locator.fromJSON(JSONObject(entity.locatorJson)) }
            .getOrNull() ?: return null
        return Bookmark(
            id = entity.id,
            locator = locator,
            page = entity.page,
            chapterTitle = entity.chapterTitle,
            excerpt = entity.excerpt,
            createdAt = entity.createdAt,
        )
    }

    private companion object {
        const val EXCERPT_CHARS = 120
    }
}
