package com.sidharth.lumen.feature.reader.gesture

import com.sidharth.lumen.core.settings.GestureSettings
import kotlin.math.abs

/** Where a touch started, horizontally. Edge strips win over tap zones where they overlap. */
enum class TouchZone {
    LEFT_EDGE_STRIP,
    RIGHT_EDGE_STRIP,
    LEFT_TAP,
    CENTRE_TAP,
    RIGHT_TAP,
}

/** What a tap in a zone should do, after RTL has been accounted for. */
enum class TapAction {
    PREVIOUS_PAGE,
    NEXT_PAGE,
    TOGGLE_CHROME,
    NONE,
}

/** The axis a drag committed to. Decided once, on first significant movement, then kept. */
enum class DragAxis {
    UNDECIDED,
    HORIZONTAL,
    VERTICAL,
}

/** What a committed vertical drag controls, decided by where it began. */
enum class DragGesture {
    NONE,
    BRIGHTNESS,
    FONT_SIZE,
}

/**
 * Pure gesture maths, deliberately free of Android types.
 *
 * All of §2.3's fiddly rules live here — zone precedence, the directional lock, RTL
 * inversion, the font-size step threshold — so they can be tested without a device. The
 * View that uses this does nothing but feed it coordinates.
 */
object GestureGeometry {

    /** §2.3: font size steps once per ~40dp of travel. */
    const val FONT_SIZE_STEP_DP = 40f

    fun zoneFor(x: Float, width: Float, settings: GestureSettings): TouchZone {
        if (width <= 0f) return TouchZone.CENTRE_TAP
        val fraction = (x / width).coerceIn(0f, 1f)
        val edge = settings.edgeStripFraction
        val tap = settings.tapZoneFraction

        // Edge strips take precedence: a drag starting there must never turn a page.
        return when {
            fraction < edge -> TouchZone.LEFT_EDGE_STRIP
            fraction > 1f - edge -> TouchZone.RIGHT_EDGE_STRIP
            fraction < tap -> TouchZone.LEFT_TAP
            fraction > 1f - tap -> TouchZone.RIGHT_TAP
            else -> TouchZone.CENTRE_TAP
        }
    }

    /**
     * A tap's meaning. In RTL the page-turn sides swap, because "next" is leftward —
     * but the brightness and font-size edges do *not* swap (§2.3), which is why they are
     * resolved separately in [dragGestureFor].
     */
    fun tapActionFor(zone: TouchZone, settings: GestureSettings, isRtl: Boolean): TapAction {
        // With page turning off, the whole page toggles the chrome rather than leaving
        // dead strips down both sides: a tap that does nothing reads as the app ignoring
        // you, and the bar is the only thing a tap has left to do.
        if (!settings.tapToTurnPageEnabled) return TapAction.TOGGLE_CHROME
        return when (zone) {
            TouchZone.LEFT_EDGE_STRIP, TouchZone.LEFT_TAP ->
                if (isRtl) TapAction.NEXT_PAGE else TapAction.PREVIOUS_PAGE

            TouchZone.RIGHT_EDGE_STRIP, TouchZone.RIGHT_TAP ->
                if (isRtl) TapAction.PREVIOUS_PAGE else TapAction.NEXT_PAGE

            TouchZone.CENTRE_TAP -> TapAction.TOGGLE_CHROME
        }
    }

    /**
     * Which vertical-drag gesture a touch owns. Physical edges, never mirrored: the
     * brightness strip stays on the left hand in an RTL book.
     */
    fun dragGestureFor(zone: TouchZone, settings: GestureSettings): DragGesture = when (zone) {
        TouchZone.LEFT_EDGE_STRIP ->
            if (settings.brightnessDragEnabled) DragGesture.BRIGHTNESS else DragGesture.NONE

        TouchZone.RIGHT_EDGE_STRIP ->
            if (settings.fontSizeDragEnabled) DragGesture.FONT_SIZE else DragGesture.NONE

        else -> DragGesture.NONE
    }

    /**
     * The directional lock (§2.3). Returns [DragAxis.UNDECIDED] until the touch has moved
     * further than [touchSlop] on some axis, then commits to whichever axis moved more.
     * The caller keeps the first non-UNDECIDED answer for the rest of the gesture — that
     * is what stops a vertical brightness drag from also turning a page.
     */
    fun decideAxis(dx: Float, dy: Float, touchSlop: Float): DragAxis {
        val absX = abs(dx)
        val absY = abs(dy)
        if (absX < touchSlop && absY < touchSlop) return DragAxis.UNDECIDED
        return if (absY > absX) DragAxis.VERTICAL else DragAxis.HORIZONTAL
    }

    /**
     * Brightness from vertical travel: up is brighter (§2.3), and the full height of the
     * screen covers the full range, so the control feels continuous rather than steppy.
     */
    fun brightnessFor(startBrightness: Float, dy: Float, height: Float): Float {
        if (height <= 0f) return startBrightness
        return (startBrightness - dy / height).coerceIn(0f, 1f)
    }

    /**
     * Font size steps once per [FONT_SIZE_STEP_DP] of travel, upward = larger. Returns the
     * total number of steps for the drag so far; the caller compares it against the steps
     * already applied, which keeps the gesture idempotent if events are coalesced.
     */
    fun fontSizeStepsFor(dy: Float, density: Float): Int {
        if (density <= 0f) return 0
        val stepPx = FONT_SIZE_STEP_DP * density
        return (-dy / stepPx).toInt()
    }
}
