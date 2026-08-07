package com.sidharth.lumen.feature.reader.gesture

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives real MotionEvent streams through [ReaderGestureLayout] with a child that records
 * what reaches it — a stand-in for Readium's WebView.
 *
 * The single most important assertion in this file is the M3 done-when: a vertical drag
 * that starts in the left edge strip is intercepted, so the child (the pager) never sees
 * the MOVE stream that would have turned a page.
 */
@RunWith(AndroidJUnit4::class)
class ReaderGestureLayoutTest {

    private lateinit var layout: ReaderGestureLayout
    private lateinit var child: RecordingChild

    private val width = 1000
    private val height = 2000

    private class RecordingChild(context: Context) : View(context) {
        val received = mutableListOf<Int>()
        override fun onTouchEvent(event: MotionEvent): Boolean {
            received += event.actionMasked
            return true // a WebView consumes everything it is given
        }
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        layout = ReaderGestureLayout(context)
        child = RecordingChild(context)
        layout.addView(
            child,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        layout.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        layout.layout(0, 0, width, height)
    }

    private fun event(action: Int, x: Float, y: Float, downTime: Long, eventTime: Long) =
        MotionEvent.obtain(downTime, eventTime, action, x, y, 0)

    private fun dispatchStream(points: List<Pair<Float, Float>>) {
        val downTime = SystemClock.uptimeMillis()
        var t = downTime
        val (x0, y0) = points.first()
        layout.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, x0, y0, downTime, t))
        points.drop(1).forEach { (x, y) ->
            t += 16
            layout.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, x, y, downTime, t))
        }
        val (xn, yn) = points.last()
        layout.dispatchTouchEvent(event(MotionEvent.ACTION_UP, xn, yn, downTime, t + 16))
    }

    // --- the M3 done-when ------------------------------------------------------------

    @Test
    fun verticalDragOnLeftEdgeIsInterceptedAndChangesBrightness() {
        val values = mutableListOf<Float>()
        var ended = false
        layout.currentBrightness = { 0.5f }
        layout.onBrightnessChange = { values += it }
        layout.onBrightnessDragEnd = { ended = true }

        // Straight vertical drag inside the 15% left strip (x=50 of 1000).
        dispatchStream(
            listOf(
                50f to 1500f,
                50f to 1400f,
                50f to 1200f,
                50f to 1000f,
                50f to 800f,
            ),
        )

        assertTrue("brightness callbacks fired", values.isNotEmpty())
        assertTrue("drag up brightened", values.last() > 0.5f)
        assertTrue(ended)

        // The child saw DOWN (dispatch order) but the MOVE stream was stolen at
        // interception — the pager can never have started a page turn from it.
        val movesReachingChild = child.received.count { it == MotionEvent.ACTION_MOVE }
        assertEquals("child must not receive the vertical drag", 0, movesReachingChild)
    }

    @Test
    fun verticalDragOnRightEdgeStepsFontSize() {
        var steps = 0
        layout.onFontSizeStep = { steps += it }

        // x=950 of 1000 is inside the right strip. The directional lock re-anchors the
        // drag at the commit point, which eats the first movement — so travel far enough
        // (4 steps, in small increments so the anchor lands early) that at least two
        // steps must land afterwards.
        val density = layout.resources.displayMetrics.density
        val travel = 4f * GestureGeometry.FONT_SIZE_STEP_DP * density
        val moves = 10

        dispatchStream(
            listOf(950f to 1500f) +
                (1..moves).map { i -> 950f to 1500f - travel * i / moves },
        )

        assertTrue("at least two steps applied, got $steps", steps >= 2)
    }

    @Test
    fun horizontalSwipeIsLeftForThePager() {
        var brightnessTouched = false
        layout.onBrightnessChange = { brightnessTouched = true }

        // A horizontal swipe that *starts* in the left strip: the directional lock must
        // hand it to the child (page turn), not to brightness.
        dispatchStream(
            listOf(
                50f to 1200f,
                150f to 1205f,
                400f to 1210f,
                700f to 1215f,
            ),
        )

        assertFalse("horizontal swipe must not change brightness", brightnessTouched)
        assertTrue(
            "child must receive the horizontal stream",
            child.received.count { it == MotionEvent.ACTION_MOVE } > 0,
        )
    }

    @Test
    fun centreDragsAreNeverIntercepted() {
        var brightnessTouched = false
        var steps = 0
        layout.onBrightnessChange = { brightnessTouched = true }
        layout.onFontSizeStep = { steps += it }

        // Vertical drag in the centre — scrolling territory, none of ours.
        dispatchStream(
            listOf(
                500f to 1500f,
                500f to 1200f,
                500f to 900f,
            ),
        )

        assertFalse(brightnessTouched)
        assertEquals(0, steps)
        assertTrue(child.received.count { it == MotionEvent.ACTION_MOVE } > 0)
    }

    @Test
    fun disabledBrightnessDragPassesThrough() {
        layout.settings = layout.settings.copy(brightnessDragEnabled = false)
        var brightnessTouched = false
        layout.onBrightnessChange = { brightnessTouched = true }

        dispatchStream(
            listOf(
                50f to 1500f,
                50f to 1200f,
                50f to 900f,
            ),
        )

        assertFalse(brightnessTouched)
        assertTrue(child.received.count { it == MotionEvent.ACTION_MOVE } > 0)
    }

    @Test
    fun twoFingerTapFiresOnlyWhenEnabled() {
        var toggles = 0
        layout.onTwoFingerTap = { toggles++ }

        fun twoFingerTap() {
            val downTime = SystemClock.uptimeMillis()
            layout.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 400f, 1000f, downTime, downTime))
            val props = arrayOf(
                MotionEvent.PointerProperties().apply { id = 0 },
                MotionEvent.PointerProperties().apply { id = 1 },
            )
            val coords = arrayOf(
                MotionEvent.PointerCoords().apply { x = 400f; y = 1000f },
                MotionEvent.PointerCoords().apply { x = 600f; y = 1000f },
            )
            layout.dispatchTouchEvent(
                MotionEvent.obtain(
                    downTime, downTime + 20,
                    MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                    2, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0,
                ),
            )
            layout.dispatchTouchEvent(event(MotionEvent.ACTION_UP, 400f, 1000f, downTime, downTime + 60))
        }

        // Disabled by default (§10 concern #2): nothing fires.
        twoFingerTap()
        assertEquals(0, toggles)

        layout.settings = layout.settings.copy(twoFingerTapThemeEnabled = true)
        twoFingerTap()
        assertEquals(1, toggles)
    }
}
