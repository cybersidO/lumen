package com.sidharth.lumen.feature.library

import com.sidharth.lumen.core.books.BookFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryQueryTest {

    private var nextId = 1L

    private fun book(
        title: String,
        author: String? = null,
        addedAt: Long = 0,
        lastOpenedAt: Long? = null,
        progress: Float = 0f,
    ) = Book(
        id = nextId++,
        contentHash = "hash-$title",
        title = title,
        author = author,
        format = BookFormat.EPUB,
        filePath = "/books/$title.epub",
        coverPath = null,
        addedAt = addedAt,
        lastOpenedAt = lastOpenedAt,
        progressFraction = progress,
    )

    private fun titles(books: List<Book>) = books.map(Book::title)

    // --- sorting -----------------------------------------------------------------

    @Test
    fun `recent puts the most recently opened first`() {
        val old = book("Old", addedAt = 100, lastOpenedAt = 200)
        val fresh = book("Fresh", addedAt = 100, lastOpenedAt = 900)
        val never = book("Never", addedAt = 500)

        assertEquals(
            listOf("Fresh", "Never", "Old"),
            titles(LibraryQuery.apply(listOf(old, fresh, never), "", LibrarySort.RECENT)),
        )
    }

    @Test
    fun `an unopened book falls back to when it was added`() {
        val added = book("JustAdded", addedAt = 1_000)
        val readLongAgo = book("ReadLongAgo", addedAt = 10, lastOpenedAt = 20)

        assertEquals(
            listOf("JustAdded", "ReadLongAgo"),
            titles(LibraryQuery.apply(listOf(readLongAgo, added), "", LibrarySort.RECENT)),
        )
    }

    /** A shelf sorts "The Odyssey" under O. So does this. */
    @Test
    fun `title sort ignores a leading article`() {
        val books = listOf(book("The Odyssey"), book("A Passage to India"), book("Beloved"))

        assertEquals(
            listOf("Beloved", "The Odyssey", "A Passage to India"),
            titles(LibraryQuery.apply(books, "", LibrarySort.TITLE)),
        )
    }

    @Test
    fun `title sort is case insensitive`() {
        val books = listOf(book("zebra"), book("Apple"), book("banana"))

        assertEquals(
            listOf("Apple", "banana", "zebra"),
            titles(LibraryQuery.apply(books, "", LibrarySort.TITLE)),
        )
    }

    @Test
    fun `accented titles sort with their unaccented neighbours`() {
        val books = listOf(book("Zola"), book("Émile"), book("Faust"))

        assertEquals(
            listOf("Émile", "Faust", "Zola"),
            titles(LibraryQuery.apply(books, "", LibrarySort.TITLE)),
        )
    }

    @Test
    fun `books without an author sort last rather than first`() {
        val books = listOf(
            book("Anonymous", author = null),
            book("Middlemarch", author = "Eliot"),
            book("Blank", author = "  "),
        )

        assertEquals(
            listOf("Middlemarch", "Anonymous", "Blank"),
            titles(LibraryQuery.apply(books, "", LibrarySort.AUTHOR)),
        )
    }

    // --- searching ---------------------------------------------------------------

    @Test
    fun `an empty query keeps everything`() {
        val books = listOf(book("One"), book("Two"))
        assertEquals(2, LibraryQuery.apply(books, "   ", LibrarySort.TITLE).size)
    }

    @Test
    fun `search matches title or author, case insensitively`() {
        val books = listOf(
            book("Wuthering Heights", author = "Emily Brontë"),
            book("Middlemarch", author = "George Eliot"),
        )

        assertEquals(
            listOf("Wuthering Heights"),
            titles(LibraryQuery.apply(books, "WUTHER", LibrarySort.TITLE)),
        )
        assertEquals(
            listOf("Middlemarch"),
            titles(LibraryQuery.apply(books, "eliot", LibrarySort.TITLE)),
        )
    }

    /** Typing "bronte" on a phone keyboard must still find "Brontë". */
    @Test
    fun `search ignores accents in both the query and the data`() {
        val books = listOf(book("Wuthering Heights", author = "Emily Brontë"))

        assertEquals(1, LibraryQuery.apply(books, "bronte", LibrarySort.TITLE).size)
        assertEquals(1, LibraryQuery.apply(books, "Brontë", LibrarySort.TITLE).size)
    }

    @Test
    fun `search matches inside a word, not just at the start`() {
        val books = listOf(book("Middlemarch"))
        assertEquals(1, LibraryQuery.apply(books, "march", LibrarySort.TITLE).size)
    }

    @Test
    fun `a query that matches nothing returns empty`() {
        val books = listOf(book("Middlemarch", author = "George Eliot"))
        assertEquals(emptyList<Book>(), LibraryQuery.apply(books, "dickens", LibrarySort.TITLE))
    }

    // --- progress ----------------------------------------------------------------

    @Test
    fun `progress percent rounds down and stays in range`() {
        assertEquals(0, book("a", progress = 0f).progressPercent)
        assertEquals(49, book("b", progress = 0.499f).progressPercent)
        assertEquals(100, book("c", progress = 1f).progressPercent)
        // Defensive: a corrupt or out-of-range value must not render as "-20% read".
        assertEquals(0, book("d", progress = -0.2f).progressPercent)
        assertEquals(100, book("e", progress = 4f).progressPercent)
    }
}
