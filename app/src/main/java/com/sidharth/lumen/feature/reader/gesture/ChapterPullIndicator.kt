package com.sidharth.lumen.feature.reader.gesture

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

const val ChapterPullIndicatorTag = "chapter_pull_indicator"

/**
 * The filling ring that says "keep going and the chapter will turn".
 *
 * It sits at the edge being pushed against — bottom for the next chapter, top for the
 * previous — because that is where the finger already is and where the page has stopped
 * moving. The ring *is* the explanation: a reader who pulls at the end of a chapter sees
 * something begin to happen and learns the gesture without being told.
 */
@Composable
fun ChapterPullIndicator(
    progress: Float,
    forward: Boolean,
    modifier: Modifier = Modifier,
) {
    val shown = progress > 0.01f
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        label = "chapter-pull-alpha",
    )
    if (alpha <= 0.01f) return
    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        contentAlignment = if (forward) Alignment.BottomCenter else Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .alpha(alpha)
                .size(48.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                .testTag(ChapterPullIndicatorTag),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(40.dp),
                strokeWidth = 3.dp,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Icon(
                imageVector = if (forward) {
                    Icons.Outlined.ArrowDownward
                } else {
                    Icons.Outlined.ArrowUpward
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
