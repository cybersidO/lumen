package com.sidharth.lumen.feature.review

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sidharth.lumen.core.database.BookEntity
import com.sidharth.lumen.core.database.HighlightEntity
import com.sidharth.lumen.core.database.LookupEventEntity
import com.sidharth.lumen.core.database.LumenDatabase
import com.sidharth.lumen.core.database.ReviewSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * §M7's acceptance condition, against a real database rather than a fixture:
 * *"words I looked up yesterday appear in today's queue"*.
 *
 * The clock is passed in everywhere, so "yesterday" is a row with an older `created_at`
 * rather than a test that has to wait a day.
 */
@RunWith(AndroidJUnit4::class)
class ReviewQueueTest {

    private lateinit var database: LumenDatabase
    private lateinit var repository: ReviewRepository

    private val now = System.currentTimeMillis()
    private fun daysAgo(n: Long) = now - TimeUnit.DAYS.toMillis(n)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            LumenDatabase::class.java,
        ).build()
        repository = ReviewRepository(database.reviewDao(), Dispatchers.IO)
    }

    @After
    fun tearDown() = database.close()

    private suspend fun insertLookup(
        word: String,
        createdAt: Long,
        found: Boolean = true,
    ): Long = database.lookupEventDao().insert(
        LookupEventEntity(
            word = word,
            lemma = word,
            bookContentHash = "hash-a",
            resourceHref = "/ch1.xhtml",
            chapterTitle = "Chapter One",
            sentence = "The $word was everywhere in the room.",
            dictionaryId = "wordnet-3.1",
            found = found,
            createdAt = createdAt,
        ),
    )

    private suspend fun insertHighlight(quote: String, createdAt: Long): Long =
        database.highlightDao().insert(
            HighlightEntity(
                bookContentHash = "hash-a",
                colorId = 0,
                createdAt = createdAt,
                updatedAt = createdAt,
                resourceHref = "/ch1.xhtml",
                resourceProgression = 0.4,
                locatorJson = null,
                quotePrefix = "",
                quoteExact = quote,
                quoteSuffix = "",
                chapterTitle = "Chapter One",
                progressFraction = 0.4f,
            ),
        )

    private suspend fun seedBook() = database.bookDao().insert(
        BookEntity(
            contentHash = "hash-a",
            title = "Middlemarch",
            author = "George Eliot",
            format = "EPUB",
            filePath = "/books/hash-a.epub",
            coverPath = null,
            addedAt = daysAgo(30),
            lastOpenedAt = null,
        ),
    )

    // --- the milestone's acceptance condition ------------------------------------------

    @Test
    fun aWordLookedUpYesterdayIsInTodaysQueue() = runTest {
        seedBook()
        insertLookup("mice", createdAt = daysAgo(1))

        assertEquals(1, repository.seed())

        val due = repository.observeDue(now).first()
        assertEquals(1, due.size)
        val card = due.single()
        assertEquals("mice", card.prompt)
        assertEquals(ReviewSourceType.LOOKUP, card.sourceType)
        // The card carries the context the review prompt is built from.
        assertNotNull(card.context)
        assertTrue(card.context!!.contains("mice"))
        assertEquals("Middlemarch", card.bookTitle)
        assertEquals("Chapter One", card.chapterTitle)
    }

    @Test
    fun highlightsJoinTheQueueToo() = runTest {
        seedBook()
        insertHighlight("the exact words worth keeping", createdAt = daysAgo(2))

        repository.seed()

        val due = repository.observeDue(now).first()
        assertEquals(1, due.size)
        assertEquals(ReviewSourceType.HIGHLIGHT, due.single().sourceType)
        assertEquals("the exact words worth keeping", due.single().prompt)
    }

    /** A word the dictionary could not resolve is not worth drilling. */
    @Test
    fun unresolvedLookupsAreNotSeeded() = runTest {
        seedBook()
        insertLookup("zzzblarg", createdAt = daysAgo(1), found = false)

        assertEquals(0, repository.seed())
        assertEquals(0, repository.observeDue(now).first().size)
    }

    /**
     * Seeding runs on every visit to the notes screen, so it has to be safe to repeat —
     * the unique index on (source_type, source_id) is what guarantees that.
     */
    @Test
    fun seedingTwiceDoesNotDuplicateOrResetCards() = runTest {
        seedBook()
        val lookupId = insertLookup("mice", createdAt = daysAgo(1))
        repository.seed()

        // Answer it, so its schedule has moved off the default.
        val card = repository.observeDue(now).first().single()
        repository.record(card, ReviewResult.KNOWN, now)

        // A second lookup of the same word must not resurrect the card.
        insertLookup("mice", createdAt = now)
        repository.seed()

        assertEquals(
            "one card per source row",
            2,
            database.reviewDao().count(),
        )
        val advanced = database.reviewDao().observeDue(
            now + TimeUnit.DAYS.toMillis(4),
            ReviewSchedule.GRADUATED_INDEX,
        ).first().first { it.sourceId == lookupId }
        assertEquals(1, advanced.intervalIndex)
    }

    // --- the ladder, through the database ----------------------------------------------

    @Test
    fun knowingACardPushesItOutOfTodaysQueue() = runTest {
        seedBook()
        insertLookup("mice", createdAt = daysAgo(1))
        repository.seed()

        val card = repository.observeDue(now).first().single()
        repository.record(card, ReviewResult.KNOWN, now)

        assertEquals("gone from today", 0, repository.observeDue(now).first().size)
        // 1d → 3d: still not due in two days, due in four.
        assertEquals(
            0,
            repository.observeDue(now + TimeUnit.DAYS.toMillis(2)).first().size,
        )
        assertEquals(
            1,
            repository.observeDue(now + TimeUnit.DAYS.toMillis(4)).first().size,
        )
    }

    @Test
    fun answeringAgainBringsTheCardBackTomorrow() = runTest {
        seedBook()
        insertLookup("mice", createdAt = daysAgo(1))
        repository.seed()

        val card = repository.observeDue(now).first().single()
        repository.record(card, ReviewResult.AGAIN, now)

        assertEquals(0, repository.observeDue(now).first().size)
        assertEquals(
            1,
            repository.observeDue(now + TimeUnit.DAYS.toMillis(2)).first().size,
        )
    }

    @Test
    fun aGraduatedCardLeavesTheQueueForGood() = runTest {
        seedBook()
        insertLookup("mice", createdAt = daysAgo(1))
        repository.seed()

        // Climb the whole ladder.
        var clock = now
        repeat(ReviewSchedule.INTERVAL_DAYS.size) {
            val card = repository.observeDue(clock).first().singleOrNull()
            assertNotNull("expected a due card at rung $it", card)
            repository.record(card!!, ReviewResult.KNOWN, clock)
            clock += TimeUnit.DAYS.toMillis(90)
        }

        assertEquals(
            "a learned card should not come back",
            0,
            repository.observeDue(clock + TimeUnit.DAYS.toMillis(365)).first().size,
        )
    }

    // --- the badge ---------------------------------------------------------------------

    @Test
    fun theDueCountMatchesTheQueue() = runTest {
        seedBook()
        insertLookup("mice", createdAt = daysAgo(1))
        insertLookup("better", createdAt = daysAgo(3))
        insertHighlight("a passage", createdAt = daysAgo(2))
        // Not yet due: captured in the future, which is what a fresh capture looks like
        // to a queue read a moment earlier.
        insertLookup("later", createdAt = now + TimeUnit.DAYS.toMillis(5))
        repository.seed()

        assertEquals(3, repository.observeDueCount(now).first())
        assertEquals(3, repository.observeDue(now).first().size)
    }

    @Test
    fun anEmptyLibraryHasNothingDue() = runTest {
        assertEquals(0, repository.seed())
        assertEquals(0, repository.observeDueCount(now).first())
    }
}
