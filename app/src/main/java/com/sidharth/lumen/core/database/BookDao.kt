package com.sidharth.lumen.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    /** Unordered on purpose — sorting is a pure function over this list, so it is testable. */
    @Query("SELECT * FROM books")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE content_hash = :contentHash LIMIT 1")
    suspend fun findByContentHash(contentHash: String): BookEntity?

    @Query("SELECT * FROM books WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): BookEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(book: BookEntity): Long

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE books SET last_opened_at = :timestamp WHERE id = :id")
    suspend fun markOpened(id: Long, timestamp: Long)
}

@Dao
interface HighlightDao {

    @Query("SELECT COUNT(*) FROM highlights WHERE book_content_hash = :contentHash")
    suspend fun countForBook(contentHash: String): Int

    @Query("DELETE FROM highlights WHERE book_content_hash = :contentHash")
    suspend fun deleteForBook(contentHash: String)

    @Insert
    suspend fun insert(highlight: HighlightEntity): Long

    @Query("SELECT * FROM highlights WHERE book_content_hash = :contentHash ORDER BY progress_fraction")
    suspend fun forBook(contentHash: String): List<HighlightEntity>
}
