package com.sidharth.lumen.feature.stats

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sidharth.lumen.core.database.BookEntity
import com.sidharth.lumen.core.database.LumenDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** M10 stats against a real database: the recorder's lifecycle and the day-bucketing SQL. */
@RunWith(AndroidJUnit4::class)
class ReadingSessionTest {

    private lateinit var db: LumenDatabase
    private lateinit var recorder: ReadingSessionRecorder

    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now(zone)
    private fun startOf(day: LocalDate) = day.atStartOfDay(zone).toInstant().toEpochMilli()
    private fun min(n: Long) = TimeUnit.MINUTES.toMillis(n)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            LumenDatabase::class.java,
        ).build()
        recorder = ReadingSessionRecorder(db.readingSessionDao(), Dispatchers.IO)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seedBook(hash: String, title: String) = db.bookDao().insert(
        BookEntity(
            contentHash = hash, title = title, author = null, format = "EPUB",
            filePath = "/books/$hash.epub", coverPath = null, addedAt = 0, lastOpenedAt = null,
        ),
    )

    @Test
    fun aSessionIsWrittenOnStartAndGrowsWithEachTurn() = runTest {
        seedBook("h1", "Middlemarch")
        val t0 = startOf(today) + min(600) // 10:00 today
        recorder.start("h1", 0.10f, t0)
        recorder.pageTurned(0.11f, t0 + min(1))
        recorder.pageTurned(0.12f, t0 + min(2))
        recorder.stop(t0 + min(3))

        val perBook = db.readingSessionDao().observePerBook().first()
        assertEquals(1, perBook.size)
        assertEquals("Middlemarch", perBook[0].title)
        assertEquals(3L, perBook[0].minutes)
        assertEquals(2L, perBook[0].pagesTurned)
    }

    @Test
    fun aBlinkAndBackOutIsNotASession() = runTest {
        seedBook("h1", "Middlemarch")
        val t0 = startOf(today) + min(600)
        recorder.start("h1", 0f, t0)
        recorder.stop(t0 + 5_000) // 5 seconds
        assertEquals(0, db.readingSessionDao().count())
    }

    @Test
    fun minutesAreBucketedByLocalCalendarDay() = runTest {
        seedBook("h1", "Middlemarch")
        val dao = db.readingSessionDao()
        // 23:50 yesterday for 8 minutes: belongs to yesterday, not split, not today.
        val lateYesterday = startOf(today.minusDays(1)) + min(23 * 60 + 50)
        recorder.start("h1", 0f, lateYesterday)
        recorder.stop(lateYesterday + min(8))
        // 09:00 today for 20 minutes.
        val thisMorning = startOf(today) + min(9 * 60)
        recorder.start("h1", 0.2f, thisMorning)
        recorder.stop(thisMorning + min(20))

        val daily = dao.observeDaily(startOf(today.minusDays(13))).first()
        val byDay = daily.associate { it.dayEpoch to it.minutes }
        assertEquals(8L, byDay[startOf(today.minusDays(1))])
        assertEquals(20L, byDay[startOf(today)])

        val days = dao.readingDays()
        assertEquals(listOf(startOf(today), startOf(today.minusDays(1))), days)
        assertEquals(2, ReadingStreak.compute(days, startOf(today)))
    }

    @Test
    fun startingTheSameBookTwiceDoesNotOpenASecondSession() = runTest {
        seedBook("h1", "Middlemarch")
        val t0 = startOf(today) + min(600)
        recorder.start("h1", 0f, t0)
        recorder.start("h1", 0f, t0 + min(1)) // e.g. ON_START fired again
        recorder.stop(t0 + min(5))
        assertEquals(1, db.readingSessionDao().count())
        assertTrue(db.readingSessionDao().observeTotalMinutes().first() >= 4)
    }
}
