package com.sidharth.lumen.feature.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class ReadingPaceTest {

    private fun sec(n: Long) = TimeUnit.SECONDS.toMillis(n)

    @Test
    fun theFirstTurnIsAnAnchorNotASample() {
        val pace = ReadingPace()
        assertFalse(pace.onPageTurned(sec(100)))
        assertEquals(0, pace.samples)
        assertFalse(pace.isSettled)
    }

    @Test
    fun steadyTurnsSettleToTheObservedPace() {
        val pace = ReadingPace()
        var t = 0L
        pace.onPageTurned(t)
        repeat(5) { t += sec(45); pace.onPageTurned(t) }
        assertTrue(pace.isSettled)
        assertEquals(45.0, pace.secondsPerPage, 0.01)
        // 4 pages left at 45s ≈ 3 minutes, rounded up.
        assertEquals(3, pace.minutesFor(4))
    }

    @Test
    fun aFlickThroughPagesIsIgnored() {
        val pace = ReadingPace()
        pace.onPageTurned(sec(0))
        pace.onPageTurned(sec(50))          // counted
        assertFalse(pace.onPageTurned(sec(51)))  // 1s later: skimming, not reading
        assertEquals(1, pace.samples)
        assertEquals(50.0, pace.secondsPerPage, 0.01)
    }

    @Test
    fun aLongPauseIsIgnored() {
        val pace = ReadingPace()
        pace.onPageTurned(sec(0))
        pace.onPageTurned(sec(60))
        assertFalse(pace.onPageTurned(sec(60 + 20 * 60)))  // went to make tea
        assertEquals(60.0, pace.secondsPerPage, 0.01)
    }

    @Test
    fun markInteractionResetsTheClockWithoutSampling() {
        val pace = ReadingPace()
        pace.onPageTurned(sec(0))
        pace.markInteraction(sec(300))  // e.g. a TOC jump
        assertTrue(pace.onPageTurned(sec(340)))  // 40s after the jump, not 340s
        assertEquals(40.0, pace.secondsPerPage, 0.01)
    }

    @Test
    fun zeroPagesLeftIsZeroMinutes() {
        assertEquals(0, ReadingPace().minutesFor(0))
    }
}

class ReadingStreakTest {
    private val day = TimeUnit.DAYS.toMillis(1)
    private val today = 1_700_000_000_000L - (1_700_000_000_000L % day)

    @Test
    fun noReadingIsNoStreak() {
        assertEquals(0, ReadingStreak.compute(emptyList(), today, day))
    }

    @Test
    fun consecutiveDaysEndingTodayCount() {
        val days = listOf(today, today - day, today - 2 * day)
        assertEquals(3, ReadingStreak.compute(days, today, day))
    }

    @Test
    fun aStreakIsNotBrokenUntilTheDayIsOver() {
        // Read yesterday and the day before, nothing yet today: still 2.
        val days = listOf(today - day, today - 2 * day)
        assertEquals(2, ReadingStreak.compute(days, today, day))
    }

    @Test
    fun aGapEndsTheStreak() {
        val days = listOf(today, today - day, today - 3 * day, today - 4 * day)
        assertEquals(2, ReadingStreak.compute(days, today, day))
    }

    @Test
    fun readingOnlyLongAgoIsZero() {
        assertEquals(0, ReadingStreak.compute(listOf(today - 5 * day), today, day))
    }
}
