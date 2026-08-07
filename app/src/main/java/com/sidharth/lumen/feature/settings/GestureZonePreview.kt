package com.sidharth.lumen.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sidharth.lumen.R
import com.sidharth.lumen.core.settings.GestureSettings
import com.sidharth.lumen.feature.reader.gesture.TouchZone

const val ZonePreviewTag = "gesture_zone_preview"

/**
 * §2.3's live preview: a phone-shaped diagram of the gesture zones that re-lays-out as
 * the sliders move. Interactive in the way that matters for understanding — tapping a
 * zone names it and explains what it does, in place of a legend the eye has to match up.
 */
@Composable
fun GestureZonePreview(
    settings: GestureSettings,
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableStateOf<TouchZone?>(null) }

    val edge = settings.edgeStripFraction
    val tap = (settings.tapZoneFraction - edge).coerceAtLeast(0f)
    val centre = (1f - 2f * (edge + tap)).coerceAtLeast(0f)

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 14f)
                .clip(RoundedCornerShape(18.dp))
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(18.dp),
                )
                .testTag(ZonePreviewTag),
        ) {
            ZoneCell(
                weight = edge,
                zone = TouchZone.LEFT_EDGE_STRIP,
                label = "☀",
                selected = selected,
                onSelect = { selected = it },
            )
            ZoneCell(
                weight = tap,
                zone = TouchZone.LEFT_TAP,
                label = "‹",
                selected = selected,
                onSelect = { selected = it },
            )
            ZoneCell(
                weight = centre,
                zone = TouchZone.CENTRE_TAP,
                label = "☰",
                selected = selected,
                onSelect = { selected = it },
            )
            ZoneCell(
                weight = tap,
                zone = TouchZone.RIGHT_TAP,
                label = "›",
                selected = selected,
                onSelect = { selected = it },
            )
            ZoneCell(
                weight = edge,
                zone = TouchZone.RIGHT_EDGE_STRIP,
                label = "Aa",
                selected = selected,
                onSelect = { selected = it },
            )
        }

        Text(
            text = stringResource(
                when (selected) {
                    TouchZone.LEFT_EDGE_STRIP -> R.string.zone_desc_left_edge
                    TouchZone.RIGHT_EDGE_STRIP -> R.string.zone_desc_right_edge
                    TouchZone.LEFT_TAP -> R.string.zone_desc_left_tap
                    TouchZone.RIGHT_TAP -> R.string.zone_desc_right_tap
                    TouchZone.CENTRE_TAP -> R.string.zone_desc_centre
                    null -> R.string.zone_desc_hint
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ZoneCell(
    weight: Float,
    zone: TouchZone,
    label: String,
    selected: TouchZone?,
    onSelect: (TouchZone) -> Unit,
) {
    if (weight <= 0f) return

    val isSelected = selected == zone
    val fill = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        zone == TouchZone.LEFT_EDGE_STRIP || zone == TouchZone.RIGHT_EDGE_STRIP ->
            MaterialTheme.colorScheme.surfaceVariant

        zone == TouchZone.CENTRE_TAP -> MaterialTheme.colorScheme.surface

        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }

    Box(
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
            .padding(1.dp)
            .background(fill)
            .clickable { onSelect(zone) }
            .testTag("zone_cell_${zone.name.lowercase()}"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = if (isSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
