package com.sidharth.lumen.feature.dictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §2.4 stores "the sentence it appeared in" with every lookup, and §2.6 turns that stored
 * sentence into the prompt for review. A wrong sentence makes the review card useless, so
 * the extraction is tested rather than assumed.
 */
class SentenceExtractorTest {

    private val passage =
        "The hallway smelt of boiled cabbage. Winston slipped quickly through the " +
            "glass doors. It was no use trying the lift."

    @Test
    fun `pulls out the sentence containing the word`() {
        assertEquals(
            "Winston slipped quickly through the glass doors.",
            SentenceExtractor.around(passage, "quickly"),
        )
    }

    @Test
    fun `handles a word in the first sentence`() {
        assertEquals(
            "The hallway smelt of boiled cabbage.",
            SentenceExtractor.around(passage, "cabbage"),
        )
    }

    @Test
    fun `handles a word in the last sentence`() {
        assertEquals(
            "It was no use trying the lift.",
            SentenceExtractor.around(passage, "lift"),
        )
    }

    @Test
    fun `matching ignores case and attached punctuation`() {
        assertEquals(
            "Winston slipped quickly through the glass doors.",
            SentenceExtractor.around(passage, "“Quickly,”"),
        )
    }

    /**
     * A break needs terminal punctuation *followed by whitespace*, so an abbreviation
     * does not chop the sentence in half.
     */
    @Test
    fun `an abbreviation does not split the sentence`() {
        val text = "She consulted Dr. Chandra about the readings."
        assertEquals(text, SentenceExtractor.around(text, "readings"))
    }

    @Test
    fun `collapses newlines so the stored sentence reads as one line`() {
        val text = "The wind was\n  vile that\n  morning."
        assertEquals("The wind was vile that morning.", SentenceExtractor.around(text, "vile"))
    }

    @Test
    fun `falls back to the passage when the word is not present`() {
        val result = SentenceExtractor.around(passage, "zzzz")
        assertTrue(result!!.startsWith("The hallway"))
    }

    @Test
    fun `text without terminal punctuation is returned whole`() {
        val text = "a fragment with no full stop"
        assertEquals(text, SentenceExtractor.around(text, "fragment"))
    }

    @Test
    fun `blank input yields null rather than an empty sentence`() {
        assertNull(SentenceExtractor.around("", "word"))
        assertNull(SentenceExtractor.around("   ", "word"))
    }

    @Test
    fun `very long sentences are capped`() {
        val long = "word " + "filler ".repeat(400)
        val result = SentenceExtractor.around(long, "word")!!
        assertTrue("was ${result.length}", result.length <= 400)
    }
}
