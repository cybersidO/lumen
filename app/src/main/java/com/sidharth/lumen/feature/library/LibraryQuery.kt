package com.sidharth.lumen.feature.library

import java.text.Normalizer
import java.util.Locale

enum class LibrarySort {
    /** Most recently opened first; never-opened books fall back to when they were added. */
    RECENT,
    TITLE,
    AUTHOR,
}

/**
 * Sorting and searching, kept as a pure function over the full list rather than pushed
 * into SQL. A personal library is tens of books, not thousands, so the cost is nil and
 * the behaviour becomes directly testable — including the parts that are easy to get
 * wrong, like accents and leading articles.
 */
object LibraryQuery {

    fun apply(books: List<Book>, query: String, sort: LibrarySort): List<Book> =
        books.filter { it.matches(query) }.sortedWith(comparatorFor(sort))

    private fun comparatorFor(sort: LibrarySort): Comparator<Book> = when (sort) {
        LibrarySort.RECENT ->
            compareByDescending<Book> { it.recencyTimestamp }
                .thenBy { it.sortableTitle }

        LibrarySort.TITLE ->
            compareBy<Book> { it.sortableTitle }
                .thenByDescending { it.recencyTimestamp }

        // Books with no author sort last rather than being hidden at the top under "".
        LibrarySort.AUTHOR ->
            compareBy<Book> { it.author.isNullOrBlank() }
                .thenBy { it.author?.let(::normalize).orEmpty() }
                .thenBy { it.sortableTitle }
    }

    private fun Book.matches(query: String): Boolean {
        val needle = normalize(query)
        if (needle.isEmpty()) return true
        return normalize(title).contains(needle) ||
            author?.let { normalize(it).contains(needle) } == true
    }

    private val Book.sortableTitle: String get() = normalize(title).stripLeadingArticle()

    /**
     * Case- and accent-insensitive, so searching "bronte" finds "Brontë" and sorting
     * doesn't scatter accented titles to the end of the alphabet.
     */
    internal fun normalize(value: String): String =
        Normalizer.normalize(value.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")

    /** "The Odyssey" sorts under O, the way it would on a shelf. */
    private fun String.stripLeadingArticle(): String {
        LEADING_ARTICLES.forEach { article ->
            if (startsWith(article)) return substring(article.length)
        }
        return this
    }

    private val COMBINING_MARKS = "\\p{Mn}+".toRegex()
    private val LEADING_ARTICLES = listOf("the ", "a ", "an ")
}
