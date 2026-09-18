package com.sidharth.lumen.feature.stats

import com.sidharth.lumen.core.settings.HighlightStyle
import com.sidharth.lumen.core.settings.PageFlow
import com.sidharth.lumen.core.settings.ReadingSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingSettingsTest {
    @Test
    fun sanitisedClampsEveryRange() {
        val s = ReadingSettings(keepScreenOnMinutes = 999, warmTint = 3f, autoScrollSpeed = 0f).sanitised()
        assertEquals(ReadingSettings.MAX_KEEP_SCREEN_ON_MINUTES, s.keepScreenOnMinutes)
        assertEquals(1f, s.warmTint)
        assertEquals(ReadingSettings.MIN_AUTO_SCROLL_SPEED, s.autoScrollSpeed)
    }

    @Test
    fun unknownStorageKeysFallBackToDefaults() {
        assertEquals(HighlightStyle.FILL, HighlightStyle.fromStorageKey("neon"))
        assertEquals(PageFlow.PAGED, PageFlow.fromStorageKey(null))
        assertEquals(PageFlow.SCROLL, PageFlow.fromStorageKey("scroll"))
    }
}
