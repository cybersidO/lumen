package com.sidharth.lumen.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sidharth.lumen.core.settings.HighlightStyle
import com.sidharth.lumen.core.settings.PageFlow
import com.sidharth.lumen.core.settings.ReaderTypography
import com.sidharth.lumen.core.settings.ReadingSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M10's reading comforts, rendered for real and measured — the warm tint by sampling the
 * pixels it produces, the sheet by driving its controls the way a thumb would.
 */
@RunWith(AndroidJUnit4::class)
class ReaderComfortUiTest {

    @get:Rule
    val compose = createComposeRule()

    private fun centrePixel(tag: String): Int {
        val image = compose.onNodeWithTag(tag).captureToImage()
        return image.toPixelMap()[image.width / 2, image.height / 2].toArgb()
    }

    @Test
    fun warmTintAtFullStrengthIsAmberButStillLight() {
        compose.setContent {
            Box(Modifier.fillMaxSize().background(Color.White).testTag("page")) {
                WarmTintOverlay(strength = 1f)
            }
        }
        val argb = centrePixel("page")
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        // Warm: red is preserved, blue is pulled down.
        assertTrue("expected red > blue, got r=$r b=$b", r > b + 40)
        // But readable: the ceiling keeps it far from opaque orange.
        assertTrue("page went too dark: b=$b", b > 140)
        assertTrue("green should be only slightly reduced: g=$g", g > 190)
    }

    @Test
    fun warmTintOffDrawsNothing() {
        compose.setContent {
            Box(Modifier.fillMaxSize().background(Color.White).testTag("page")) {
                WarmTintOverlay(strength = 0f)
            }
        }
        assertEquals(Color.White.toArgb(), centrePixel("page"))
        compose.onNodeWithTag(WarmTintTag).assertDoesNotExist()
    }

    @Test
    fun draggingTheWarmTintSliderReportsAStrength() {
        var latest: ReadingSettings? = null
        compose.setContent {
            TypographySheet(
                typography = ReaderTypography.Defaults,
                reading = ReadingSettings.Defaults,
                onChange = {},
                onReadingChange = { latest = it },
            )
        }
        // Driven through the slider's accessibility action rather than a synthetic swipe:
        // it is the same path TalkBack takes, and it is not at the mercy of the scroll
        // container claiming the gesture first.
        compose.onNodeWithTag(TypographyWarmTintTag).performScrollTo()
        compose.onNodeWithTag(TypographyWarmTintTag)
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0.8f) }
        assertNotNull("slider should have reported a value", latest)
        assertEquals(0.8f, latest!!.warmTint, 0.01f)
    }

    @Test
    fun pageFlowAndHighlightStyleChipsReportTheirChoice() {
        var latest = ReadingSettings.Defaults
        compose.setContent {
            TypographySheet(
                typography = ReaderTypography.Defaults,
                reading = latest,
                onChange = {},
                onReadingChange = { latest = it },
            )
        }
        compose.onNodeWithText(PageFlow.SCROLL.displayName).performClick()
        assertEquals(PageFlow.SCROLL, latest.pageFlow)
        compose.onNodeWithText(HighlightStyle.UNDERLINE.displayName).performScrollTo().performClick()
        assertEquals(HighlightStyle.UNDERLINE, latest.highlightStyle)
    }
}
