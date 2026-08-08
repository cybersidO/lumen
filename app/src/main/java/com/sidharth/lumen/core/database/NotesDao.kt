package com.sidharth.lumen.core.database

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * One row of the cross-book list (§2.6). Highlights, standalone notes and looked-up words
 * are different tables, so the query unions them into a single shape the UI can sort and
 * page through without caring which it came from.
 */
data class NoteListRow(
    val kind: String,
    /** Row id within its own table — with [kind], this identifies the entry. */
    val entryId: Long,
    val bookContentHash: String?,
    val bookTitle: String?,
    /** Highlight quote, note body, or the looked-up word. */
    val primaryText: String,
    /** The note attached to a highlight, or the sentence a word was found in. */
    val secondaryText: String?,
    val colorId: Int?,
    val chapterTitle: String?,
    val progressFraction: Float?,
    val createdAt: Long,
)

/** Distinct values for the filter chips. */
data class BookFacet(val contentHash: String, val title: String, val entryCount: Int)

@Dao
interface NotesDao {

    /**
     * The whole notes database as one list.
     *
     * Filters are all optional and applied with the `:param IS NULL OR ...` idiom so a
     * single query serves every combination — no string building, so no chance of a
     * malformed clause reaching SQLite.
     *
     * [query] is matched through the FTS tables for highlights and notes; looked-up words
     * are matched on the word and its sentence, which are short enough not to need an
     * index of their own.
     */
    @Query(
        """
        SELECT * FROM (
            SELECT
                'HIGHLIGHT' AS kind,
                h.id AS entryId,
                h.book_content_hash AS bookContentHash,
                b.title AS bookTitle,
                h.quote_exact AS primaryText,
                (SELECT n.body FROM notes n WHERE n.highlight_id = h.id LIMIT 1) AS secondaryText,
                h.color_id AS colorId,
                h.chapter_title AS chapterTitle,
                h.progress_fraction AS progressFraction,
                h.created_at AS createdAt
            FROM highlights h
            LEFT JOIN books b ON b.content_hash = h.book_content_hash
            WHERE (:query IS NULL OR h.id IN (
                    SELECT rowid FROM highlights_fts WHERE highlights_fts MATCH :query
                  ) OR h.id IN (
                    SELECT n.highlight_id FROM notes n
                    WHERE n.rowid IN (SELECT rowid FROM notes_fts WHERE notes_fts MATCH :query)
                  ))
              AND (:bookHash IS NULL OR h.book_content_hash = :bookHash)
              AND (:colorId IS NULL OR h.color_id = :colorId)
              AND (:tagId IS NULL OR h.id IN (
                    SELECT ht.highlight_id FROM highlight_tags ht WHERE ht.tag_id = :tagId
                  ))
              AND h.created_at BETWEEN :from AND :to
              AND (:kinds IS NULL OR 'HIGHLIGHT' IN (:kindList))

            UNION ALL

            SELECT
                'NOTE' AS kind,
                n.id AS entryId,
                n.book_content_hash AS bookContentHash,
                b.title AS bookTitle,
                n.body AS primaryText,
                NULL AS secondaryText,
                NULL AS colorId,
                NULL AS chapterTitle,
                NULL AS progressFraction,
                n.created_at AS createdAt
            FROM notes n
            LEFT JOIN books b ON b.content_hash = n.book_content_hash
            WHERE n.highlight_id IS NULL
              AND (:query IS NULL OR n.rowid IN (
                    SELECT rowid FROM notes_fts WHERE notes_fts MATCH :query
                  ))
              AND (:bookHash IS NULL OR n.book_content_hash = :bookHash)
              AND :colorId IS NULL
              AND :tagId IS NULL
              AND n.created_at BETWEEN :from AND :to
              AND (:kinds IS NULL OR 'NOTE' IN (:kindList))

            UNION ALL

            SELECT
                'LOOKUP' AS kind,
                l.id AS entryId,
                l.book_content_hash AS bookContentHash,
                b.title AS bookTitle,
                l.word AS primaryText,
                l.sentence AS secondaryText,
                NULL AS colorId,
                l.chapter_title AS chapterTitle,
                NULL AS progressFraction,
                l.created_at AS createdAt
            FROM lookup_events l
            LEFT JOIN books b ON b.content_hash = l.book_content_hash
            WHERE (:query IS NULL
                   OR l.word LIKE '%' || :rawQuery || '%'
                   OR l.sentence LIKE '%' || :rawQuery || '%')
              AND (:bookHash IS NULL OR l.book_content_hash = :bookHash)
              AND :colorId IS NULL
              AND :tagId IS NULL
              AND l.created_at BETWEEN :from AND :to
              AND (:kinds IS NULL OR 'LOOKUP' IN (:kindList))
        )
        ORDER BY createdAt DESC
        LIMIT :limit
        """,
    )
    fun observeEntries(
        query: String?,
        rawQuery: String?,
        bookHash: String?,
        colorId: Int?,
        tagId: Long?,
        from: Long,
        to: Long,
        kinds: String?,
        kindList: List<String>,
        limit: Int = 500,
    ): Flow<List<NoteListRow>>

    @Query(
        """
        SELECT b.content_hash AS contentHash, b.title AS title, COUNT(h.id) AS entryCount
        FROM books b
        LEFT JOIN highlights h ON h.book_content_hash = b.content_hash
        GROUP BY b.content_hash, b.title
        ORDER BY b.title
        """,
    )
    fun observeBookFacets(): Flow<List<BookFacet>>

    @Query("SELECT * FROM tags ORDER BY name")
    fun observeTags(): Flow<List<TagEntity>>

    /** Everything for one book, oldest first — the shape the Markdown export wants. */
    @Query(
        """
        SELECT
            'HIGHLIGHT' AS kind,
            h.id AS entryId,
            h.book_content_hash AS bookContentHash,
            b.title AS bookTitle,
            h.quote_exact AS primaryText,
            (SELECT n.body FROM notes n WHERE n.highlight_id = h.id LIMIT 1) AS secondaryText,
            h.color_id AS colorId,
            h.chapter_title AS chapterTitle,
            h.progress_fraction AS progressFraction,
            h.created_at AS createdAt
        FROM highlights h
        LEFT JOIN books b ON b.content_hash = h.book_content_hash
        WHERE h.book_content_hash = :contentHash
        ORDER BY h.progress_fraction, h.created_at
        """,
    )
    suspend fun highlightsForExport(contentHash: String): List<NoteListRow>

    @Query("SELECT * FROM highlights ORDER BY book_content_hash, progress_fraction")
    suspend fun allHighlights(): List<HighlightEntity>

    @Query("SELECT * FROM lookup_events ORDER BY created_at")
    suspend fun allLookups(): List<LookupEventEntity>

    @Query(
        """
        SELECT t.name FROM tags t
        INNER JOIN highlight_tags ht ON ht.tag_id = t.id
        WHERE ht.highlight_id = :highlightId
        ORDER BY t.name
        """,
    )
    suspend fun tagNamesFor(highlightId: Long): List<String>
}
