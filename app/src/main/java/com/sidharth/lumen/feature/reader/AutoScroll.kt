package com.sidharth.lumen.feature.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sidharth.lumen.R
import com.sidharth.lumen.core.settings.ReadingSettings
import kotlinx.coroutines.delay
import org.readium.r2.navigator.epub.EpubNavigatorFragment

const val AutoScrollBarTag = "reader_auto_scroll_bar"

/** Runtime state of auto-scroll; never persisted except the speed, which is a setting. */
data class AutoScrollState(
    val active: Boolean = false,
    val paused: Boolean = false,
)

/**
 * Teleprompter-style scrolling for scroll mode (M10).
 *
 * Readium keeps the whole resource in one WebView when `scroll = true`, and exposes a
 * JavaScript bridge, so the page is nudged with `scrollBy` on a steady tick. When the
 * bottom of the resource is reached the navigator is asked for the next one, and the
 * loop carries on from its top — a chapter boundary is not a stop.
 *
 * Speed 1.0 ≈ 40px/s, about a slow read-aloud pace on a phone. The tick is coarse
 * (every 50ms) because each step is a JS round-trip; the browser's own smooth-scroll
 * hides the steps.
 */
@Composable
fun AutoScrollDriver(
    navigator: EpubNavigatorFragment?,
    state: AutoScrollState,
    speed: Float,
) {
    LaunchedEffect(navigator, state, speed) {
        val current = navigator ?: return@LaunchedEffect
        if (!state.active || state.paused) return@LaunchedEffect
        val pixelsPerTick = (BASE_PIXELS_PER_SECOND * speed * TICK_MILLIS / 1000f).coerceAtLeast(1f)
        while (true) {
            // One round-trip does both: scroll, then report whether the bottom is reached.
            val atEnd = current.evaluateJavascript(
                """
                (function() {
                  var el = document.scrollingElement || document.documentElement;
                  el.scrollBy({ top: $pixelsPerTick, left: 0, behavior: 'auto' });
                  return (el.scrollTop + window.innerHeight) >= (el.scrollHeight - 2);
                })();
                """.trimIndent(),
            )
            if (atEnd == "true") {
                // Let the reader see the last lines before the jump.
                delay(END_OF_RESOURCE_PAUSE_MILLIS)
                if (!current.goForward(animated = false)) break // last resource: stop
                delay(TICK_MILLIS * 4)
            }
            delay(TICK_MILLIS)
        }
    }
}

/**
 * Moves one screenful in scroll mode, the way a page turn should behave there.
 *
 * `goForward()` in scroll mode advances to the next *resource* — a whole chapter — so a
 * tap meant to move down the page was jumping the book. When the end of the resource is
 * already in view this does fall through to the navigator, so reading still carries on
 * into the next chapter rather than stopping dead.
 */
suspend fun EpubNavigatorFragment.scrollByScreen(forward: Boolean) {
    // Whether the edge is already reached is decided *before* scrolling, not by comparing
    // positions afterwards: a smooth scroll has not moved yet when the next line runs, so
    // an after-the-fact check reports "stuck" every time — and would jump a chapter on
    // every tap, which is the bug this is here to fix.
    val direction = if (forward) "1" else "-1"
    val atEdge = evaluateJavascript(
        """
        (function() {
          var el = document.scrollingElement || document.documentElement;
          var dir = $direction;
          var atEnd = (el.scrollTop + window.innerHeight) >= (el.scrollHeight - 2);
          var atStart = el.scrollTop <= 2;
          if ((dir > 0 && atEnd) || (dir < 0 && atStart)) return true;
          el.scrollBy({ top: dir * window.innerHeight * 0.9, left: 0, behavior: 'smooth' });
          return false;
        })();
        """.trimIndent(),
    )
    // Only when there is genuinely nothing left to scroll does the chapter change.
    if (atEdge == "true") {
        if (forward) goForward(animated = false) else goBackward(animated = false)
    }
}

private const val BASE_PIXELS_PER_SECOND = 40f
private const val TICK_MILLIS = 50L
private const val END_OF_RESOURCE_PAUSE_MILLIS = 1200L

/** The floating bar shown while auto-scroll is on: pause/play, a speed slider, stop. */
@Composable
fun AutoScrollBar(
    state: AutoScrollState,
    speed: Float,
    onTogglePause: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.active) return
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(AutoScrollBarTag),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = onTogglePause) {
                Icon(
                    imageVector = if (state.paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                    contentDescription = stringResource(
                        if (state.paused) R.string.auto_scroll_resume else R.string.auto_scroll_pause,
                    ),
                )
            }
            Text(
                text = stringResource(R.string.auto_scroll_speed_label, speed),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.width(52.dp),
            )
            Slider(
                value = speed,
                onValueChange = onSpeedChange,
                valueRange = ReadingSettings.MIN_AUTO_SCROLL_SPEED..ReadingSettings.MAX_AUTO_SCROLL_SPEED,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onStop) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.auto_scroll_stop),
                )
            }
        }
    }
}
