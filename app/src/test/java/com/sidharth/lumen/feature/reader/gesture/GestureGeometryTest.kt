package com.sidharth.lumen.feature.reader.gesture

import com.sidharth.lumen.core.settings.GestureSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class GestureGeometryTest {

    private val defaults = GestureSettings.Defaults

    /**
     * The shipped defaults turn all three gestures off (1.6.0), so the tests that cover
     * the *mechanism* have to ask for them explicitly. The tests below named after the
     * defaults are the ones that pin the shipped behaviour.
     */
    private val allEnabled = GestureSettings.Defaults.copy(
        brightnessDragEnabled = true,
        fontSizeDragEnabled = true,
        tapToTurnPageEnabled = true,
    )
    private val width = 1000f

    // --- zones ---------------------------------------------------------------------

    @Test
    fun `zones split the width the way the spec draws them`() {
        // 15% edges, 25% tap zones, centre in between.
        assertEquals(TouchZone.LEFT_EDGE_STRIP, GestureGeometry.zoneFor(0f, width, defaults))
        assertEquals(TouchZone.LEFT_EDGE_STRIP, GestureGeometry.zoneFor(149f, width, defaults))
        assertEquals(TouchZone.LEFT_TAP, GestureGeometry.zoneFor(151f, width, defaults))
        assertEquals(TouchZone.LEFT_TAP, GestureGeometry.zoneFor(249f, width, defaults))
        assertEquals(TouchZone.CENTRE_TAP, GestureGeometry.zoneFor(251f, width, defaults))
        assertEquals(TouchZone.CENTRE_TAP, GestureGeometry.zoneFor(500f, width, defaults))
        assertEquals(TouchZone.CENTRE_TAP, GestureGeometry.zoneFor(749f, width, defaults))
        assertEquals(TouchZone.RIGHT_TAP, GestureGeometry.zoneFor(751f, width, defaults))
        assertEquals(TouchZone.RIGHT_EDGE_STRIP, GestureGeometry.zoneFor(851f, width, defaults))
        assertEquals(TouchZone.RIGHT_EDGE_STRIP, GestureGeometry.zoneFor(999f, width, defaults))
    }

    @Test
    fun `edge strips win where they overlap tap zones`() {
        // x=100 is inside both the 15% edge strip and the 25% tap zone; the strip wins,
        // because a drag started there must never turn a page.
        assertEquals(TouchZone.LEFT_EDGE_STRIP, GestureGeometry.zoneFor(100f, width, defaults))
    }

    @Test
    fun `degenerate width does not crash and lands in the centre`() {
        assertEquals(TouchZone.CENTRE_TAP, GestureGeometry.zoneFor(10f, 0f, defaults))
    }

    // --- taps ----------------------------------------------------------------------

    @Test
    fun `taps map left-previous right-next in LTR`() {
        assertEquals(
            TapAction.PREVIOUS_PAGE,
            GestureGeometry.tapActionFor(TouchZone.LEFT_TAP, allEnabled, isRtl = false),
        )
        assertEquals(
            TapAction.NEXT_PAGE,
            GestureGeometry.tapActionFor(TouchZone.RIGHT_TAP, allEnabled, isRtl = false),
        )
        assertEquals(
            TapAction.TOGGLE_CHROME,
            GestureGeometry.tapActionFor(TouchZone.CENTRE_TAP, allEnabled, isRtl = false),
        )
    }

    /** §2.3: in RTL books the page-turn direction inverts. */
    @Test
    fun `taps invert in RTL`() {
        assertEquals(
            TapAction.NEXT_PAGE,
            GestureGeometry.tapActionFor(TouchZone.LEFT_TAP, allEnabled, isRtl = true),
        )
        assertEquals(
            TapAction.PREVIOUS_PAGE,
            GestureGeometry.tapActionFor(TouchZone.RIGHT_TAP, allEnabled, isRtl = true),
        )
        // The centre is direction-neutral.
        assertEquals(
            TapAction.TOGGLE_CHROME,
            GestureGeometry.tapActionFor(TouchZone.CENTRE_TAP, allEnabled, isRtl = true),
        )
    }

    /**
     * The 1.6.0 request, in one test: no tap anywhere may change the chapter.
     *
     * Every zone toggles the chrome instead of going dead — a tap that does nothing reads
     * as the app ignoring you.
     */
    @Test
    fun `with tap-to-turn off every zone only shows the bars`() {
        TouchZone.entries.forEach { zone ->
            listOf(false, true).forEach { rtl ->
                assertEquals(
                    "$zone, rtl=$rtl",
                    TapAction.TOGGLE_CHROME,
                    GestureGeometry.tapActionFor(zone, defaults, isRtl = rtl),
                )
            }
        }
    }

    @Test
    fun `the shipped defaults turn page-turning and both edge drags off`() {
        assertEquals(false, defaults.tapToTurnPageEnabled)
        assertEquals(
            DragGesture.NONE,
            GestureGeometry.dragGestureFor(TouchZone.LEFT_EDGE_STRIP, defaults),
        )
        assertEquals(
            DragGesture.NONE,
            GestureGeometry.dragGestureFor(TouchZone.RIGHT_EDGE_STRIP, defaults),
        )
    }

    // --- drags ---------------------------------------------------------------------

    /** §2.3: brightness/font-size edges do NOT swap in RTL — they are physical sides. */
    @Test
    fun `drag gestures are fixed to physical edges`() {
        assertEquals(
            DragGesture.BRIGHTNESS,
            GestureGeometry.dragGestureFor(TouchZone.LEFT_EDGE_STRIP, allEnabled),
        )
        assertEquals(
            DragGesture.FONT_SIZE,
            GestureGeometry.dragGestureFor(TouchZone.RIGHT_EDGE_STRIP, allEnabled),
        )
        assertEquals(
            DragGesture.NONE,
            GestureGeometry.dragGestureFor(TouchZone.CENTRE_TAP, allEnabled),
        )
    }

    @Test
    fun `disabled drags resolve to none`() {
        val noBrightness = allEnabled.copy(brightnessDragEnabled = false)
        val noFontSize = allEnabled.copy(fontSizeDragEnabled = false)
        assertEquals(
            DragGesture.NONE,
            GestureGeometry.dragGestureFor(TouchZone.LEFT_EDGE_STRIP, noBrightness),
        )
        assertEquals(
            DragGesture.NONE,
            GestureGeometry.dragGestureFor(TouchZone.RIGHT_EDGE_STRIP, noFontSize),
        )
    }

    // --- the directional lock --------------------------------------------------------

    @Test
    fun `axis stays undecided inside the slop radius`() {
        assertEquals(DragAxis.UNDECIDED, GestureGeometry.decideAxis(5f, 5f, touchSlop = 24f))
        assertEquals(DragAxis.UNDECIDED, GestureGeometry.decideAxis(-23f, 0f, touchSlop = 24f))
    }

    @Test
    fun `axis commits to the dominant direction once slop is exceeded`() {
        assertEquals(DragAxis.VERTICAL, GestureGeometry.decideAxis(10f, 40f, touchSlop = 24f))
        assertEquals(DragAxis.VERTICAL, GestureGeometry.decideAxis(10f, -40f, touchSlop = 24f))
        assertEquals(DragAxis.HORIZONTAL, GestureGeometry.decideAxis(40f, 10f, touchSlop = 24f))
        assertEquals(DragAxis.HORIZONTAL, GestureGeometry.decideAxis(-40f, -10f, touchSlop = 24f))
    }

    // --- brightness maths --------------------------------------------------------------

    @Test
    fun `dragging up brightens and dragging down dims`() {
        val height = 2000f
        // dy is negative when the finger moves up.
        assertEquals(0.75f, GestureGeometry.brightnessFor(0.5f, dy = -500f, height = height), 1e-4f)
        assertEquals(0.25f, GestureGeometry.brightnessFor(0.5f, dy = 500f, height = height), 1e-4f)
    }

    @Test
    fun `brightness clamps at both ends and survives zero height`() {
        assertEquals(1f, GestureGeometry.brightnessFor(0.9f, dy = -900f, height = 2000f), 1e-4f)
        assertEquals(0f, GestureGeometry.brightnessFor(0.1f, dy = 900f, height = 2000f), 1e-4f)
        assertEquals(0.4f, GestureGeometry.brightnessFor(0.4f, dy = 100f, height = 0f), 1e-4f)
    }

    // --- font size steps ----------------------------------------------------------------

    @Test
    fun `font size steps once per 40dp of travel, upward = larger`() {
        val density = 2.5f // 40dp = 100px
        assertEquals(0, GestureGeometry.fontSizeStepsFor(dy = -99f, density = density))
        assertEquals(1, GestureGeometry.fontSizeStepsFor(dy = -100f, density = density))
        assertEquals(3, GestureGeometry.fontSizeStepsFor(dy = -350f, density = density))
        assertEquals(-2, GestureGeometry.fontSizeStepsFor(dy = 250f, density = density))
        assertEquals(0, GestureGeometry.fontSizeStepsFor(dy = -100f, density = 0f))
    }
}
