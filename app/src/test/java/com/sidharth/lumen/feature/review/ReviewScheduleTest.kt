package com.sidharth.lumen.feature.review

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * §M7's acceptance condition is a scheduling claim — "words I looked up yesterday appear
 * in today's queue" — so it is tested as arithmetic here, where the clock is an argument
 * rather than something to wait for.
 */
class ReviewScheduleTest {

    private val now = 1_700_000_000_000L
    private fun days(n: Long) = TimeUnit.DAYS.toMillis(n)

    // --- the milestone's acceptance condition ----------------------------------------

    @Test
    fun `a word looked up yesterday is due today`() {
        val yesterday = now - days(1)
        val dueAt = ReviewSchedule.initialDueAt(yesterday)
        assertTrue("due $dueAt should be <= now $now", dueAt <= now)
    }

    /** Saved this morning should be reviewable this afternoon, not tomorrow. */
    @Test
    fun `something saved today is due immediately`() {
        assertEquals(now, ReviewSchedule.initialDueAt(now))
        assertTrue(ReviewSchedule.initialDueAt(now) <= now)
    }

    // --- the ladder ------------------------------------------------------------------

    @Test
    fun `the intervals are the ones the spec asked for`() {
        assertEquals(listOf(1, 3, 7, 21, 60), ReviewSchedule.INTERVAL_DAYS.toList())
    }

    @Test
    fun `knowing a card climbs one rung each time`() {
        var index = 0
        val expected = listOf(3L, 7L, 21L, 60L)
        expected.forEach { expectedDays ->
            val due = ReviewSchedule.nextDueAt(now, index, ReviewResult.KNOWN)
            assertEquals(
                "from index $index",
                now + days(expectedDays),
                due,
            )
            index = ReviewSchedule.nextIntervalIndex(index, ReviewResult.KNOWN)
        }
    }

    @Test
    fun `a card known at the top of the ladder graduates`() {
        val top = ReviewSchedule.INTERVAL_DAYS.lastIndex
        val next = ReviewSchedule.nextIntervalIndex(top, ReviewResult.KNOWN)
        assertEquals(ReviewSchedule.GRADUATED_INDEX, next)
        assertTrue(ReviewSchedule.isGraduated(next))
    }

    @Test
    fun `graduating cannot run off the end of the ladder`() {
        var index = ReviewSchedule.GRADUATED_INDEX
        repeat(5) { index = ReviewSchedule.nextIntervalIndex(index, ReviewResult.KNOWN) }
        assertEquals(ReviewSchedule.GRADUATED_INDEX, index)
        // And it still produces a sane due date rather than crashing on a bad index.
        assertEquals(now + days(60), ReviewSchedule.nextDueAt(now, index, ReviewResult.KNOWN))
    }

    @Test
    fun `answering again drops back to the first interval`() {
        listOf(0, 1, 2, 3, 4).forEach { index ->
            assertEquals(0, ReviewSchedule.nextIntervalIndex(index, ReviewResult.AGAIN))
            assertEquals(now + days(1), ReviewSchedule.nextDueAt(now, index, ReviewResult.AGAIN))
        }
    }

    @Test
    fun `a fresh card is not graduated`() {
        assertFalse(ReviewSchedule.isGraduated(0))
        assertEquals("1d", ReviewSchedule.intervalLabel(0))
        assertEquals("60d", ReviewSchedule.intervalLabel(4))
        assertEquals("learned", ReviewSchedule.intervalLabel(ReviewSchedule.GRADUATED_INDEX))
    }

    // --- blanking the term ------------------------------------------------------------

    @Test
    fun `the term is blanked out of its sentence`() {
        val blanked = ReviewSchedule.blankTerm("The mice were everywhere.", "mice")
        assertFalse("still contains the answer: $blanked", blanked.contains("mice"))
        assertTrue(blanked.contains("____"))
        assertTrue(blanked.startsWith("The "))
    }

    @Test
    fun `blanking is case insensitive`() {
        val blanked = ReviewSchedule.blankTerm("Mice everywhere, mice!", "mice")
        assertFalse(blanked.contains("Mice"))
        assertFalse(blanked.contains("mice"))
    }

    /** Blanking "run" must not gut "running" — the sentence would stop making sense. */
    @Test
    fun `only whole words are blanked`() {
        val blanked = ReviewSchedule.blankTerm("He was running a run.", "run")
        assertTrue("running should survive: $blanked", blanked.contains("running"))
        assertTrue(blanked.contains("___"))
    }

    @Test
    fun `a sentence without the term is left alone`() {
        val sentence = "Nothing to blank here."
        assertEquals(sentence, ReviewSchedule.blankTerm(sentence, "absent"))
    }

    @Test
    fun `empty inputs are handled`() {
        assertEquals("text", ReviewSchedule.blankTerm("text", ""))
        assertEquals("", ReviewSchedule.blankTerm("", "word"))
    }

    @Test
    fun `regex metacharacters in the term do not break the blank`() {
        val blanked = ReviewSchedule.blankTerm("Costs 5 (approx.) today", "(approx.)")
        assertFalse(blanked.contains("(approx.)"))
    }

    /** A very long quote should not produce an absurd row of underscores. */
    @Test
    fun `the blank is capped in length`() {
        val term = "supercalifragilisticexpialidocious"
        val blanked = ReviewSchedule.blankTerm("It was $term today.", term)
        assertTrue(blanked.contains("_"))
        assertTrue("blank too long: $blanked", Regex("_+").find(blanked)!!.value.length <= 12)
    }
}
