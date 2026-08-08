package com.sidharth.lumen.feature.notes

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** One exportable highlight, flattened with its note and tags. */
data class ExportHighlight(
    val quote: String,
    val note: String?,
    val tags: List<String>,
    val colorName: String,
    val chapterTitle: String?,
    val progressFraction: Float,
    val createdAt: Long,
)

/** One book's worth of highlights, which is the unit the Markdown export writes. */
data class ExportBook(
    val title: String,
    val author: String?,
    val contentHash: String,
    val highlights: List<ExportHighlight>,
)

data class ExportLookup(
    val word: String,
    val lemma: String?,
    val bookTitle: String?,
    val chapterTitle: String?,
    val sentence: String?,
    val found: Boolean,
    val createdAt: Long,
)

/**
 * §2.6's three export formats. All pure string generation — no Android, no file I/O — so
 * the formatting is unit-testable, which §5 requires.
 */
object Exporters {

    /**
     * Markdown, one file per book, "clean enough to drop straight into an Obsidian vault"
     * (§2.6): YAML frontmatter, each highlight a blockquote, its note beneath it, tags as
     * `#tags`.
     */
    fun toMarkdown(book: ExportBook): String = buildString {
        appendLine("---")
        appendLine("title: ${yamlScalar(book.title)}")
        appendLine("author: ${yamlScalar(book.author ?: "Unknown")}")
        appendLine("highlights: ${book.highlights.size}")
        appendLine("exported: ${isoDate(System.currentTimeMillis())}")
        // The content hash identifies the book across re-imports, so a re-export lands on
        // the same note rather than creating a duplicate in the vault.
        appendLine("lumen-content-hash: ${book.contentHash}")
        appendLine("---")
        appendLine()
        appendLine("# ${book.title}")
        book.author?.let { appendLine("*$it*") }
        appendLine()

        // Suppresses *consecutive* repeats. Highlights are exported in reading order
        // (see NotesDao.highlightsForExport, ordered by progress), so a chapter's
        // highlights are contiguous and one heading per chapter falls out naturally.
        var lastChapter: String? = null
        book.highlights.forEach { highlight ->
            if (highlight.chapterTitle != null && highlight.chapterTitle != lastChapter) {
                appendLine("## ${highlight.chapterTitle}")
                appendLine()
                lastChapter = highlight.chapterTitle
            }

            // Blockquote, with every line prefixed so multi-line quotes stay quoted.
            highlight.quote.trim().lines().forEach { line -> appendLine("> ${line.trim()}") }
            appendLine()

            highlight.note?.takeIf { it.isNotBlank() }?.let { note ->
                note.trim().lines().forEach { appendLine(it.trim()) }
                appendLine()
            }

            if (highlight.tags.isNotEmpty()) {
                appendLine(highlight.tags.joinToString(" ") { "#${tagSlug(it)}" })
                appendLine()
            }
        }
    }

    /**
     * CSV, RFC 4180 quoting: every field quoted, embedded quotes doubled. Quotes routinely
     * contain commas, newlines and quotation marks, so anything less corrupts the file.
     */
    fun toCsv(books: List<ExportBook>): String = buildString {
        appendLine(
            csvRow(
                "book", "author", "chapter", "quote", "note", "tags",
                "colour", "progress", "created",
            ),
        )
        books.forEach { book ->
            book.highlights.forEach { highlight ->
                appendLine(
                    csvRow(
                        book.title,
                        book.author.orEmpty(),
                        highlight.chapterTitle.orEmpty(),
                        highlight.quote,
                        highlight.note.orEmpty(),
                        highlight.tags.joinToString(" "),
                        highlight.colorName,
                        "%.4f".format(Locale.ROOT, highlight.progressFraction),
                        isoDate(highlight.createdAt),
                    ),
                )
            }
        }
    }

    /** JSON, the lossless one — everything the other two formats flatten away. */
    fun toJson(books: List<ExportBook>, lookups: List<ExportLookup>): String {
        val root = JSONObject()
        root.put("exportedAt", isoDate(System.currentTimeMillis()))
        root.put("schema", 1)

        root.put(
            "books",
            JSONArray().apply {
                books.forEach { book ->
                    put(
                        JSONObject().apply {
                            put("title", book.title)
                            put("author", book.author ?: JSONObject.NULL)
                            put("contentHash", book.contentHash)
                            put(
                                "highlights",
                                JSONArray().apply {
                                    book.highlights.forEach { highlight ->
                                        put(
                                            JSONObject().apply {
                                                put("quote", highlight.quote)
                                                put("note", highlight.note ?: JSONObject.NULL)
                                                put("tags", JSONArray(highlight.tags))
                                                put("colour", highlight.colorName)
                                                put(
                                                    "chapter",
                                                    highlight.chapterTitle ?: JSONObject.NULL,
                                                )
                                                put("progress", highlight.progressFraction)
                                                put("created", isoDate(highlight.createdAt))
                                            },
                                        )
                                    }
                                },
                            )
                        },
                    )
                }
            },
        )

        root.put(
            "lookups",
            JSONArray().apply {
                lookups.forEach { lookup ->
                    put(
                        JSONObject().apply {
                            put("word", lookup.word)
                            put("lemma", lookup.lemma ?: JSONObject.NULL)
                            put("book", lookup.bookTitle ?: JSONObject.NULL)
                            put("chapter", lookup.chapterTitle ?: JSONObject.NULL)
                            put("sentence", lookup.sentence ?: JSONObject.NULL)
                            put("found", lookup.found)
                            put("created", isoDate(lookup.createdAt))
                        },
                    )
                }
            },
        )

        return root.toString(2)
    }

    /** A filename safe on every filesystem, derived from the book's title. */
    fun markdownFileName(book: ExportBook): String {
        val safe = book.title
            .replace(UNSAFE_FILENAME_CHARS, " ")
            .replace(WHITESPACE, " ")
            .trim()
            .take(80)
            .ifBlank { "Untitled" }
        return "$safe.md"
    }

    // --- helpers ---------------------------------------------------------------------

    /**
     * Obsidian tags cannot contain spaces, so a multi-word tag becomes hyphenated rather
     * than silently splitting into two unrelated tags at import.
     */
    internal fun tagSlug(tag: String): String =
        tag.trim().replace(WHITESPACE, "-").replace(UNSAFE_TAG_CHARS, "")

    /** Quotes a YAML scalar only when it needs it, so ordinary titles stay readable. */
    internal fun yamlScalar(value: String): String {
        val needsQuoting = value.isEmpty() ||
            value.first().isWhitespace() ||
            value.last().isWhitespace() ||
            value.any { it in ":#[]{}&*!|>%@`\"'\n" }
        return if (needsQuoting) "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\"" else value
    }

    internal fun csvField(value: String): String =
        "\"${value.replace("\"", "\"\"")}\""

    private fun csvRow(vararg fields: String): String =
        fields.joinToString(",") { csvField(it) }

    internal fun isoDate(epochMillis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(epochMillis))

    private val WHITESPACE = Regex("\\s+")
    private val UNSAFE_FILENAME_CHARS = Regex("[/\\\\:*?\"<>|\\u0000-\\u001f]")
    private val UNSAFE_TAG_CHARS = Regex("[^\\w\\-/]")
}
