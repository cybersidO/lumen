package com.sidharth.lumen.feature.review

import java.util.concurrent.TimeUnit

/** What the reader said about a card. */
enum class ReviewResult { KNOWN, AGAIN }

/**
 * §2.6's spaced schedule: "A simple spaced-interval schedule (1d, 3d, 7d, 21d, 60d) is
 * enough; don't build a full SM-2 unless it falls out for free."
 *
 * It does not fall out for free — SM-2 needs an ease factor per card and a graded answer
 * (0–5), and this app asks a binary "known / review again". So this is exactly the ladder
 * the spec asked for, and nothing more.
 *
 * Pure arithmetic on millis, no Android and no clock of its own: `now` is always passed
 * in, which is what makes "a word looked up yesterday is due today" directly testable.
 */
object ReviewSchedule {

    /** Days until the next showing, indexed by how many times it has been got right. */
    val INTERVAL_DAYS = intArrayOf(1, 3, 7, 21, 60)

    /** Past the last rung a card is considered learned and leaves the queue. */
    val GRADUATED_INDEX = INTERVAL_DAYS.size

    /**
     * A newly captured word or highlight is due **immediately**, not tomorrow.
     *
     * The alternative — first showing after 1 day — means something saved this morning is
     * invisible all day, which reads as the feature being broken rather than as a
     * scheduling choice.
     */
    fun initialDueAt(capturedAt: Long): Long = capturedAt

    /**
     * The next due time after answering.
     *
     * KNOWN climbs one rung; AGAIN drops back to the first, because the point of the
     * ladder is that a card you have just failed should come round again soon.
     */
    fun nextDueAt(now: Long, intervalIndex: Int, result: ReviewResult): Long {
        val next = nextIntervalIndex(intervalIndex, result)
        if (next >= GRADUATED_INDEX) {
            // Graduated: parked far enough out that it will not reappear on its own.
            return now + TimeUnit.DAYS.toMillis(INTERVAL_DAYS.last().toLong())
        }
        return now + TimeUnit.DAYS.toMillis(INTERVAL_DAYS[next].toLong())
    }

    fun nextIntervalIndex(intervalIndex: Int, result: ReviewResult): Int = when (result) {
        ReviewResult.KNOWN -> (intervalIndex + 1).coerceAtMost(GRADUATED_INDEX)
        ReviewResult.AGAIN -> 0
    }

    fun isGraduated(intervalIndex: Int): Boolean = intervalIndex >= GRADUATED_INDEX

    /** Human label for the interval a card has reached, for the review screen. */
    fun intervalLabel(intervalIndex: Int): String = when {
        isGraduated(intervalIndex) -> "learned"
        else -> "${INTERVAL_DAYS[intervalIndex]}d"
    }

    /**
     * Blanks the term inside its sentence so the card is a recall prompt rather than a
     * giveaway (§2.6: "the highlight's context with the term blanked").
     *
     * Matching is case-insensitive and whole-word, so blanking "run" does not gut
     * "running"; the blank keeps the original word's length as a faint hint at scale.
     */
    fun blankTerm(sentence: String, term: String): String {
        val cleaned = term.trim()
        if (cleaned.isEmpty() || sentence.isEmpty()) return sentence
        val pattern = Regex(
            "(?<![\\p{L}\\p{N}])${Regex.escape(cleaned)}(?![\\p{L}\\p{N}])",
            RegexOption.IGNORE_CASE,
        )
        return if (pattern.containsMatchIn(sentence)) {
            pattern.replace(sentence) { "_".repeat(it.value.length.coerceAtMost(12)) }
        } else {
            sentence
        }
    }
}
