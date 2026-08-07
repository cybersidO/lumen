package com.sidharth.lumen.core.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrightnessSettingsTest {

    @Test
    fun `top of the slider is full window brightness with no dim`() {
        val top = BrightnessSettings.fromSlider(1f)
        assertEquals(1f, top.windowBrightness!!, 1e-4f)
        assertEquals(0f, top.extraDim, 1e-4f)
    }

    @Test
    fun `the floor of the window range has zero brightness and zero dim`() {
        val atFloor = BrightnessSettings.fromSlider(BrightnessSettings.SUB_MINIMUM_TRAVEL)
        assertEquals(0f, atFloor.windowBrightness!!, 1e-4f)
        assertEquals(0f, atFloor.extraDim, 1e-4f)
    }

    /** §2.3: below the OS minimum, the black overlay takes over. */
    @Test
    fun `below the floor the overlay dims further`() {
        val below = BrightnessSettings.fromSlider(BrightnessSettings.SUB_MINIMUM_TRAVEL / 2f)
        assertEquals(0f, below.windowBrightness!!, 1e-4f)
        assertEquals(BrightnessSettings.MAX_EXTRA_DIM / 2f, below.extraDim, 1e-4f)

        val bottom = BrightnessSettings.fromSlider(0f)
        assertEquals(BrightnessSettings.MAX_EXTRA_DIM, bottom.extraDim, 1e-4f)
    }

    /** The screen must never be able to reach fully, unrecoverably black. */
    @Test
    fun `even the bottom of the range leaves the page faintly visible`() {
        assertTrue(BrightnessSettings.fromSlider(0f).extraDim < 1f)
    }

    @Test
    fun `slider round-trips through settings and back`() {
        listOf(0f, 0.1f, BrightnessSettings.SUB_MINIMUM_TRAVEL, 0.5f, 0.99f, 1f).forEach { v ->
            assertEquals(v, BrightnessSettings.fromSlider(v).toSlider(), 1e-3f)
        }
    }

    @Test
    fun `the mapping is continuous across the floor boundary`() {
        val justAbove = BrightnessSettings.fromSlider(BrightnessSettings.SUB_MINIMUM_TRAVEL + 0.001f)
        val justBelow = BrightnessSettings.fromSlider(BrightnessSettings.SUB_MINIMUM_TRAVEL - 0.001f)
        // No jump: both sides of the boundary are within a whisker of "0 brightness, 0 dim".
        assertEquals(0f, justAbove.windowBrightness!!, 0.01f)
        assertEquals(0f, justAbove.extraDim, 0.01f)
        assertEquals(0f, justBelow.windowBrightness!!, 0.01f)
        assertEquals(0f, justBelow.extraDim, 0.01f)
    }

    @Test
    fun `follow system reports a sensible slider start`() {
        val slider = BrightnessSettings.Defaults.toSlider()
        assertTrue(slider in 0.5f..0.9f)
    }

    @Test
    fun `sanitised clamps out-of-range values`() {
        val wild = BrightnessSettings(windowBrightness = 4f, extraDim = 2f).sanitised()
        assertEquals(1f, wild.windowBrightness!!, 1e-4f)
        assertEquals(BrightnessSettings.MAX_EXTRA_DIM, wild.extraDim, 1e-4f)
    }
}
