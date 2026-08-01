package com.sidharth.lumen.core.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderTypographyTest {

    @Test
    fun `font size clamps to the supported range`() {
        val base = ReaderTypography.Defaults
        assertEquals(
            ReaderTypography.MAX_FONT_SIZE_PERCENT,
            base.withFontSizeClamped(9_999).fontSizePercent,
        )
        assertEquals(
            ReaderTypography.MIN_FONT_SIZE_PERCENT,
            base.withFontSizeClamped(-40).fontSizePercent,
        )
        assertEquals(140, base.withFontSizeClamped(140).fontSizePercent)
    }

    /** Readium takes multipliers, so a percentage must not leak through unconverted. */
    @Test
    fun `percentages convert to multipliers`() {
        assertEquals(1.0, ReaderTypography.Defaults.fontSizeMultiplier, 0.0001)
        assertEquals(
            1.5,
            ReaderTypography.Defaults.copy(fontSizePercent = 150).fontSizeMultiplier,
            0.0001,
        )
        assertEquals(
            0.75,
            ReaderTypography.Defaults.copy(pageMarginsPercent = 75).pageMarginsMultiplier,
            0.0001,
        )
    }

    @Test
    fun `storage keys round-trip and are frozen`() {
        ReaderFont.entries.forEach { font ->
            assertEquals(font, ReaderFont.fromStorageKey(font.storageKey))
        }
        ReaderTextAlign.entries.forEach { align ->
            assertEquals(align, ReaderTextAlign.fromStorageKey(align.storageKey))
        }
        assertEquals(listOf("serif", "sans", "dyslexic"), ReaderFont.entries.map { it.storageKey })
        assertEquals(listOf("justify", "start"), ReaderTextAlign.entries.map { it.storageKey })
    }

    @Test
    fun `unknown keys fall back to the defaults`() {
        assertEquals(ReaderFont.SERIF, ReaderFont.fromStorageKey("comic-sans"))
        assertEquals(ReaderFont.SERIF, ReaderFont.fromStorageKey(null))
        assertEquals(ReaderTextAlign.JUSTIFY, ReaderTextAlign.fromStorageKey(null))
    }

    /**
     * The reader's font-size edge drag (M3) steps by this amount; a step that doesn't
     * divide the range cleanly would strand the user just short of the maximum.
     */
    @Test
    fun `stepping from the default reaches both ends of the range exactly`() {
        var typography = ReaderTypography.Defaults
        repeat(100) {
            typography = typography.withFontSizeClamped(
                typography.fontSizePercent + ReaderTypography.FONT_SIZE_STEP_PERCENT,
            )
        }
        assertEquals(ReaderTypography.MAX_FONT_SIZE_PERCENT, typography.fontSizePercent)

        repeat(100) {
            typography = typography.withFontSizeClamped(
                typography.fontSizePercent - ReaderTypography.FONT_SIZE_STEP_PERCENT,
            )
        }
        assertEquals(ReaderTypography.MIN_FONT_SIZE_PERCENT, typography.fontSizePercent)
    }
}
