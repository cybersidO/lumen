package com.sidharth.lumen.feature.reader

/**
 * One resource of the book, reduced to what counting pages needs.
 *
 * [title] is the table-of-contents entry pointing at this resource, when there is one.
 * It matters more than the file name: real EPUBs name their files `split_014.xhtml`,
 * so the only readable clue to what a resource *is* usually comes from the TOC.
 */
data class ResourcePages(
    val href: String,
    val pageCount: Int,
    val title: String?,
)

/**
 * How long a book is, and how much of that length is notes.
 *
 * The distinction is the point: a history with eighty pages of endnotes is not a
 * four-hundred-page read, and a progress bar that counts the notes as reading says you
 * have further to go than you do.
 */
data class BookPages(
    val total: Int,
    val notes: Int,
) {
    val mainText: Int get() = (total - notes).coerceAtLeast(0)

    /** Only worth showing when the notes are a real section and not the whole book. */
    val hasNotes: Boolean get() = notes > 0 && mainText > 0

    companion object {
        val Empty = BookPages(total = 0, notes = 0)
    }
}

/**
 * Counts pages from Readium's synthetic positions.
 *
 * These are not the page numbers of any printed edition — Readium divides each resource
 * into roughly 1024-character slices — but they are *stable*: unlike a screen count they
 * do not change when the font size does, so "page 120 of 354" means the same thing
 * tomorrow at a different size. That stability is the reason to count them at all.
 *
 * Deliberately free of Android and Readium types so the rules can be tested without a
 * device, in the same spirit as [com.sidharth.lumen.feature.reader.gesture.GestureGeometry].
 */
object PageCounter {

    /**
     * Words that mark a resource as notes.
     *
     * Plurals only, and never a bare "note": a chapter titled "A Note on the Translation"
     * is front matter, not the endnote apparatus, and counting it would quietly inflate
     * the number this whole feature exists to report.
     */
    private val NOTE_WORDS = setOf(
        "notes",
        "footnote",
        "footnotes",
        "endnote",
        "endnotes",
        "rearnote",
        "rearnotes",
    )

    fun count(resources: List<ResourcePages>): BookPages = BookPages(
        total = resources.sumOf { it.pageCount },
        notes = resources.filter { isNotes(it.href, it.title) }.sumOf { it.pageCount },
    )

    /**
     * Whether a resource holds notes, judged by its TOC title first and its file name
     * second.
     *
     * A heuristic, and knowingly so: EPUB has no required marker for "this file is the
     * endnotes". Books that put their notes in popup `aside`s inside each chapter have no
     * separate notes resource at all, and for those this correctly reports none — those
     * notes occupy no pages of their own.
     */
    fun isNotes(href: String, title: String?): Boolean =
        containsNoteWord(title) || containsNoteWord(fileName(href))

    private fun containsNoteWord(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        return text.lowercase()
            .split(Regex("[^a-z]+"))
            .any { it in NOTE_WORDS }
    }

    /**
     * The last path segment, without fragment or query — the only part of an href that
     * ever carries a human-chosen word.
     */
    private fun fileName(href: String): String = href
        .substringBefore('#')
        .substringBefore('?')
        .substringAfterLast('/')
}
