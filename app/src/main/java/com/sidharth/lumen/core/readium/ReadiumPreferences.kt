package com.sidharth.lumen.core.readium

import com.sidharth.lumen.core.settings.ReaderFont
import com.sidharth.lumen.core.settings.ReaderTextAlign
import com.sidharth.lumen.core.settings.ReaderTypography
import com.sidharth.lumen.core.settings.ResolvedTheme
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Color as ReadiumColor
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.TextAlign as ReadiumTextAlign
import org.readium.r2.navigator.preferences.Theme as ReadiumTheme

/**
 * Translates Lumen's settings into the preferences Readium's EPUB navigator understands.
 *
 * This is the whole of §2.7's "inject CSS into the book's rendering context, don't just
 * invert colours": Readium restyles the text through its own stylesheet, so images keep
 * their real colours instead of being negated into something unreadable.
 */
fun readiumPreferences(
    typography: ReaderTypography,
    theme: ResolvedTheme,
): EpubPreferences = EpubPreferences(
    fontFamily = typography.font.toReadium(),
    fontSize = typography.fontSizeMultiplier,
    lineHeight = typography.lineHeight.toDouble(),
    pageMargins = typography.pageMarginsMultiplier,
    textAlign = typography.textAlign.toReadium(),
    hyphens = typography.hyphens,
    theme = theme.toReadiumTheme(),
    // Readium's own themes have their own colours, which do not match Lumen's palettes —
    // the page would sit on pure black while the chrome around it is #121212. §2.7 wants
    // one theme across the app *and* the book, so the exact colours are always passed
    // through rather than left to Readium's defaults. This is also what makes AMOLED
    // possible at all: Readium has no AMOLED theme, only DARK.
    backgroundColor = ReadiumColor(theme.pageBackground()),
    textColor = ReadiumColor(theme.pageText()),
    // Publisher styles off, otherwise a book with its own CSS ignores the reader's
    // font size and line height — which is exactly what the user changed them for.
    publisherStyles = false,
    // Paginated, not scrolled (§2.2).
    scroll = false,
)

/** Kept in step with LumenColors; a book page is a surface like any other. */
private fun ResolvedTheme.pageBackground(): Int = when (this) {
    ResolvedTheme.LIGHT -> 0xFFFBF7F2.toInt()
    ResolvedTheme.DARK -> 0xFF121212.toInt()
    ResolvedTheme.AMOLED -> 0xFF000000.toInt()
    ResolvedTheme.SEPIA -> 0xFFF4ECD8.toInt()
}

private fun ResolvedTheme.pageText(): Int = when (this) {
    ResolvedTheme.LIGHT -> 0xFF1B1815.toInt()
    ResolvedTheme.DARK, ResolvedTheme.AMOLED -> 0xFFE7E1D8.toInt()
    ResolvedTheme.SEPIA -> 0xFF4A3B2A.toInt()
}

private fun ReaderFont.toReadium(): FontFamily = when (this) {
    ReaderFont.SERIF -> FontFamily.SERIF
    ReaderFont.SANS -> FontFamily.SANS_SERIF
    ReaderFont.DYSLEXIC -> FontFamily.OPEN_DYSLEXIC
}

private fun ReaderTextAlign.toReadium(): ReadiumTextAlign = when (this) {
    ReaderTextAlign.JUSTIFY -> ReadiumTextAlign.JUSTIFY
    ReaderTextAlign.START -> ReadiumTextAlign.START
}

private fun ResolvedTheme.toReadiumTheme(): ReadiumTheme = when (this) {
    ResolvedTheme.LIGHT -> ReadiumTheme.LIGHT
    ResolvedTheme.DARK, ResolvedTheme.AMOLED -> ReadiumTheme.DARK
    ResolvedTheme.SEPIA -> ReadiumTheme.SEPIA
}
