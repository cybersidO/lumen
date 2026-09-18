package com.sidharth.lumen.feature.stats

import java.util.concurrent.TimeUnit

/**
 * A running estimate of how long one page takes this reader, for "~12 min left in this
 * chapter". Pure arithmetic; the clock is always passed in.
 *
 * It is an exponential moving average over observed page turns, so it settles quickly to
 * the reader's actual pace and drifts with it, rather than being a fixed guess. Turns that
 * were clearly not reading — a flick through many pages in a second, or a return after a
 * long pause — are ignored, otherwise a single trip to the kitchen would claim the reader
 * takes twenty minutes a page.
 */
class ReadingPace(
    initialSecondsPerPage: Double = DEFAULT_SECONDS_PER_PAGE,
) {
    var secondsPerPage: Double = initialSecondsPerPage
        private set

    /** How many page turns have fed the estimate; the UI hides the figure until a few. */
    var samples: Int = 0
        private set

    private var lastTurnAt: Long? = null

    /** Call on every page turn. Returns true if the turn was counted as reading. */
    fun onPageTurned(now: Long): Boolean {
        val previous = lastTurnAt
        lastTurnAt = now
        if (previous == null) return false
        val elapsed = (now - previous) / 1000.0
        if (elapsed < MIN_PLAUSIBLE_SECONDS || elapsed > MAX_PLAUSIBLE_SECONDS) return false
        secondsPerPage = if (samples == 0) elapsed else ALPHA * elapsed + (1 - ALPHA) * secondsPerPage
        samples++
        return true
    }

    /** Resets the interval clock without touching the estimate — e.g. after a chapter jump. */
    fun markInteraction(now: Long) {
        lastTurnAt = now
    }

    fun minutesFor(pages: Int): Int =
        if (pages <= 0) 0 else Math.ceil(pages * secondsPerPage / 60.0).toInt()

    val isSettled: Boolean get() = samples >= SETTLED_AFTER_SAMPLES

    companion object {
        const val DEFAULT_SECONDS_PER_PAGE = 60.0
        const val MIN_PLAUSIBLE_SECONDS = 4.0
        val MAX_PLAUSIBLE_SECONDS = TimeUnit.MINUTES.toSeconds(6).toDouble()
        const val ALPHA = 0.3
        const val SETTLED_AFTER_SAMPLES = 3
    }
}

/**
 * Consecutive calendar days with reading, counted back from today (or from yesterday, if
 * today has none yet — a streak is not broken until the day is over).
 */
object ReadingStreak {
    fun compute(readingDaysDesc: List<Long>, todayEpoch: Long, dayMillis: Long = TimeUnit.DAYS.toMillis(1)): Int {
        if (readingDaysDesc.isEmpty()) return 0
        val days = readingDaysDesc.toSet()
        var cursor = if (todayEpoch in days) todayEpoch else todayEpoch - dayMillis
        var streak = 0
        while (cursor in days) {
            streak++
            cursor -= dayMillis
        }
        return streak
    }
}
