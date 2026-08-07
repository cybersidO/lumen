package com.sidharth.lumen.feature.reader.gesture

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

const val GestureIndicatorTag = "gesture_indicator"

/** Which side the indicator sits on — the same side as the finger doing the dragging. */
enum class IndicatorSide { LEFT, RIGHT }

/**
 * The transient readout §2.3 asks for: a slim bar plus a numeric percentage, fading out
 * ~600ms after the finger lifts. Rendered in Compose *above* the reader, which is safe
 * because it is only present during a drag the gesture layer has already taken ownership
 * of — it can no longer steal anything from the WebView.
 */
@Composable
fun GestureIndicator(
    visible: Boolean,
    side: IndicatorSide,
    label: String,
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(FADE_OUT_MILLIS)),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = when (side) {
                IndicatorSide.LEFT -> Alignment.CenterStart
                IndicatorSide.RIGHT -> Alignment.CenterEnd
            },
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f))
                    .padding(horizontal = 12.dp, vertical = 16.dp)
                    .testTag(GestureIndicatorTag)
                    .semantics { contentDescription = label },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .height(160.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.outlineVariant),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(fraction.coerceIn(0f, 1f))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/**
 * Dimming below the OS floor (§2.3). A plain black scrim over everything, including the
 * book — the only way to go darker once `screenBrightness` is already at 0.
 */
@Composable
fun DimOverlay(alpha: Float, modifier: Modifier = Modifier) {
    if (alpha <= 0f) return
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = alpha.coerceIn(0f, 1f))),
    )
}

const val FADE_OUT_MILLIS = 600
