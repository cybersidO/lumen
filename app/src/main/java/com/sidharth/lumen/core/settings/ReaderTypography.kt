package com.sidharth.lumen.core.settings

/** The three faces §2.2 asks for. Values are Readium's own bundled families. */
enum class ReaderFont(val storageKey: String, val displayName: String) {
    SERIF("serif", "Serif"),
    SANS("sans", "Sans-serif"),

    /**
     * Readium ships OpenDyslexic with the navigator, so it needs no font asset of our own.
     * Atkinson Hyperlegible (the face preferred in DECISIONS.md) needs a bundled file and a
     * `fontFamilyDeclarations` entry; it is deferred rather than half-done.
     */
    DYSLEXIC("dyslexic", "OpenDyslexic"),
    ;

    companion object {
        fun fromStorageKey(key: String?): ReaderFont =
            entries.firstOrNull { it.storageKey == key } ?: SERIF
    }
}

enum class ReaderTextAlign(val storageKey: String, val displayName: String) {
    JUSTIFY("justify", "Justified"),
    START("start", "Ragged right"),
    ;

    companion object {
        fun fromStorageKey(key: String?): ReaderTextAlign =
            entries.firstOrNull { it.storageKey == key } ?: JUSTIFY
    }
}

/**
 * How the book's text is set. Stored app-wide rather than per-book: this is a statement
 * about the reader's eyes, not about any one title.
 *
 * Sizes are percentages because that is what Readium's EPUB preferences take, and because
 * a percentage survives a change of font family in a way that a point size does not.
 */
data class ReaderTypography(
    val font: ReaderFont = ReaderFont.SERIF,
    val fontSizePercent: Int = DEFAULT_FONT_SIZE_PERCENT,
    val lineHeight: Float = DEFAULT_LINE_HEIGHT,
    val pageMarginsPercent: Int = DEFAULT_MARGINS_PERCENT,
    val textAlign: ReaderTextAlign = ReaderTextAlign.JUSTIFY,
    val hyphens: Boolean = true,
) {
    companion object {
        const val MIN_FONT_SIZE_PERCENT = 60
        const val MAX_FONT_SIZE_PERCENT = 300
        const val FONT_SIZE_STEP_PERCENT = 10
        const val DEFAULT_FONT_SIZE_PERCENT = 100

        const val MIN_LINE_HEIGHT = 1.0f
        const val MAX_LINE_HEIGHT = 2.4f
        const val DEFAULT_LINE_HEIGHT = 1.4f

        const val MIN_MARGINS_PERCENT = 50
        const val MAX_MARGINS_PERCENT = 250
        const val DEFAULT_MARGINS_PERCENT = 100

        val Defaults = ReaderTypography()
    }

    fun withFontSizeClamped(percent: Int) = copy(
        fontSizePercent = percent.coerceIn(MIN_FONT_SIZE_PERCENT, MAX_FONT_SIZE_PERCENT),
    )

    /** Readium takes a multiplier, not a percentage. */
    val fontSizeMultiplier: Double get() = fontSizePercent / 100.0
    val pageMarginsMultiplier: Double get() = pageMarginsPercent / 100.0
}
