package com.sidharth.lumen.core.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** What a review card was made from. §4 calls these sourceType / sourceId. */
object ReviewSourceType {
    const val LOOKUP = "LOOKUP"
    const val HIGHLIGHT = "HIGHLIGHT"
}

/**
 * One card in the review queue (§4's ReviewItem).
 *
 * The unique index on (source_type, source_id) is what makes seeding idempotent: the
 * queue is topped up by inserting every eligible word and highlight with IGNORE, so a
 * word looked up five times still yields one card and its schedule is never reset by a
 * later lookup.
 */
@Entity(
    tableName = "review_items",
    indices = [
        Index(value = ["source_type", "source_id"], unique = true),
        Index(value = ["due_at"]),
    ],
)
data class ReviewItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "source_type")
    val sourceType: String,

    /** Row id in `lookup_events` or `highlights`, depending on [sourceType]. */
    @ColumnInfo(name = "source_id")
    val sourceId: Long,

    /** How far up the interval ladder; see ReviewSchedule.INTERVAL_DAYS. */
    @ColumnInfo(name = "interval_index")
    val intervalIndex: Int,

    @ColumnInfo(name = "due_at")
    val dueAt: Long,

    /** KNOWN / AGAIN, or null before the card has ever been answered. */
    @ColumnInfo(name = "last_result")
    val lastResult: String?,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)

/** A card with the text it needs, joined from whichever table it came from. */
data class ReviewCard(
    val itemId: Long,
    val sourceType: String,
    val sourceId: Long,
    val intervalIndex: Int,
    val dueAt: Long,
    /** The word, or the highlight's quote. */
    val prompt: String,
    /** The sentence a word appeared in, or the note attached to a highlight. */
    val context: String?,
    val bookTitle: String?,
    val chapterTitle: String?,
)

@Dao
interface ReviewDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoring(items: List<ReviewItemEntity>)

    @Query("UPDATE review_items SET interval_index = :index, due_at = :dueAt, last_result = :result WHERE id = :id")
    suspend fun record(id: Long, index: Int, dueAt: Long, result: String)

    @Query("SELECT COUNT(*) FROM review_items WHERE due_at <= :now AND interval_index < :graduated")
    fun observeDueCount(now: Long, graduated: Int): Flow<Int>

    /**
     * Cards due now, oldest first so nothing starves. Graduated cards are excluded rather
     * than deleted — keeping the row means a word is never re-seeded and re-learned.
     */
    @Query(
        """
        SELECT
            r.id AS itemId,
            r.source_type AS sourceType,
            r.source_id AS sourceId,
            r.interval_index AS intervalIndex,
            r.due_at AS dueAt,
            COALESCE(l.word, h.quote_exact) AS prompt,
            COALESCE(l.sentence, (SELECT n.body FROM notes n WHERE n.highlight_id = h.id LIMIT 1))
                AS context,
            COALESCE(bl.title, bh.title) AS bookTitle,
            COALESCE(l.chapter_title, h.chapter_title) AS chapterTitle
        FROM review_items r
        LEFT JOIN lookup_events l
            ON r.source_type = 'LOOKUP' AND l.id = r.source_id
        LEFT JOIN highlights h
            ON r.source_type = 'HIGHLIGHT' AND h.id = r.source_id
        LEFT JOIN books bl ON bl.content_hash = l.book_content_hash
        LEFT JOIN books bh ON bh.content_hash = h.book_content_hash
        WHERE r.due_at <= :now
          AND r.interval_index < :graduated
          AND COALESCE(l.word, h.quote_exact) IS NOT NULL
        ORDER BY r.due_at
        LIMIT :limit
        """,
    )
    fun observeDue(now: Long, graduated: Int, limit: Int = 100): Flow<List<ReviewCard>>

    /** Words worth reviewing: only ones the dictionary actually resolved. */
    @Query(
        """
        SELECT id FROM lookup_events
        WHERE found = 1
          AND id NOT IN (SELECT source_id FROM review_items WHERE source_type = 'LOOKUP')
        """,
    )
    suspend fun unseededLookupIds(): List<Long>

    @Query(
        """
        SELECT id FROM highlights
        WHERE id NOT IN (SELECT source_id FROM review_items WHERE source_type = 'HIGHLIGHT')
        """,
    )
    suspend fun unseededHighlightIds(): List<Long>

    @Query("SELECT created_at FROM lookup_events WHERE id = :id")
    suspend fun lookupCreatedAt(id: Long): Long?

    @Query("SELECT created_at FROM highlights WHERE id = :id")
    suspend fun highlightCreatedAt(id: Long): Long?

    @Query("SELECT COUNT(*) FROM review_items")
    suspend fun count(): Int
}
