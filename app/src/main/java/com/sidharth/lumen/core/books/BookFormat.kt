package com.sidharth.lumen.core.books

enum class BookFormat(val extension: String, val displayName: String) {
    EPUB("epub", "EPUB"),

    /**
     * Declared so storage paths and the `format` column don't need a migration in M8, but
     * import refuses PDFs today rather than accepting a file it cannot open. See
     * [BookImporter].
     */
    PDF("pdf", "PDF"),
    ;

    companion object {
        fun fromStorageName(name: String): BookFormat? =
            entries.firstOrNull { it.name == name }
    }
}
