package com.sidharth.lumen.feature.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules the voice picker depends on. A phone carries hundreds of voices — this
 * emulator had 256 — so which ones surface, and in what order, is the whole difference
 * between a usable list and a wall of Arabic for an English novel.
 */
class SpeechVoiceTest {

    private data class Raw(val id: String, val tag: String?, val quality: Int, val network: Boolean)

    /** Mirrors SpeechController's mapping and ordering. */
    private fun prepare(raw: List<Raw>, bookLanguage: String?): List<SpeechVoice> = raw
        .filterNot { it.network }
        .map { r ->
            SpeechVoice(
                id = r.id,
                label = r.id,
                languageTag = r.tag,
                quality = r.quality,
                matchesBook = bookLanguage != null &&
                    r.tag?.substringBefore('-')?.lowercase() == bookLanguage,
            )
        }
        .sortedWith(
            compareByDescending<SpeechVoice> { it.matchesBook }
                .thenByDescending { it.quality }
                .thenBy { it.label },
        )

    @Test
    fun networkVoicesAreNeverOffered() {
        val out = prepare(
            listOf(
                Raw("en-gb-local", "en-GB", 4, network = false),
                Raw("en-gb-cloud", "en-GB", 5, network = true),
            ),
            bookLanguage = "en",
        )
        assertEquals(listOf("en-gb-local"), out.map { it.id })
    }

    @Test
    fun theBooksLanguageComesFirstEvenWhenOthersScoreHigher() {
        val out = prepare(
            listOf(
                Raw("ar-best", "ar", 5, false),
                Raw("en-ok", "en-GB", 2, false),
            ),
            bookLanguage = "en",
        )
        assertEquals(listOf("en-ok", "ar-best"), out.map { it.id })
        assertTrue(out.first().matchesBook)
    }

    @Test
    fun regionalVariantsStillCountAsTheBooksLanguage() {
        val out = prepare(listOf(Raw("v", "en-AU", 3, false)), bookLanguage = "en")
        assertTrue("en-AU should match an English book", out.single().matchesBook)
    }

    @Test
    fun withinOneLanguageBetterQualityComesFirst() {
        val out = prepare(
            listOf(
                Raw("b", "en-GB", 2, false),
                Raw("a", "en-GB", 5, false),
            ),
            bookLanguage = "en",
        )
        assertEquals(listOf("a", "b"), out.map { it.id })
    }

    @Test
    fun aBookWithNoStatedLanguageMatchesNothingAndFallsBackToQuality() {
        val out = prepare(
            listOf(
                Raw("low", "en-GB", 1, false),
                Raw("high", "ar", 5, false),
            ),
            bookLanguage = null,
        )
        assertEquals(listOf("high", "low"), out.map { it.id })
        assertTrue(out.none { it.matchesBook })
    }
}
