package com.sidharth.lumen.feature.reader.gesture

import android.annotation.SuppressLint
import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import com.sidharth.lumen.core.settings.GestureSettings
import kotlin.math.abs

/**
 * The gesture layer for the reader.
 *
 * It has to be a ViewGroup wrapping Readium's WebView, not a Compose overlay. M2 proved
 * why: anything layered above the navigator in Compose swallows every touch and silently
 * stops pages turning. A ViewGroup can *watch* the whole stream through
 * `onInterceptTouchEvent` and only take ownership at the moment a gesture turns out to be
 * one of ours — everything else reaches the WebView untouched, so text selection,
 * long-press and swipe-to-turn keep working.
 *
 * The one rule that drives the design: **an edge strip drag must never turn a page**, so
 * interception happens as soon as the directional lock commits to VERTICAL inside a strip,
 * before the pager has seen enough movement to start scrolling.
 */
class ReaderGestureLayout(context: Context) : FrameLayout(context) {

    var settings: GestureSettings = GestureSettings.Defaults
    var isRtl: Boolean = false

    /** Supplied by the host; returns the brightness the drag should start from. */
    var currentBrightness: () -> Float = { 0.5f }

    var onBrightnessChange: (Float) -> Unit = {}
    var onBrightnessDragEnd: () -> Unit = {}
    var onFontSizeStep: (steps: Int) -> Unit = {}
    var onFontSizeDragEnd: () -> Unit = {}
    var onTwoFingerTap: () -> Unit = {}

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
    private val density = context.resources.displayMetrics.density

    private var startX = 0f
    private var startY = 0f
    private var downTime = 0L
    private var axis = DragAxis.UNDECIDED
    private var gesture = DragGesture.NONE
    private var zone = TouchZone.CENTRE_TAP

    private var brightnessAtStart = 0.5f
    private var appliedFontSteps = 0

    /** Set when a second finger lands, so the gesture is not also treated as a tap. */
    private var sawMultiTouch = false

    init {
        // The layout must not draw or focus; it exists purely to observe touches.
        setWillNotDraw(true)
        isClickable = false
        isFocusable = false
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> beginGesture(event)

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) sawMultiTouch = true
            }

            MotionEvent.ACTION_MOVE -> {
                if (gesture == DragGesture.NONE || sawMultiTouch) return false
                if (axis == DragAxis.UNDECIDED) {
                    axis = GestureGeometry.decideAxis(
                        dx = event.x - startX,
                        dy = event.y - startY,
                        touchSlop = touchSlop,
                    )
                }
                // Take the gesture only once it is certainly vertical and inside a strip.
                // Committing earlier would steal horizontal swipes; later would let the
                // pager start moving first.
                if (axis == DragAxis.VERTICAL) {
                    brightnessAtStart = currentBrightness()
                    appliedFontSteps = 0
                    // Re-anchor so the drag measures from the commit point — otherwise
                    // the first MOVE would jump by the slop distance already travelled.
                    startY = event.y
                    return true
                }
            }
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            // Reaches here only when no child consumed the DOWN, e.g. a tap on blank
            // margin. The gesture state still has to be set up so the UP resolves.
            MotionEvent.ACTION_DOWN -> {
                beginGesture(event)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                when (gesture) {
                    DragGesture.BRIGHTNESS -> {
                        onBrightnessChange(
                            GestureGeometry.brightnessFor(
                                startBrightness = brightnessAtStart,
                                dy = event.y - startY,
                                height = height.toFloat(),
                            ),
                        )
                        return true
                    }

                    DragGesture.FONT_SIZE -> {
                        val steps = GestureGeometry.fontSizeStepsFor(event.y - startY, density)
                        if (steps != appliedFontSteps) {
                            // Haptic per step boundary, so the control feels notched
                            // even though the finger is moving smoothly (§2.3).
                            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            onFontSizeStep(steps - appliedFontSteps)
                            appliedFontSteps = steps
                        }
                        return true
                    }

                    DragGesture.NONE -> return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                endDrag()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * Watches the full stream (regardless of who consumes it) for the two-finger tap.
     * Single-finger taps are deliberately NOT resolved here: Readium's own `TapEvent`
     * already filters out link clicks and selection taps at the JS level, so the zone
     * lookup for those happens in the reader's InputListener with the tap's point.
     */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) sawMultiTouch = true
            }

            MotionEvent.ACTION_UP -> {
                if (sawMultiTouch && axis != DragAxis.VERTICAL) {
                    val movedFar = abs(event.x - startX) > touchSlop ||
                        abs(event.y - startY) > touchSlop
                    val quick = event.eventTime - downTime < longPressTimeout
                    if (!movedFar && quick && settings.twoFingerTapThemeEnabled) {
                        onTwoFingerTap()
                    }
                    sawMultiTouch = false
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    private fun beginGesture(event: MotionEvent) {
        startX = event.x
        startY = event.y
        downTime = event.eventTime
        axis = DragAxis.UNDECIDED
        sawMultiTouch = event.pointerCount >= 2
        zone = GestureGeometry.zoneFor(event.x, width.toFloat(), settings)
        gesture = GestureGeometry.dragGestureFor(zone, settings)
        brightnessAtStart = currentBrightness()
        appliedFontSteps = 0
    }

    private fun endDrag() {
        when (gesture) {
            DragGesture.BRIGHTNESS -> onBrightnessDragEnd()
            DragGesture.FONT_SIZE -> onFontSizeDragEnd()
            DragGesture.NONE -> Unit
        }
        gesture = DragGesture.NONE
        axis = DragAxis.UNDECIDED
        appliedFontSteps = 0
    }
}
