package com.sidharth.lumen.core.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Every dictionary lookup, kept deliberately.
 *
 * §2.4: "Every lookup is logged to the database with the word, book, chapter, and the
 * sentence it appeared in. This log is a feature, not telemetry — it powers §2.6." It
 * never leaves the device, and it is what M7's review queue is built from.
 */
@Entity(
    tableName = "lookup_events",
    indices = [Index("word"), Index("book_content_hash"), Index("created_at")],
)
data class LookupEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Exactly as it appeared in the book, punctuation trimmed but inflection intact. */
    val word: String,

    /** The base form the dictionary actually answered on; null when nothing resolved. */
    val lemma: String?,

    @ColumnInfo(name = "book_content_hash")
    val bookContentHash: String?,

    @ColumnInfo(name = "resource_href")
    val resourceHref: String?,

    @ColumnInfo(name = "chapter_title")
    val chapterTitle: String?,

    /** The sentence around the word — the context that makes review worthwhile. */
    val sentence: String?,

    @ColumnInfo(name = "dictionary_id")
    val dictionaryId: String,

    /** False for a word the dictionary could not resolve; those are worth seeing too. */
    val found: Boolean,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)

@Dao
interface LookupEventDao {

    @Insert
    suspend fun insert(event: LookupEventEntity): Long

    @Query("SELECT * FROM lookup_events ORDER BY created_at DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<LookupEventEntity>>

    @Query("SELECT COUNT(*) FROM lookup_events WHERE word = :word")
    suspend fun timesLookedUp(word: String): Int

    @Query("DELETE FROM lookup_events WHERE book_content_hash = :contentHash")
    suspend fun deleteForBook(contentHash: String)
}
