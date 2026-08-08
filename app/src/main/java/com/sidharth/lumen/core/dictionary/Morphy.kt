package com.sidharth.lumen.core.dictionary

/** WordNet's four parts of speech. */
enum class WordPos(val code: String, val displayName: String) {
    NOUN("n", "noun"),
    VERB("v", "verb"),
    ADJECTIVE("a", "adjective"),
    ADVERB("r", "adverb"),
    ;

    companion object {
        fun fromCode(code: String): WordPos? = entries.firstOrNull { it.code == code }
    }
}

/**
 * Lemmatisation, WordNet's Morphy algorithm plus one addition.
 *
 * §2.4 is blunt about why this exists: "a lookup that fails on plurals is a broken
 * feature." The reader taps an inflected word in a sentence; the dictionary is keyed by
 * base forms.
 *
 * Deliberately free of Android and SQLite: it asks two questions of whatever is behind it
 * ([isKnownLemma], [exceptionsFor]) and is otherwise pure, so every rule below is directly
 * unit-testable.
 *
 * Order matters, strongest evidence first:
 *  1. the surface form itself, if the dictionary already knows it ("running" is a real
 *     adjective and noun — the reader should see that first);
 *  2. WordNet's irregular-form exception lists (mice → mouse, better → good/well);
 *  3. suffix detachment rules, keeping only candidates the dictionary confirms.
 */
class Morphy(
    private val isKnownLemma: (lemma: String, pos: WordPos) -> Boolean,
    private val exceptionsFor: (surface: String) -> List<Pair<String, WordPos>>,
) {

    /**
     * Every base form worth looking up for [surface], best first, without duplicates.
     * An empty list means the word is genuinely unknown — the caller then offers §2.4's
     * "search the web" escape hatch rather than showing an empty sheet.
     */
    fun candidates(surface: String): List<Candidate> {
        val word = normalise(surface)
        if (word.isEmpty()) return emptyList()

        val out = LinkedHashSet<Candidate>()

        // 1. The word as typed, if it is itself a headword.
        WordPos.entries.forEach { pos ->
            if (isKnownLemma(word, pos)) out += Candidate(word, pos, Origin.EXACT)
        }

        // 2. Irregular forms. Trusted even when the base is odd, because these are
        //    hand-curated: WordNet says "mice" is "mouse" and that settles it.
        exceptionsFor(word).forEach { (base, pos) ->
            if (isKnownLemma(base, pos)) out += Candidate(base, pos, Origin.EXCEPTION)
        }

        // 3. Regular suffix detachment, confirmed against the dictionary.
        WordPos.entries.forEach { pos ->
            detachmentRules(pos).forEach { (suffix, replacement) ->
                if (word.length > suffix.length && word.endsWith(suffix)) {
                    val stem = word.dropLast(suffix.length) + replacement
                    if (stem.isNotEmpty() && isKnownLemma(stem, pos)) {
                        out += Candidate(stem, pos, Origin.RULE)
                    }
                    // "running" → "runn" → "run"; "stopped" → "stopp" → "stop".
                    // WordNet's own rules stop short of undoing the doubled consonant,
                    // so a reader tapping "running" would otherwise never reach the verb.
                    undoubleConsonant(stem)?.let { undoubled ->
                        if (isKnownLemma(undoubled, pos)) {
                            out += Candidate(undoubled, pos, Origin.RULE)
                        }
                    }
                }
            }
        }

        return out.toList()
    }

    /** A base form to look up, and why it was proposed. */
    data class Candidate(
        val lemma: String,
        val pos: WordPos,
        val origin: Origin,
    )

    enum class Origin { EXACT, EXCEPTION, RULE }

    private companion object {
        /**
         * Trims the punctuation a tap on running text drags along — quotes, commas, the
         * trailing full stop — and lowercases. Interior hyphens and apostrophes stay,
         * because "self-aware" and "don't" are headwords in their own right.
         */
        fun normalise(raw: String): String =
            raw.trim()
                .lowercase()
                .trim { !it.isLetterOrDigit() && it != '-' && it != '\'' }

        /** WordNet's detachment table: suffix to strip, replacement to append. */
        fun detachmentRules(pos: WordPos): List<Pair<String, String>> = when (pos) {
            WordPos.NOUN -> listOf(
                "ses" to "s", "xes" to "x", "zes" to "z",
                "ches" to "ch", "shes" to "sh",
                "men" to "man", "ies" to "y", "s" to "",
            )

            WordPos.VERB -> listOf(
                "ies" to "y", "es" to "e", "es" to "", "s" to "",
                "ed" to "e", "ed" to "", "ing" to "e", "ing" to "",
            )

            WordPos.ADJECTIVE -> listOf(
                "est" to "", "est" to "e", "er" to "", "er" to "e",
            )

            // WordNet has no adverb detachment rules; adverbs live in adv.exc only.
            WordPos.ADVERB -> emptyList()
        }

        /** "runn" → "run", but leaves "pass" and "bell" alone (those are real words). */
        fun undoubleConsonant(stem: String): String? {
            if (stem.length < 3) return null
            val last = stem[stem.lastIndex]
            if (last != stem[stem.lastIndex - 1]) return null
            if (last !in "bdfglmnprstz") return null
            return stem.dropLast(1)
        }
    }
}
