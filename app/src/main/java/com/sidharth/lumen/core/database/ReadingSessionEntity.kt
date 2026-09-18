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
 * One stretch of reading: the book was open and in the foreground from [startedAt] to
 * [endedAt]. Progress is captured at both ends so pages-per-hour can be derived without
 * a separate page-turn log.
 *
 * The row is written when reading starts and *updated* on every page turn, so a process
 * kill mid-session loses at most the seconds since the last turn rather than the session.
 */
@Entity(
    tableName = "reading_sessions",
    indices = [
        Index("book_content_hash"),
        Index("started_at"),
    ],
)
data class ReadingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "book_content_hash") val bookContentHash: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long,
    @ColumnInfo(name = "start_progress") val startProgress: Float,
    @ColumnInfo(name = "end_progress") val endProgress: Float,
    @ColumnInfo(name = "pages_turned") val pagesTurned: Int,
)

/** Minutes read on one calendar day (device-local), for the daily bars and the streak. */
data class DailyReading(
    val dayEpoch: Long,
    val minutes: Long,
)

/** Per-book totals for the stats list. */
data class BookReadingTotal(
    @ColumnInfo(name = "book_content_hash") val bookContentHash: String,
    val title: String?,
    val minutes: Long,
    @ColumnInfo(name = "pages_turned") val pagesTurned: Long,
    @ColumnInfo(name = "last_read_at") val lastReadAt: Long,
)

@Dao
interface ReadingSessionDao {

    @Insert
    suspend fun insert(session: ReadingSessionEntity): Long

    @Query(
        """
        UPDATE reading_sessions
        SET ended_at = :endedAt, end_progress = :endProgress, pages_turned = :pagesTurned
        WHERE id = :id
        """,
    )
    suspend fun touch(id: Long, endedAt: Long, endProgress: Float, pagesTurned: Int)

    @Query("DELETE FROM reading_sessions WHERE id = :id")
    suspend fun delete(id: Long)

    /**
     * Minutes per local calendar day. `unixepoch` arithmetic is done in SQL so the day
     * boundary is the device's, not UTC's — a session at 23:50 belongs to that day.
     *
     * The trailing `'utc'` matters: `datetime(..., 'localtime', 'start of day')` yields a
     * local-time *string*, and `strftime('%s', …)` would otherwise read it as UTC, putting
     * every bucket off by the zone offset (5½ hours in India). Caught by
     * `ReadingSessionTest.minutesAreBucketedByLocalCalendarDay` on an Asia/Kolkata emulator.
     */
    @Query(
        """
        SELECT
            CAST(strftime('%s', datetime(started_at / 1000, 'unixepoch', 'localtime', 'start of day'), 'utc') AS INTEGER) * 1000 AS dayEpoch,
            SUM(ended_at - started_at) / 60000 AS minutes
        FROM reading_sessions
        WHERE started_at >= :sinceMillis
        GROUP BY dayEpoch
        ORDER BY dayEpoch ASC
        """,
    )
    fun observeDaily(sinceMillis: Long): Flow<List<DailyReading>>

    @Query(
        """
        SELECT
            s.book_content_hash,
            b.title AS title,
            SUM(s.ended_at - s.started_at) / 60000 AS minutes,
            SUM(s.pages_turned) AS pages_turned,
            MAX(s.ended_at) AS last_read_at
        FROM reading_sessions s
        LEFT JOIN books b ON b.content_hash = s.book_content_hash
        GROUP BY s.book_content_hash
        ORDER BY last_read_at DESC
        """,
    )
    fun observePerBook(): Flow<List<BookReadingTotal>>

    @Query("SELECT COALESCE(SUM(ended_at - started_at), 0) / 60000 FROM reading_sessions")
    fun observeTotalMinutes(): Flow<Long>

    @Query("SELECT COALESCE(SUM(pages_turned), 0) FROM reading_sessions")
    fun observeTotalPages(): Flow<Long>

    /** Every distinct local day with any reading at all, newest first — for the streak. */
    @Query(
        """
        SELECT DISTINCT
            CAST(strftime('%s', datetime(started_at / 1000, 'unixepoch', 'localtime', 'start of day'), 'utc') AS INTEGER) * 1000
        FROM reading_sessions
        ORDER BY 1 DESC
        """,
    )
    suspend fun readingDays(): List<Long>

    @Query("SELECT COUNT(*) FROM reading_sessions")
    suspend fun count(): Int
}
