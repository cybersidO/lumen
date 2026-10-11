package com.sidharth.lumen.feature.reader.gesture

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.webkit.WebView
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

    /**
     * Swallow horizontal drags instead of letting them reach Readium's pager.
     *
     * Set in scroll mode, where the pager moves a whole *chapter* at a time: a thumb
     * scrolling down a page drifts sideways constantly, and each drift was jumping the
     * reader out of the chapter they were reading. In paged mode this stays false,
     * because there a horizontal swipe turning a page is exactly what it should do.
     *
     * Nothing is lost — the tap zones still call `goForward`/`goBackward`, auto-scroll
     * still crosses chapters on its own, and the table of contents is unaffected.
     */
    var blockHorizontalSwipes: Boolean = false

    /**
     * Whether a pull past the end of a chapter may cross into the next one.
     *
     * Scroll mode only: there a chapter is a dead end, because Readium lays each resource
     * out separately and scrolling simply stops at the bottom. In paged mode the pager
     * already handles it.
     */
    var chapterPullEnabled: Boolean = false

    /**
     * Lets a test stand in for the page's own scroll state.
     *
     * Production leaves this null and the layout finds the WebView itself; a test has no
     * WebView to find, and the pull logic is worth covering without one.
     */
    var pageScrollProbe: ((direction: Int) -> Boolean)? = null

    /** Supplied by the host; returns the brightness the drag should start from. */
    var currentBrightness: () -> Float = { 0.5f }

    var onBrightnessChange: (Float) -> Unit = {}
    var onBrightnessDragEnd: () -> Unit = {}
    var onFontSizeStep: (steps: Int) -> Unit = {}
    var onFontSizeDragEnd: () -> Unit = {}
    var onTwoFingerTap: () -> Unit = {}

    /** Ring progress, 0f..1f, and which way the pull is heading. */
    var onChapterPull: (progress: Float, forward: Boolean) -> Unit = { _, _ -> }

    /** The ring filled: change chapter. */
    var onChapterCross: (forward: Boolean) -> Unit = {}

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

    private val chapterPull = ChapterPullTracker(thresholdPx = CHAPTER_PULL_DP * density)
    private var lastY = 0f
    private var pullForward = true

    /**
     * One crossing per finger, however far the drag goes.
     *
     * Resetting the tracker after a crossing re-arms it instantly, and a single long drag
     * then walks through four chapters — which is what the test caught. Lifting the
     * finger is the only thing that clears this.
     */
    private var crossedInThisGesture = false

    /** Clears the ring once the finger has been off for the tracker's forget window. */
    private val forgetPull = Runnable {
        chapterPull.reset()
        onChapterPull(0f, pullForward)
    }

    /** Set when a second finger lands, so the gesture is not also treated as a tap. */
    private var sawMultiTouch = false

    private var movedBeyondSlop = false

    /**
     * True when the gesture that just finished actually moved.
     *
     * Readium decides for itself what counts as a tap, and a short flick while scrolling
     * slips under its threshold — which turned an ordinary scroll into a page turn. This
     * layer sees the whole touch stream whether or not it intercepts, so it can answer
     * the only question that matters: did the finger travel? The tap handler checks this
     * before acting, so a drag is never also a tap.
     */
    val lastGestureWasDrag: Boolean get() = movedBeyondSlop

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
                if (!movedBeyondSlop &&
                    (abs(event.x - startX) > touchSlop || abs(event.y - startY) > touchSlop)
                ) {
                    movedBeyondSlop = true
                }
                if (sawMultiTouch) return false
                decideAxisIfNeeded(event)
                // Claim a horizontal drag anywhere on the page when the pager would
                // otherwise skip a chapter. Decided at the same moment as any other
                // axis lock, so the pager never sees enough movement to begin.
                if (blockHorizontalSwipes && axis == DragAxis.HORIZONTAL) return true
                if (gesture == DragGesture.NONE) {
                    // Not one of the edge-strip drags, so this is an ordinary scroll —
                    // the only kind that can reach the end of a chapter.
                    trackChapterPull(event)
                    return false
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

                    // Either a tap being tracked, or a horizontal drag we are swallowing
                    // so it cannot reach the pager.
                    DragGesture.NONE -> {
                        // Reached when no child consumed the DOWN — a drag that began on
                        // blank margin. The pull has to work there too, or the gesture
                        // silently depends on where the finger happened to land.
                        decideAxisIfNeeded(event)
                        trackChapterPull(event)
                        return true
                    }
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

    private fun decideAxisIfNeeded(event: MotionEvent) {
        if (axis != DragAxis.UNDECIDED) return
        axis = GestureGeometry.decideAxis(
            dx = event.x - startX,
            dy = event.y - startY,
            touchSlop = touchSlop,
        )
    }

    /**
     * Accumulates a pull once the page itself has nothing left to scroll.
     *
     * The travel measured is between consecutive MOVE events, not from the touch-down:
     * what matters is how far the finger has gone *since the edge was reached*, and the
     * distance before that belongs to the scroll.
     */
    private fun trackChapterPull(event: MotionEvent) {
        if (!chapterPullEnabled || axis != DragAxis.VERTICAL || crossedInThisGesture) return
        val dy = event.y - lastY
        lastY = event.y
        if (dy == 0f) return
        // Dragging the finger up pushes the page down, towards the next chapter.
        val forward = dy < 0f
        if (forward != pullForward) {
            chapterPull.reset()
            pullForward = forward
        }
        if (pageCanScroll(if (forward) 1 else -1)) {
            // Still room on the page; this is a scroll, not a pull.
            if (chapterPull.progress > 0f) {
                chapterPull.reset()
                onChapterPull(0f, forward)
            }
            return
        }
        removeCallbacks(forgetPull)
        val crossed = chapterPull.pull(abs(dy), event.eventTime)
        onChapterPull(chapterPull.progress, forward)
        if (crossed) {
            crossedInThisGesture = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onChapterCross(forward)
            chapterPull.reset()
            onChapterPull(0f, forward)
        }
    }

    /**
     * Whether the page being read can still scroll [direction] (1 down, -1 up).
     *
     * Readium keeps a WebView per resource inside a pager, so "the page being read" is
     * the one covering the middle of this layout — asking the wrong one would report the
     * wrong edge and cross a chapter early.
     */
    private fun pageCanScroll(direction: Int): Boolean {
        pageScrollProbe?.let { return it(direction) }
        val page = activePage() ?: return true
        return page.canScrollVertically(direction)
    }

    private fun activePage(): View? {
        val own = Rect()
        if (!getGlobalVisibleRect(own)) return null
        var showing: View? = null
        var covering: View? = null
        val rect = Rect()
        fun visit(view: View) {
            if (view is WebView) {
                if (view.isShown) {
                    if (showing == null) showing = view
                    if (view.getGlobalVisibleRect(rect) &&
                        rect.contains(own.centerX(), own.centerY())
                    ) {
                        covering = view
                    }
                }
                return
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) visit(view.getChildAt(i))
            }
        }
        visit(this)
        return covering ?: showing
    }

    private fun beginGesture(event: MotionEvent) {
        startX = event.x
        startY = event.y
        lastY = event.y
        downTime = event.eventTime
        axis = DragAxis.UNDECIDED
        movedBeyondSlop = false
        crossedInThisGesture = false
        sawMultiTouch = event.pointerCount >= 2
        zone = GestureGeometry.zoneFor(event.x, width.toFloat(), settings)
        gesture = GestureGeometry.dragGestureFor(zone, settings)
        brightnessAtStart = currentBrightness()
        appliedFontSteps = 0
    }

    private fun endDrag() {
        // The ring stays up between two flicks at a chapter edge, then gives up.
        if (chapterPull.progress > 0f) {
            removeCallbacks(forgetPull)
            postDelayed(forgetPull, ChapterPullTracker.DEFAULT_FORGET_MILLIS)
        }
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
