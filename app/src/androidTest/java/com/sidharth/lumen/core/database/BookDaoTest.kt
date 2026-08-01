package com.sidharth.lumen.core.database

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookDaoTest {

    private lateinit var database: LumenDatabase
    private lateinit var bookDao: BookDao
    private lateinit var highlightDao: HighlightDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LumenDatabase::class.java,
        ).build()
        bookDao = database.bookDao()
        highlightDao = database.highlightDao()
    }

    @After
    fun tearDown() = database.close()

    private fun book(hash: String, title: String = "Title") = BookEntity(
        contentHash = hash,
        title = title,
        author = "Author",
        format = "EPUB",
        filePath = "/books/$hash.epub",
        coverPath = null,
        addedAt = 1_000,
        lastOpenedAt = null,
    )

    @Test
    fun insertedBookIsFoundByContentHash() = runTest {
        bookDao.insert(book("abc123"))

        val found = bookDao.findByContentHash("abc123")
        assertNotNull(found)
        assertEquals("Title", found!!.title)
        assertNull(bookDao.findByContentHash("nope"))
    }

    /**
     * The unique index is what makes re-import a no-op rather than a duplicate shelf
     * entry. If this ever stops throwing, the importer's dedupe check is the only thing
     * standing between the user and a library full of copies.
     */
    @Test
    fun duplicateContentHashIsRejected() = runTest {
        bookDao.insert(book("same"))
        assertThrows(SQLiteConstraintException::class.java) {
            kotlinx.coroutines.runBlocking { bookDao.insert(book("same", title = "Other")) }
        }
    }

    @Test
    fun observeAllEmitsInsertedBooks() = runTest {
        bookDao.insert(book("one", "One"))
        bookDao.insert(book("two", "Two"))

        val titles = bookDao.observeAll().first().map { it.title }.sorted()
        assertEquals(listOf("One", "Two"), titles)
    }

    @Test
    fun markOpenedStampsTheTimestamp() = runTest {
        val id = bookDao.insert(book("opened"))
        bookDao.markOpened(id, 4_242)

        assertEquals(4_242L, bookDao.findById(id)!!.lastOpenedAt)
    }

    // --- the deletion contract from §2.1 -----------------------------------------

    private fun highlight(bookHash: String) = HighlightEntity(
        bookContentHash = bookHash,
        colorId = 0,
        createdAt = 1,
        updatedAt = 1,
        resourceHref = "/chapter1.xhtml",
        textPositionStart = 10,
        textPositionEnd = 20,
        quotePrefix = "before ",
        quoteExact = "the quote",
        quoteSuffix = " after",
        chapterTitle = "Chapter 1",
        progressFraction = 0.25f,
    )

    @Test
    fun deletingABookLeavesItsHighlightsBehindByDefault() = runTest {
        val id = bookDao.insert(book("keepme"))
        highlightDao.insert(highlight("keepme"))
        highlightDao.insert(highlight("keepme"))

        bookDao.deleteById(id)

        // No foreign key, so nothing cascades: the highlights outlive the book and are
        // waiting to be reunited with it on re-import.
        assertEquals(2, highlightDao.countForBook("keepme"))
    }

    @Test
    fun highlightsCanBeDeletedWithTheBookWhenAsked() = runTest {
        val id = bookDao.insert(book("dropme"))
        highlightDao.insert(highlight("dropme"))

        highlightDao.deleteForBook("dropme")
        bookDao.deleteById(id)

        assertEquals(0, highlightDao.countForBook("dropme"))
    }

    @Test
    fun reimportingTheSameFileReunitesItWithOrphanedHighlights() = runTest {
        val id = bookDao.insert(book("stable-hash"))
        highlightDao.insert(highlight("stable-hash"))
        bookDao.deleteById(id)

        // Same bytes, new row id, different file path — the highlights still belong to it.
        val newId = bookDao.insert(book("stable-hash").copy(filePath = "/books/elsewhere.epub"))

        assertEquals(1, highlightDao.countForBook("stable-hash"))
        assertEquals("stable-hash", bookDao.findById(newId)!!.contentHash)
    }

    @Test
    fun highlightCountsAreScopedToOneBook() = runTest {
        bookDao.insert(book("a"))
        bookDao.insert(book("b"))
        highlightDao.insert(highlight("a"))

        assertEquals(1, highlightDao.countForBook("a"))
        assertEquals(0, highlightDao.countForBook("b"))
    }
}
