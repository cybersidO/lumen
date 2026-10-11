package com.sidharth.lumen.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageCounterTest {

    private fun resource(href: String, pages: Int, title: String? = null) =
        ResourcePages(href = href, pageCount = pages, title = title)

    @Test
    fun `total is every resource added up`() {
        val pages = PageCounter.count(
            listOf(
                resource("split_001.xhtml", 12),
                resource("split_002.xhtml", 30),
                resource("split_003.xhtml", 8),
            ),
        )
        assertEquals(50, pages.total)
        assertEquals(0, pages.notes)
        assertEquals(50, pages.mainText)
    }

    @Test
    fun `a notes section is counted separately and excluded from the text`() {
        val pages = PageCounter.count(
            listOf(
                resource("split_001.xhtml", 200, title = "Chapter One"),
                // The real-world case: an opaque file name and a readable TOC title.
                resource("split_014.xhtml", 58, title = "Notes"),
            ),
        )
        assertEquals(258, pages.total)
        assertEquals(58, pages.notes)
        assertEquals(200, pages.mainText)
        assertTrue(pages.hasNotes)
    }

    @Test
    fun `the file name is used when the TOC says nothing`() {
        val pages = PageCounter.count(
            listOf(
                resource("ch01.xhtml", 100),
                resource("footnotes-02.xhtml", 20),
                resource("endnotes.html", 15),
            ),
        )
        assertEquals(35, pages.notes)
    }

    // --- what must NOT be read as notes --------------------------------------------

    @Test
    fun `a bare singular note is front matter, not the note apparatus`() {
        // "A Note on the Translation" is a page of preface; counting it would inflate
        // exactly the number this feature exists to report.
        assertFalse(PageCounter.isNotes("pref.xhtml", "A Note on the Translation"))
    }

    @Test
    fun `words that merely contain note are not notes`() {
        assertFalse(PageCounter.isNotes("notebook.xhtml", "The Notebook"))
        assertFalse(PageCounter.isNotes("ch1.xhtml", "Denote and Connote"))
    }

    @Test
    fun `the path leading to a resource never decides it`() {
        // A publisher folder called "notes" must not make every chapter inside it notes.
        assertFalse(PageCounter.isNotes("OEBPS/notes/chapter-01.xhtml", "Chapter One"))
    }

    @Test
    fun `fragments and queries are ignored when reading the file name`() {
        assertTrue(PageCounter.isNotes("text/endnotes.xhtml#note-14", null))
        assertTrue(PageCounter.isNotes("text/notes.xhtml?v=2", null))
    }

    @Test
    fun `plural and compound forms are all recognised`() {
        listOf("Notes", "Endnotes", "Footnotes", "NOTES TO CHAPTER 3", "Rearnotes")
            .forEach { title ->
                assertTrue(title, PageCounter.isNotes("x.xhtml", title))
            }
    }

    // --- the degenerate cases -------------------------------------------------------

    @Test
    fun `a book that is nothing but notes is not reported as a notes split`() {
        // Nothing useful to say about "0 pages of text", so the UI must not offer it.
        val pages = PageCounter.count(listOf(resource("notes.xhtml", 40, title = "Notes")))
        assertEquals(40, pages.notes)
        assertEquals(0, pages.mainText)
        assertFalse(pages.hasNotes)
    }

    @Test
    fun `an empty publication counts to zero rather than failing`() {
        val pages = PageCounter.count(emptyList())
        assertEquals(BookPages.Empty, pages)
        assertFalse(pages.hasNotes)
    }
}
