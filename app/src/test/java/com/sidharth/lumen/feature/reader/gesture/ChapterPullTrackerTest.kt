package com.sidharth.lumen.feature.reader.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterPullTrackerTest {

    private fun tracker() = ChapterPullTracker(thresholdPx = 300f)

    @Test
    fun `the ring fills in proportion to the pull`() {
        val t = tracker()
        t.pull(75f, now = 0)
        assertEquals(0.25f, t.progress, 1e-4f)
        t.pull(75f, now = 100)
        assertEquals(0.5f, t.progress, 1e-4f)
    }

    @Test
    fun `several short flicks add up`() {
        // The real behaviour at the bottom of a chapter: nobody does one long drag.
        val t = tracker()
        assertFalse(t.pull(100f, now = 0))
        assertFalse(t.pull(100f, now = 300))
        assertTrue("three flicks should cross", t.pull(100f, now = 600))
    }

    @Test
    fun `the chapter crosses once, not on every further pull`() {
        val t = tracker()
        assertTrue(t.pull(400f, now = 0))
        assertFalse("a second crossing would skip a chapter", t.pull(400f, now = 100))
    }

    @Test
    fun `a pull abandoned halfway is forgotten rather than lying in wait`() {
        val t = tracker()
        t.pull(200f, now = 0)
        assertEquals(200f / 300f, t.progress, 1e-4f)
        // Back a minute later, a short pull must not inherit the old distance.
        assertFalse(t.pull(50f, now = 60_000))
        assertEquals(50f / 300f, t.progress, 1e-4f)
    }

    @Test
    fun `the pull survives the gap between two flicks`() {
        val t = tracker()
        t.pull(200f, now = 0)
        assertFalse(t.pull(50f, now = ChapterPullTracker.DEFAULT_FORGET_MILLIS))
        assertEquals(250f / 300f, t.progress, 1e-4f)
    }

    @Test
    fun `scrolling away from the edge clears the pull`() {
        val t = tracker()
        t.pull(200f, now = 0)
        t.reset()
        assertEquals(0f, t.progress, 1e-4f)
        assertFalse(t.pull(100f, now = 100))
    }

    @Test
    fun `progress is clamped and never exceeds a full ring`() {
        val t = tracker()
        t.pull(5_000f, now = 0)
        assertEquals(1f, t.progress, 1e-4f)
    }

    @Test
    fun `movement away from the pull direction is ignored, not subtracted`() {
        val t = tracker()
        t.pull(150f, now = 0)
        t.pull(-150f, now = 50)
        assertEquals(0.5f, t.progress, 1e-4f)
    }

    @Test
    fun `a zero threshold cannot cross or divide by zero`() {
        val t = ChapterPullTracker(thresholdPx = 0f)
        assertFalse(t.pull(100f, now = 0))
        assertEquals(0f, t.progress, 1e-4f)
    }
}
