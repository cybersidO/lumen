package com.sidharth.lumen.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
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

    /**
     * The reading position. Written on every page turn, so it is one statement rather than
     * a read-modify-write — losing a page on a crash is acceptable, corrupting the row is
     * not.
     */
    @Query(
        """
        UPDATE books
        SET current_locator_json = :locatorJson, progress_fraction = :progressFraction
        WHERE id = :id
        """,
    )
    suspend fun saveProgress(id: Long, locatorJson: String, progressFraction: Float)
}

@Dao
interface HighlightDao {

    @Query("SELECT COUNT(*) FROM highlights WHERE book_content_hash = :contentHash")
    suspend fun countForBook(contentHash: String): Int

    @Query("DELETE FROM highlights WHERE book_content_hash = :contentHash")
    suspend fun deleteForBook(contentHash: String)

    @Insert
    suspend fun insert(highlight: HighlightEntity): Long

    @Update
    suspend fun update(highlight: HighlightEntity)

    @Query("DELETE FROM highlights WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM highlights WHERE id = :id")
    suspend fun findById(id: Long): HighlightEntity?

    @Query("SELECT * FROM highlights WHERE book_content_hash = :contentHash ORDER BY progress_fraction")
    suspend fun forBook(contentHash: String): List<HighlightEntity>

    /** The reader keeps decorations in step with this. */
    @Query("SELECT * FROM highlights WHERE book_content_hash = :contentHash ORDER BY progress_fraction")
    fun observeForBook(contentHash: String): Flow<List<HighlightEntity>>
}

@Dao
interface NoteDao {

    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM notes WHERE highlight_id = :highlightId LIMIT 1")
    suspend fun forHighlight(highlightId: Long): NoteEntity?

    @Query("DELETE FROM notes WHERE highlight_id = :highlightId")
    suspend fun deleteForHighlight(highlightId: Long)

    @Query("DELETE FROM notes WHERE book_content_hash = :contentHash AND highlight_id IS NOT NULL")
    suspend fun deleteHighlightNotesForBook(contentHash: String)
}

@Dao
interface TagDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: TagEntity): Long

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): TagEntity?

    @Query("SELECT * FROM tags ORDER BY name")
    suspend fun all(): List<TagEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun link(link: HighlightTagEntity)

    @Query("DELETE FROM highlight_tags WHERE highlight_id = :highlightId")
    suspend fun unlinkAllFor(highlightId: Long)

    @Query(
        """
        SELECT tags.* FROM tags
        INNER JOIN highlight_tags ON highlight_tags.tag_id = tags.id
        WHERE highlight_tags.highlight_id = :highlightId
        ORDER BY tags.name
        """,
    )
    suspend fun forHighlight(highlightId: Long): List<TagEntity>
}
