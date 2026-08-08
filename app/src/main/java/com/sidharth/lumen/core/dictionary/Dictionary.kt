package com.sidharth.lumen.core.dictionary

/** One sense of a word: what it means, and an example if the source has one. */
data class Sense(
    val pos: WordPos,
    val senseNumber: Int,
    val definition: String,
    val examples: List<String>,
)

/** Everything the lookup sheet shows for one headword. */
data class DictionaryEntry(
    val headword: String,
    /** IPA, when the source has a pronunciation. WordNet alone has none. */
    val pronunciation: String?,
    val senses: List<Sense>,
    /** True when the reader's word was inflected and this is its base form. */
    val resolvedFrom: String? = null,
)

/**
 * The lookup contract. §2.4 asks for "multiple dictionaries pluggable behind one
 * interface; ship one, design for more (e.g. a medical dictionary I may add later)."
 *
 * A dictionary owns its own lemmatisation because that is not a shared concern: a medical
 * dictionary would want "-itis"/"-ectomy" morphology and its own irregulars, which have
 * nothing to do with WordNet's Morphy rules.
 */
interface Dictionary {
    /** Stable id, stored on every lookup event so the log stays meaningful. */
    val id: String

    val displayName: String

    /** Attribution shown in the sheet; licences require it and the user should see it. */
    val attribution: String

    /**
     * Entries for a word exactly as it appeared in the book — inflected, possibly with
     * punctuation attached. Empty means genuinely not found, which is what triggers the
     * web escape hatch.
     */
    suspend fun lookup(surfaceForm: String): List<DictionaryEntry>
}
