package com.sidharth.lumen.core.books

enum class BookFormat(val extension: String, val displayName: String) {
    EPUB("epub", "EPUB"),

    /**
     * Readable since M8. Rendering, page turns, progress and rectangle highlights work;
     * dictionary lookup does not, because the PDF engine's Java binding exposes no text
     * API — see DECISIONS.md and the README's PDF limits.
     */
    PDF("pdf", "PDF"),
    ;

    companion object {
        fun fromStorageName(name: String): BookFormat? =
            entries.firstOrNull { it.name == name }
    }
}
