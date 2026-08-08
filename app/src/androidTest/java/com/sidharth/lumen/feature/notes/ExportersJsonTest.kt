package com.sidharth.lumen.feature.notes

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The JSON half of the export tests, on-device.
 *
 * `org.json` on the JVM unit-test classpath is a stub that throws "not mocked" — the real
 * implementation is part of the platform. Rather than pull in a third-party JSON library
 * only to test the one Android already provides, these assertions run where the real
 * `org.json` lives. Markdown and CSV stay as fast JVM tests in `ExportersTest`.
 */
@RunWith(AndroidJUnit4::class)
class ExportersJsonTest {

    private val awkwardQuote =
        "It was a bright, cold day, and the clocks were \"striking\" thirteen.\nSo it began."

    private val book = ExportBook(
        title = "Nineteen Eighty-Four: A Novel",
        author = "George Orwell",
        contentHash = "abc123",
        highlights = listOf(
            ExportHighlight(
                quote = awkwardQuote,
                note = "The clocks are the tell.",
                tags = listOf("opening line", "dystopia"),
                colorName = "Yellow",
                chapterTitle = "Chapter 1",
                progressFraction = 0.012f,
                createdAt = 1_700_000_000_000,
            ),
            ExportHighlight(
                quote = "Freedom is the freedom to say that two plus two make four.",
                note = null,
                tags = emptyList(),
                colorName = "Blue",
                chapterTitle = "Chapter 7",
                progressFraction = 0.51f,
                createdAt = 1_700_000_100_000,
            ),
        ),
    )

    @Test
    fun jsonRoundTripsTheStructureLosslessly() {
        val lookups = listOf(
            ExportLookup(
                word = "mice",
                lemma = "mouse",
                bookTitle = "Nineteen Eighty-Four",
                chapterTitle = "Chapter 1",
                sentence = "The mice were everywhere.",
                found = true,
                createdAt = 1_700_000_200_000,
            ),
        )
        val parsed = JSONObject(Exporters.toJson(listOf(book), lookups))

        val books = parsed.getJSONArray("books")
        assertEquals(1, books.length())
        assertEquals("abc123", books.getJSONObject(0).getString("contentHash"))

        val highlights = books.getJSONObject(0).getJSONArray("highlights")
        assertEquals(2, highlights.length())
        // Newlines and quotation marks survive the round trip untouched.
        assertEquals(awkwardQuote, highlights.getJSONObject(0).getString("quote"))
        assertEquals(2, highlights.getJSONObject(0).getJSONArray("tags").length())
        // A highlight with no note serialises as null, not the string "null".
        assertTrue(highlights.getJSONObject(1).isNull("note"))

        val lookupJson = parsed.getJSONArray("lookups").getJSONObject(0)
        assertEquals("mouse", lookupJson.getString("lemma"))
        assertTrue(lookupJson.getBoolean("found"))
    }

    @Test
    fun anEmptyLibraryStillProducesValidJson() {
        val parsed = JSONObject(Exporters.toJson(emptyList(), emptyList()))
        assertEquals(0, parsed.getJSONArray("books").length())
        assertEquals(0, parsed.getJSONArray("lookups").length())
    }
}
