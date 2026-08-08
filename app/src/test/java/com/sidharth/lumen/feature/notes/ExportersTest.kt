package com.sidharth.lumen.feature.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §5 names export formatting as one of four non-negotiable test targets, and §2.6 sets the
 * bar: the Markdown must be "clean enough to drop straight into an Obsidian vault".
 *
 * The awkward cases here are the real ones — quotes containing commas, quotation marks and
 * newlines; titles with colons; multi-word tags — because those are what silently corrupt
 * an export rather than failing loudly.
 */
class ExportersTest {

    private val awkwardQuote =
        "It was a bright, cold day, and the clocks were \"striking\" thirteen.\nSo it began."

    private val book = ExportBook(
        title = "Nineteen Eighty-Four: A Novel",
        author = "George Orwell",
        contentHash = "abc123",
        highlights = listOf(
            ExportHighlight(
                quote = awkwardQuote,
                note = "The clocks are the tell.\nThirteen means something is off.",
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

    // --- Markdown ---------------------------------------------------------------------

    @Test
    fun `markdown starts with YAML frontmatter carrying the content hash`() {
        val md = Exporters.toMarkdown(book)
        val lines = md.lines()

        assertEquals("---", lines[0])
        assertTrue(md.contains("lumen-content-hash: abc123"))
        assertTrue(md.contains("highlights: 2"))
        // Exactly one closing delimiter for the frontmatter block.
        assertEquals(2, lines.count { it == "---" })
    }

    /** A colon in a title would otherwise make the frontmatter invalid YAML. */
    @Test
    fun `a title containing a colon is quoted in the frontmatter`() {
        val md = Exporters.toMarkdown(book)
        assertTrue(
            "title line was: ${md.lines().first { it.startsWith("title:") }}",
            md.contains("""title: "Nineteen Eighty-Four: A Novel""""),
        )
    }

    @Test
    fun `plain titles are not needlessly quoted`() {
        assertEquals("Middlemarch", Exporters.yamlScalar("Middlemarch"))
    }

    @Test
    fun `every line of a multi-line quote is blockquoted`() {
        val md = Exporters.toMarkdown(book)
        val quoteLines = md.lines().filter { it.startsWith(">") }
        assertTrue("expected both lines quoted, got $quoteLines", quoteLines.size >= 2)
        assertTrue(quoteLines.any { it.contains("bright, cold day") })
        assertTrue(quoteLines.any { it.contains("So it began.") })
    }

    @Test
    fun `the note follows its highlight unquoted`() {
        val md = Exporters.toMarkdown(book)
        assertTrue(md.contains("The clocks are the tell."))
        // The note must not be swept into the blockquote.
        assertFalse(md.contains("> The clocks are the tell."))
    }

    /** Obsidian tags cannot contain spaces; a split tag is a silently wrong import. */
    @Test
    fun `multi-word tags are hyphenated, not split`() {
        val md = Exporters.toMarkdown(book)
        assertTrue("expected #opening-line in:\n$md", md.contains("#opening-line"))
        assertTrue(md.contains("#dystopia"))
        assertFalse("a bare '#opening ' would be two tags", md.contains("#opening "))
    }

    @Test
    fun `tag slugs strip characters Obsidian rejects`() {
        assertEquals("two-words", Exporters.tagSlug("two words"))
        assertEquals("with-punct", Exporters.tagSlug("with, punct!"))
        assertEquals("keeps/nesting", Exporters.tagSlug("keeps/nesting"))
    }

    /**
     * Headings suppress *consecutive* repeats, which is exactly right because the export
     * query orders highlights by reading position — a chapter's highlights are contiguous.
     */
    @Test
    fun `a chapter heading is written once for a run of its highlights`() {
        val threeInChapterOne = book.copy(
            highlights = listOf(
                book.highlights[0],
                book.highlights[0].copy(quote = "second in chapter one"),
                book.highlights[1],
            ),
        )
        val md = Exporters.toMarkdown(threeInChapterOne)
        assertEquals(1, md.lines().count { it == "## Chapter 1" })
        assertEquals(1, md.lines().count { it == "## Chapter 7" })
        assertTrue(md.contains("second in chapter one"))
    }

    @Test
    fun `filenames drop characters that filesystems reject`() {
        val name = Exporters.markdownFileName(book.copy(title = "A/B: \"C\"?"))
        assertFalse(name.contains('/'))
        assertFalse(name.contains(':'))
        assertFalse(name.contains('"'))
        assertTrue(name.endsWith(".md"))
    }

    @Test
    fun `an untitled book still gets a usable filename`() {
        assertEquals("Untitled.md", Exporters.markdownFileName(book.copy(title = "   ")))
    }

    // --- CSV ---------------------------------------------------------------------------

    @Test
    fun `csv quotes every field and doubles embedded quotes`() {
        val csv = Exporters.toCsv(listOf(book))
        assertTrue(csv.contains("\"\"striking\"\""))
        // Header and both rows present.
        assertTrue(csv.startsWith("\"book\",\"author\""))
    }

    @Test
    fun `csv survives commas and newlines inside a quote`() {
        val csv = Exporters.toCsv(listOf(book))
        // The embedded newline stays inside the quoted field, so the row count by
        // *record* is what matters — verified by counting the quoted book titles.
        assertEquals(2, Regex("\"Nineteen Eighty-Four: A Novel\"").findAll(csv).count())
    }

    @Test
    fun `csv escaping is exact`() {
        assertEquals("\"plain\"", Exporters.csvField("plain"))
        assertEquals("\"say \"\"hi\"\"\"", Exporters.csvField("say \"hi\""))
    }

    // JSON output is verified in ExportersJsonTest (androidTest): `org.json` is only a
    // stub on the JVM unit-test classpath, and the real implementation lives on-device.
    // Adding a third-party JSON library purely to test the platform's own was the worse
    // trade.

    @Test
    fun `dates are written as UTC ISO-8601`() {
        assertEquals("2023-11-14T22:13:20Z", Exporters.isoDate(1_700_000_000_000))
    }

    @Test
    fun `an empty library still produces valid output`() {
        val empty = book.copy(highlights = emptyList())
        assertTrue(Exporters.toMarkdown(empty).contains("highlights: 0"))
        assertTrue(Exporters.toCsv(emptyList()).startsWith("\"book\""))
    }
}
