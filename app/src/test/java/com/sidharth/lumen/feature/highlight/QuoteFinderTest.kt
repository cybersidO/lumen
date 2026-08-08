package com.sidharth.lumen.feature.highlight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §2.5 names this path explicitly: "Write unit tests for the fallback path specifically."
 * These are those tests. Every case models a real way an EPUB's text drifts between the
 * moment a highlight was made and the moment it must be re-anchored.
 */
class QuoteFinderTest {

    private val document = """
        It was a bright cold day in April, and the clocks were striking thirteen.
        Winston Smith, his chin nuzzled into his breast in an effort to escape the
        vile wind, slipped quickly through the glass doors of Victory Mansions,
        though not quickly enough to prevent a swirl of gritty dust from entering
        along with him. The hallway smelt of boiled cabbage and old rag mats.
    """.trimIndent()

    private fun slice(match: QuoteFinder.Match?) =
        match?.let { document.substring(it.start, it.end) }

    // --- the straightforward cases ---------------------------------------------------

    @Test
    fun `a unique quote is found without needing context`() {
        val match = QuoteFinder.find(document, prefix = "", exact = "Victory Mansions", suffix = "")
        assertNotNull(match)
        assertEquals("Victory Mansions", slice(match))
    }

    @Test
    fun `context confirms a unique quote when present`() {
        val match = QuoteFinder.find(
            document,
            prefix = "the glass doors of ",
            exact = "Victory Mansions",
            suffix = ",\nthough not quickly",
        )
        assertNotNull(match)
        assertTrue(match!!.contextMatched)
    }

    @Test
    fun `an absent quote returns null rather than a guess`() {
        assertNull(QuoteFinder.find(document, "", "Room 101", ""))
    }

    @Test
    fun `empty inputs are refused`() {
        assertNull(QuoteFinder.find(document, "a", "", "b"))
        assertNull(QuoteFinder.find("", "a", "quote", "b"))
    }

    // --- disambiguation: the reason prefix and suffix exist ---------------------------

    @Test
    fun `repeated quotes resolve by prefix and suffix context`() {
        // "quickly" appears twice; the context must select the second occurrence.
        val match = QuoteFinder.find(
            document,
            prefix = "though not ",
            exact = "quickly",
            suffix = " enough to prevent",
        )
        assertNotNull(match)
        assertEquals("quickly", slice(match))
        // The second "quickly" (inside "though not quickly enough"), not the first
        // ("slipped quickly through").
        assertTrue(match!!.start > document.indexOf("slipped quickly"))
        assertTrue(match.contextMatched)
    }

    @Test
    fun `repeated quotes with the other context pick the first occurrence`() {
        val match = QuoteFinder.find(
            document,
            prefix = "slipped ",
            exact = "quickly",
            suffix = " through the glass",
        )
        assertNotNull(match)
        assertEquals(document.indexOf("quickly"), match!!.start)
    }

    /** A wrong highlight is worse than a lost one. */
    @Test
    fun `ambiguous repeats with no matching context return null`() {
        val doc = "the cat sat. the cat sat. the cat sat."
        assertNull(QuoteFinder.find(doc, prefix = "ZZZ", exact = "cat", suffix = "QQQ"))
    }

    @Test
    fun `partial context still disambiguates`() {
        // Context that only partially survives (say the preceding word changed) still
        // scores higher at the right occurrence than at the wrong ones.
        val match = QuoteFinder.find(
            document,
            prefix = "XXXX not ", // first half wrong, " not " tail still matches
            exact = "quickly",
            suffix = " enough",
        )
        assertNotNull(match)
        assertTrue(match!!.start > document.indexOf("slipped quickly"))
    }

    // --- whitespace drift: the most common real-world change ---------------------------

    @Test
    fun `a quote that spans a reflowed line break is still found`() {
        // The stored quote has a space where the document now has a newline.
        val match = QuoteFinder.find(
            document,
            prefix = "",
            exact = "escape the vile wind",
            suffix = "",
        )
        assertNotNull(match)
        // The matched span covers the newline in the original document.
        assertTrue(slice(match)!!.contains("\n"))
        assertTrue(slice(match)!!.startsWith("escape the"))
        assertTrue(slice(match)!!.endsWith("vile wind"))
    }

    @Test
    fun `runs of spaces in the stored quote do not defeat the search`() {
        val match = QuoteFinder.find(
            document,
            prefix = "",
            exact = "bright  cold   day",
            suffix = "",
        )
        assertNotNull(match)
        assertEquals("bright cold day", slice(match))
    }

    @Test
    fun `whitespace-tolerant matching still respects context disambiguation`() {
        val doc = "alpha beta gamma. alpha\nbeta gamma. alpha beta delta."
        val match = QuoteFinder.find(
            doc,
            prefix = "gamma. ",
            exact = "alpha beta",
            suffix = " gamma",
        )
        assertNotNull(match)
        // Must be the second occurrence — the one followed by " gamma", spelled with a
        // newline in the document.
        assertEquals(doc.indexOf("alpha\nbeta"), match!!.start)
    }

    // --- honesty of the contextMatched flag --------------------------------------------

    @Test
    fun `a bare unique match reports that context did not confirm it`() {
        val match = QuoteFinder.find(
            document,
            prefix = "COMPLETELY WRONG",
            exact = "boiled cabbage",
            suffix = "ALSO WRONG",
        )
        assertNotNull(match)
        assertFalse(match!!.contextMatched)
    }

    @Test
    fun `normalizeText collapses whitespace the same way for quote and document`() {
        assertEquals("a b c", QuoteFinder.normalizeText("  a\n\tb   c "))
    }
}
