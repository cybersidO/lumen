package com.sidharth.lumen.feature.reader.gesture

/**
 * How far the finger must keep travelling, past the end of a chapter, to cross into the
 * next one.
 *
 * 120dp is about a thumb's length of extra pull — long enough that reaching the last line
 * of a chapter does not throw you into the next one, short enough to be one unhurried
 * swipe rather than a chore.
 */
const val CHAPTER_PULL_DP = 120f

/**
 * Tracks the pull past the end (or the start) of a chapter.
 *
 * In scroll mode a chapter is a dead end: Readium lays each resource out separately, so
 * reaching the bottom and carrying on scrolling does nothing at all. Until 1.6.0 the side
 * taps covered that, and with those gone the only way onward was the table of contents.
 * So the scroll itself has to do it — keep pulling at the end of a chapter, a ring fills,
 * and the book moves on.
 *
 * Pull accumulates *across* gestures while the page stays at the edge, because the
 * natural thing to do at the bottom of a chapter is several short flicks rather than one
 * long drag. It is forgotten after [forgetAfterMillis] of stillness, so a pull abandoned
 * halfway does not lie in wait to fire later.
 *
 * Deliberately free of Android types, like the rest of [GestureGeometry].
 */
class ChapterPullTracker(
    private val thresholdPx: Float,
    private val forgetAfterMillis: Long = DEFAULT_FORGET_MILLIS,
) {
    private var pulled = 0f
    private var lastPullAt = Long.MIN_VALUE
    private var crossed = false

    /** 0f..1f — what the ring should show. */
    val progress: Float
        get() = if (thresholdPx <= 0f) 0f else (pulled / thresholdPx).coerceIn(0f, 1f)

    /**
     * Adds travel at the chapter edge. Returns true exactly once per crossing, at the
     * moment the ring fills — the chapter changes under the finger rather than on
     * release, which is what makes the ring mean anything.
     */
    fun pull(amountPx: Float, now: Long): Boolean {
        if (now - lastPullAt > forgetAfterMillis) reset()
        lastPullAt = now
        if (amountPx > 0f) pulled += amountPx
        if (!crossed && thresholdPx > 0f && pulled >= thresholdPx) {
            crossed = true
            return true
        }
        return false
    }

    /** The page can scroll again, so there is no pull in progress any more. */
    fun reset() {
        pulled = 0f
        crossed = false
    }

    companion object {
        /** Long enough to span the gap between two flicks, short enough not to surprise. */
        const val DEFAULT_FORGET_MILLIS = 1_500L
    }
}
