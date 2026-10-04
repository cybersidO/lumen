package com.sidharth.lumen.feature.reader

import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.search.SearchIterator
import org.readium.r2.shared.publication.services.search.isSearchable
import org.readium.r2.shared.publication.services.search.search

/** One hit, with enough context to recognise it in a list. */
data class SearchHit(
    val locator: Locator,
    /** The matched phrase. */
    val highlight: String,
    val before: String,
    val after: String,
    /** Chapter title where known, for grouping. */
    val chapter: String?,
)

sealed interface BookSearchState {
    data object Idle : BookSearchState
    data object Searching : BookSearchState
    data class Results(
        val query: String,
        val hits: List<SearchHit>,
        /** False once the iterator is exhausted, so the UI can stop offering "more". */
        val hasMore: Boolean,
        val loadingMore: Boolean = false,
    ) : BookSearchState {
        val isEmpty: Boolean get() = hits.isEmpty()
    }
    data class Failed(val message: String) : BookSearchState
}

/**
 * Full-text search within the open book (M13).
 *
 * Readium returns results through a [SearchIterator] a page at a time rather than all at
 * once, which matters for a long book: the first handful appear immediately instead of
 * the reader waiting while the whole publication is scanned. [loadMore] pulls the next
 * page, so the list grows as it is scrolled.
 *
 * The iterator holds resources and must be closed; [reset] does that and is called when
 * the query changes or the reader leaves.
 */
class BookSearch {

    private var iterator: SearchIterator? = null

    fun isSearchable(publication: Publication): Boolean = publication.isSearchable

    /** Starts a new search, returning the first page of hits. */
    suspend fun start(publication: Publication, query: String): BookSearchState {
        reset()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return BookSearchState.Idle
        if (!publication.isSearchable) return BookSearchState.Failed(ERROR_UNSUPPORTED)

        val created = runCatching { publication.search(trimmed) }.getOrNull()
            ?: return BookSearchState.Failed(ERROR_FAILED)
        iterator = created
        return page(trimmed, emptyList())
    }

    /** Appends the next page to [current]. */
    suspend fun loadMore(current: BookSearchState.Results): BookSearchState =
        page(current.query, current.hits)

    private suspend fun page(query: String, existing: List<SearchHit>): BookSearchState {
        val active = iterator ?: return BookSearchState.Results(query, existing, hasMore = false)
        // Readium's Try is not Kotlin's Result: it offers getOrNull/failureOrNull.
        val attempt = runCatching { active.next() }.getOrNull()
            ?: return BookSearchState.Failed(ERROR_FAILED)
        if (attempt.isFailure) return BookSearchState.Failed(ERROR_FAILED)

        // A null collection means the iterator is spent — the end, not an error.
        val collection = attempt.getOrNull()
            ?: run {
                reset()
                return BookSearchState.Results(query, existing, hasMore = false)
            }
        val hits = existing + collection.locators.map { locator -> locator.toHit() }
        return BookSearchState.Results(query, hits, hasMore = true)
    }

    fun reset() {
        iterator?.close()
        iterator = null
    }

    private fun Locator.toHit() = SearchHit(
        locator = this,
        highlight = text.highlight.orEmpty(),
        before = text.before.orEmpty(),
        after = text.after.orEmpty(),
        chapter = title?.takeIf { it.isNotBlank() },
    )

    private companion object {
        const val ERROR_UNSUPPORTED = "This book can't be searched — Lumen couldn't read its text."
        const val ERROR_FAILED = "The search stopped unexpectedly. Try a shorter phrase."
    }
}
