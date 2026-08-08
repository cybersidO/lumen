package com.sidharth.lumen.core.dictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §5 names the lemmatizer as one of four non-negotiable test targets, and §2.4 gives the
 * acceptance bar directly: "running" → "run", "mice" → "mouse".
 *
 * The fixture stands in for the real WordNet tables so the rules are tested in isolation:
 * a small set of known lemmas, and the handful of exception entries WordNet really ships
 * for these words (verified against the built database).
 */
class MorphyTest {

    private val knownLemmas: Set<Pair<String, WordPos>> = setOf(
        "run" to WordPos.VERB, "run" to WordPos.NOUN,
        "running" to WordPos.NOUN, "running" to WordPos.ADJECTIVE,
        "mouse" to WordPos.NOUN,
        "good" to WordPos.ADJECTIVE, "well" to WordPos.ADJECTIVE,
        "well" to WordPos.ADVERB, "better" to WordPos.ADJECTIVE,
        "box" to WordPos.NOUN, "church" to WordPos.NOUN, "dish" to WordPos.NOUN,
        "man" to WordPos.NOUN, "story" to WordPos.NOUN, "cat" to WordPos.NOUN,
        "carry" to WordPos.VERB, "hope" to WordPos.VERB, "walk" to WordPos.VERB,
        "stop" to WordPos.VERB, "big" to WordPos.ADJECTIVE,
        "pass" to WordPos.NOUN, "bell" to WordPos.NOUN,
    )

    /** The real entries from WordNet's noun.exc / verb.exc / adj.exc / adv.exc. */
    private val exceptions: Map<String, List<Pair<String, WordPos>>> = mapOf(
        "mice" to listOf("mouse" to WordPos.NOUN),
        "running" to listOf("run" to WordPos.VERB),
        "better" to listOf(
            "good" to WordPos.ADJECTIVE,
            "well" to WordPos.ADJECTIVE,
            "well" to WordPos.ADVERB,
        ),
        "men" to listOf("man" to WordPos.NOUN),
    )

    private val morphy = Morphy(
        isKnownLemma = { lemma, pos -> (lemma to pos) in knownLemmas },
        exceptionsFor = { surface -> exceptions[surface].orEmpty() },
    )

    private fun lemmas(word: String) = morphy.candidates(word).map { it.lemma }.distinct()

    // --- §2.4's three acceptance words --------------------------------------------

    @Test
    fun `running resolves to run`() {
        val result = lemmas("running")
        assertTrue("expected 'run' in $result", "run" in result)
        // It is also a headword in its own right, and that sense comes first.
        assertEquals("running", result.first())
    }

    @Test
    fun `mice resolves to mouse`() {
        assertEquals(listOf("mouse"), lemmas("mice"))
    }

    @Test
    fun `better resolves to good and well`() {
        val result = lemmas("better")
        assertTrue("expected 'good' in $result", "good" in result)
        assertTrue("expected 'well' in $result", "well" in result)
    }

    // --- regular plurals ------------------------------------------------------------

    @Test
    fun `regular noun plurals detach`() {
        assertTrue("cat" in lemmas("cats"))
        assertTrue("box" in lemmas("boxes"))
        assertTrue("church" in lemmas("churches"))
        assertTrue("dish" in lemmas("dishes"))
        assertTrue("story" in lemmas("stories"))
    }

    @Test
    fun `irregular men plural comes from the exception list`() {
        assertTrue("man" in lemmas("men"))
    }

    // --- verb inflections -----------------------------------------------------------

    @Test
    fun `verb inflections detach`() {
        assertTrue("walk" in lemmas("walks"))
        assertTrue("walk" in lemmas("walked"))
        assertTrue("walk" in lemmas("walking"))
        assertTrue("carry" in lemmas("carries"))
        assertTrue("hope" in lemmas("hoped"))
        assertTrue("hope" in lemmas("hoping"))
    }

    /**
     * The addition to WordNet's own rules. Without undoubling, "running" → "runn" and
     * "stopped" → "stopp", neither of which is a word, so the verb would be unreachable.
     */
    @Test
    fun `doubled consonants are undone`() {
        assertTrue("run" in lemmas("running"))
        assertTrue("stop" in lemmas("stopped"))
        assertTrue("stop" in lemmas("stopping"))
    }

    @Test
    fun `undoubling does not corrupt real words ending in a double letter`() {
        // "pass" and "bell" are themselves lemmas; they must survive untouched.
        assertEquals(listOf("pass"), lemmas("pass"))
        assertEquals(listOf("bell"), lemmas("bell"))
    }

    // --- comparatives ---------------------------------------------------------------

    @Test
    fun `adjective comparatives detach`() {
        assertTrue("big" in lemmas("bigger"))
    }

    // --- input hygiene --------------------------------------------------------------

    @Test
    fun `punctuation dragged in by a tap is trimmed`() {
        assertTrue("mouse" in lemmas("“mice,”"))
        assertTrue("mouse" in lemmas("mice."))
        assertTrue("mouse" in lemmas("  MICE  "))
    }

    @Test
    fun `interior hyphens and apostrophes are preserved`() {
        // Neither is a known lemma here, so the point is simply that normalisation did
        // not strip the interior character and turn them into something else.
        assertEquals(emptyList<String>(), lemmas("self-aware"))
        assertEquals(emptyList<String>(), lemmas("don't"))
    }

    @Test
    fun `an unknown word yields no candidates`() {
        assertEquals(emptyList<String>(), lemmas("zzzblarg"))
        assertEquals(emptyList<String>(), lemmas(""))
        assertEquals(emptyList<String>(), lemmas("   "))
    }

    @Test
    fun `candidates are deduplicated and ordered by confidence`() {
        val candidates = morphy.candidates("running")
        assertEquals(candidates.distinct(), candidates)
        assertEquals(Morphy.Origin.EXACT, candidates.first().origin)
    }
}
