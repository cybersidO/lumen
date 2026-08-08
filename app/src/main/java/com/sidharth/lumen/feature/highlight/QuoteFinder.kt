package com.sidharth.lumen.feature.highlight

/**
 * The fallback half of §2.5's anchoring contract: when position-based resolution no
 * longer matches — the book was re-imported from a slightly different file, or the
 * publisher shuffled a chapter — the highlight is re-anchored by searching the resource's
 * text for the quote with its context.
 *
 * Pure Kotlin, no Android or Readium types, because the spec demands unit tests for
 * exactly this path and the logic must be checkable without a device.
 */
object QuoteFinder {

    data class Match(
        val start: Int,
        val end: Int,
        /** True when prefix/suffix context agreed; false for a bare-quote last resort. */
        val contextMatched: Boolean,
    )

    /**
     * Finds [exact] in [document], using [prefix]/[suffix] context to pick between
     * multiple occurrences.
     *
     * Resolution ladder, strictest first:
     *  1. every occurrence of the exact quote, scored by how much surrounding context
     *     agrees — best score wins;
     *  2. whitespace-normalised comparison of the same, because re-serialised EPUB text
     *     differs most often in line breaks and runs of spaces;
     *  3. nothing — null. A highlight that cannot be re-anchored must be reported lost,
     *     not silently glued somewhere wrong.
     */
    fun find(document: String, prefix: String, exact: String, suffix: String): Match? {
        if (exact.isEmpty() || document.isEmpty()) return null

        // Direct pass over the raw document.
        val direct = findOccurrences(document, exact)?.let { occurrences ->
            pickBest(document, occurrences, prefix, suffix, exact.length)
        }

        // A direct hit whose context agrees completely cannot be beaten — done.
        val fullContext = prefix.length + suffix.length
        if (direct != null && (fullContext == 0 || direct.score >= fullContext)) {
            return direct.match
        }

        // Whitespace-tolerant pass: the document may spell the same text with different
        // line breaks. Run it even when the direct pass matched, because a partial-context
        // direct hit can be the *wrong* occurrence while the normalised pass finds the
        // right one with stronger context agreement.
        val normalized = normalizeWithMap(document)
        val needle = normalizeText(exact)
        val tolerant = if (needle.isEmpty()) {
            null
        } else {
            findOccurrences(normalized.text, needle)?.let { occurrences ->
                pickBest(
                    normalized.text,
                    occurrences,
                    // Collapsed but NOT trimmed: the boundary space between context and
                    // quote is often the only part of the context that survives, and
                    // trimming it away silently zeroes every context score.
                    normalizeContext(prefix),
                    normalizeContext(suffix),
                    needle.length,
                )
            }?.let { scored ->
                ScoredMatch(
                    match = Match(
                        start = normalized.mapToOriginal(scored.match.start),
                        end = normalized.mapToOriginal(scored.match.end - 1) + 1,
                        contextMatched = scored.match.contextMatched,
                    ),
                    score = scored.score,
                )
            }
        }

        return when {
            direct == null -> tolerant?.match
            tolerant == null -> direct.match
            tolerant.score > direct.score -> tolerant.match
            else -> direct.match
        }
    }

    private data class ScoredMatch(val match: Match, val score: Int)

    // --- occurrence scan ---------------------------------------------------------------

    private fun findOccurrences(haystack: String, needle: String): List<Int>? {
        val hits = mutableListOf<Int>()
        var index = haystack.indexOf(needle)
        while (index >= 0) {
            hits += index
            if (hits.size > MAX_OCCURRENCES) return hits // plenty to disambiguate from
            index = haystack.indexOf(needle, index + 1)
        }
        return hits.ifEmpty { null }
    }

    /**
     * Scores each occurrence by how many characters of prefix (leftward) and suffix
     * (rightward) context agree with the document around it. A unique occurrence wins
     * even with zero context; ties on score go to the earliest occurrence, which is at
     * least deterministic.
     */
    private fun pickBest(
        document: String,
        occurrences: List<Int>,
        prefix: String,
        suffix: String,
        exactLength: Int,
    ): ScoredMatch? {
        if (occurrences.size == 1) {
            val start = occurrences.single()
            val score = contextScore(document, start, exactLength, prefix, suffix)
            return ScoredMatch(
                Match(start, start + exactLength, contextMatched = score > 0),
                score,
            )
        }

        var bestStart = -1
        var bestScore = -1
        for (start in occurrences) {
            val score = contextScore(document, start, exactLength, prefix, suffix)
            if (score > bestScore) {
                bestScore = score
                bestStart = start
            }
        }

        // Multiple occurrences and no context agreement anywhere: anchoring here would be
        // a guess between identical strings, and a wrong highlight is worse than a lost one.
        if (bestScore == 0) return null

        return ScoredMatch(
            Match(bestStart, bestStart + exactLength, contextMatched = true),
            bestScore,
        )
    }

    private fun contextScore(
        document: String,
        start: Int,
        exactLength: Int,
        prefix: String,
        suffix: String,
    ): Int {
        var score = 0

        // Walk backwards from the match comparing against the prefix's tail.
        var docIndex = start - 1
        var prefixIndex = prefix.length - 1
        while (docIndex >= 0 && prefixIndex >= 0 && document[docIndex] == prefix[prefixIndex]) {
            score++
            docIndex--
            prefixIndex--
        }

        // Walk forward from the end of the match along the suffix.
        docIndex = start + exactLength
        var suffixIndex = 0
        while (
            docIndex < document.length &&
            suffixIndex < suffix.length &&
            document[docIndex] == suffix[suffixIndex]
        ) {
            score++
            docIndex++
            suffixIndex++
        }

        return score
    }

    // --- whitespace normalisation with an offset map -------------------------------------

    internal fun normalizeText(value: String): String =
        value.replace(WHITESPACE_RUN, " ").trim()

    /** Whitespace runs collapsed, boundary spaces kept — context is matched at an edge. */
    internal fun normalizeContext(value: String): String =
        value.replace(WHITESPACE_RUN, " ")

    private class NormalizedDocument(
        val text: String,
        private val originalOffsets: IntArray,
    ) {
        fun mapToOriginal(normalizedIndex: Int): Int =
            originalOffsets[normalizedIndex.coerceIn(0, originalOffsets.lastIndex)]
    }

    /** Collapses whitespace runs to single spaces, remembering where each char came from. */
    private fun normalizeWithMap(document: String): NormalizedDocument {
        val builder = StringBuilder(document.length)
        val offsets = ArrayList<Int>(document.length)
        var inWhitespace = false

        document.forEachIndexed { index, char ->
            if (char.isWhitespace()) {
                if (!inWhitespace && builder.isNotEmpty()) {
                    builder.append(' ')
                    offsets.add(index)
                }
                inWhitespace = true
            } else {
                builder.append(char)
                offsets.add(index)
                inWhitespace = false
            }
        }

        // Trim a trailing collapsed space so indices stay in range.
        if (builder.isNotEmpty() && builder.last() == ' ') {
            builder.deleteCharAt(builder.lastIndex)
            offsets.removeAt(offsets.lastIndex)
        }

        return NormalizedDocument(builder.toString(), offsets.toIntArray())
    }

    private val WHITESPACE_RUN = Regex("\\s+")
    private const val MAX_OCCURRENCES = 64
}
