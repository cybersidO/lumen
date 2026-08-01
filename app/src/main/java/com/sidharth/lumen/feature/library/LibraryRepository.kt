package com.sidharth.lumen.feature.library

import com.sidharth.lumen.core.books.BookStorage
import com.sidharth.lumen.core.database.BookDao
import com.sidharth.lumen.core.database.HighlightDao
import com.sidharth.lumen.core.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** What to do with a book's highlights when the book itself is deleted (§2.1). */
enum class HighlightDisposition {
    /** The default. Highlights survive, and reattach if the same file is imported again. */
    KEEP,
    DELETE,
}

@Singleton
class LibraryRepository @Inject constructor(
    private val bookDao: BookDao,
    private val highlightDao: HighlightDao,
    private val storage: BookStorage,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    val books: Flow<List<Book>> = bookDao.observeAll().map { entities ->
        entities.map { it.toBook() }
    }

    suspend fun highlightCount(book: Book): Int = withContext(ioDispatcher) {
        highlightDao.countForBook(book.contentHash)
    }

    suspend fun delete(book: Book, disposition: HighlightDisposition) = withContext(ioDispatcher) {
        if (disposition == HighlightDisposition.DELETE) {
            highlightDao.deleteForBook(book.contentHash)
        }
        // The row goes first. If file deletion fails (a locked file, say), the book is
        // still gone from the library rather than reappearing as a ghost entry.
        bookDao.deleteById(book.id)
        storage.deleteFilesFor(book.contentHash)
    }

    suspend fun markOpened(book: Book) = withContext(ioDispatcher) {
        bookDao.markOpened(book.id, System.currentTimeMillis())
    }
}
