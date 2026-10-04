package com.sidharth.lumen.feature.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/*
 * Chart primitives for the Home tab. The rules they follow, so they read as one system:
 *   - marks are thin (≤ 24dp), with a 4dp rounded data-end and a square baseline;
 *   - touching marks are separated by a 2dp gap in the surface colour, never a border;
 *   - magnitude is one hue, light → dark (sequential); "the one that matters" is the
 *     accent with the rest in a de-emphasis tone (emphasis);
 *   - text is always in text colours, never the series colour;
 *   - a single series has no legend — the title names it.
 * Everything is Canvas: no chart library, nothing new to depend on.
 */

private val DataEnd = 4.dp
private val Gap = 2.dp

/** A bar with a rounded data-end and a square baseline. */
private fun DrawScope.dataBar(x: Float, top: Float, width: Float, bottom: Float, color: Color) {
    val h = bottom - top
    if (h <= 0f) return
    val r = minOf(DataEnd.toPx(), width / 2f, h)
    val path = Path().apply {
        moveTo(x, bottom)
        lineTo(x, top + r)
        quadraticTo(x, top, x + r, top)
        lineTo(x + width - r, top)
        quadraticTo(x + width, top, x + width, top + r)
        lineTo(x + width, bottom)
        close()
    }
    drawPath(path, color)
}

/** Vertical columns with an optional emphasised index; labels below in text tokens. */
@Composable
fun ColumnChart(
    values: List<Long>,
    labels: List<String>,
    emphasisIndex: Int?,
    modifier: Modifier = Modifier,
    barHeight: androidx.compose.ui.unit.Dp = 88.dp,
) {
    val accent = MaterialTheme.colorScheme.primary
    val quiet = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(barHeight)) {
            if (values.isEmpty()) return@Canvas
            val gap = Gap.toPx()
            val slot = (size.width - gap * (values.size - 1)) / values.size
            val barW = minOf(slot, 24.dp.toPx())
            val inset = (slot - barW) / 2f
            values.forEachIndexed { i, v ->
                val x = i * (slot + gap) + inset
                drawRoundRect(track, Offset(x, 0f), Size(barW, size.height), CornerRadius(DataEnd.toPx()))
                val h = v.toFloat() / max * size.height
                dataBar(x, size.height - h, barW, size.height, if (i == emphasisIndex) accent else quiet)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            labels.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Contribution-style calendar: [weeks] columns × 7 rows, one hue in five steps. */
@Composable
fun Heatmap(cells: List<HeatCell>, weeks: Int, modifier: Modifier = Modifier) {
    val hue = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    val ring = MaterialTheme.colorScheme.onSurface
    val max = (cells.maxOfOrNull { it.minutes } ?: 0L).coerceAtLeast(1L)
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(112.dp)) {
            if (cells.isEmpty()) return@Canvas
            val gap = 3.dp.toPx()
            val cell = minOf((size.width - gap * (weeks - 1)) / weeks, (size.height - gap * 6) / 7)
            val originX = (size.width - (cell * weeks + gap * (weeks - 1))) / 2f
            cells.forEachIndexed { i, c ->
                val col = i / 7
                val row = i % 7
                val x = originX + col * (cell + gap)
                val y = row * (cell + gap)
                val step = when {
                    c.minutes <= 0 -> 0
                    else -> (1 + (c.minutes * 4 / max).toInt()).coerceIn(1, 4)
                }
                // Sequential: one hue, four steps of 0.25 → 1.0 alpha over the surface.
                val color = if (step == 0) empty else hue.copy(alpha = 0.25f * step)
                drawRoundRect(color, Offset(x, y), Size(cell, cell), CornerRadius(2.dp.toPx()))
                if (c.isToday) {
                    drawRoundRect(ring, Offset(x, y), Size(cell, cell), CornerRadius(2.dp.toPx()), style = Stroke(1.5.dp.toPx()))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text(
                text = "${weeks} weeks ago",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "today",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** A single-series line with a dashed mean and a ≥8dp marker on the latest point. */
@Composable
fun TrendLine(points: List<Float>, mean: Float, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val meanColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    Canvas(modifier.fillMaxWidth().height(96.dp)) {
        if (points.size < 2) return@Canvas
        val max = (points.maxOrNull() ?: 1f).coerceAtLeast(1f) * 1.15f
        val padX = 8.dp.toPx()
        val w = size.width - padX * 2
        val stepX = w / (points.size - 1)
        fun yFor(v: Float) = size.height - (v / max) * size.height
        // mean, dashed
        val my = yFor(mean)
        drawLine(
            meanColor, Offset(padX, my), Offset(size.width - padX, my),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx())),
        )
        val path = Path()
        points.forEachIndexed { i, v ->
            val x = padX + i * stepX
            if (i == 0) path.moveTo(x, yFor(v)) else path.lineTo(x, yFor(v))
        }
        drawPath(path, accent, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        // latest point: 8dp marker with a 2dp surface ring
        val lx = padX + (points.size - 1) * stepX
        val ly = yFor(points.last())
        drawCircle(surface, radius = 6.dp.toPx(), center = Offset(lx, ly))
        drawCircle(accent, radius = 4.dp.toPx(), center = Offset(lx, ly))
    }
}

/** One row of [HorizontalBars]: the label is resolved by the caller, in composable scope. */
data class BarRow(val name: String, val value: Long, val valueLabel: String)

/** Horizontal bars for long-named categories; the value is text, not a colour. */
@Composable
fun HorizontalBars(
    rows: List<BarRow>,
    modifier: Modifier = Modifier,
) {
    val hue = MaterialTheme.colorScheme.primary
    val quiet = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val max = (rows.maxOfOrNull { it.value } ?: 0L).coerceAtLeast(1L)
    Column(modifier) {
        rows.forEachIndexed { index, (name, value, valueLabel) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.weight(0.42f),
                )
                Canvas(Modifier.weight(0.40f).height(14.dp).padding(horizontal = 6.dp)) {
                    drawRoundRect(track, Offset.Zero, Size(size.width, size.height), CornerRadius(DataEnd.toPx()))
                    val w = value.toFloat() / max * size.width
                    if (w > 0f) {
                        val r = minOf(DataEnd.toPx(), w / 2f)
                        val p = Path().apply {
                            moveTo(0f, 0f); lineTo(w - r, 0f)
                            quadraticTo(w, 0f, w, r); lineTo(w, size.height - r)
                            quadraticTo(w, size.height, w - r, size.height); lineTo(0f, size.height); close()
                        }
                        drawPath(p, if (index == 0) hue else quiet)
                    }
                }
                Text(
                    text = valueLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(0.18f),
                )
            }
        }
    }
}

/**
 * The daily-goal ring (M13): a track plus the fraction of today's goal met, drawn from
 * twelve o'clock clockwise. Over-achievement is shown as a complete ring rather than a
 * second lap — the point is "done", not a score.
 */
@Composable
fun GoalRing(
    progress: Float,
    modifier: Modifier = Modifier,
    strokeWidth: androidx.compose.ui.unit.Dp = 8.dp,
    content: @Composable () -> Unit = {},
) {
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val clamped = progress.coerceIn(0f, 1f)
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            val inset = strokeWidth.toPx() / 2f
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = stroke,
            )
            if (clamped > 0f) {
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = 360f * clamped,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = stroke,
                )
            }
        }
        content()
    }
}

/** A 12dp-tall sparkline: de-emphasis hue, with the current period in the accent. */
@Composable
fun Sparkline(values: List<Long>, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val quiet = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Canvas(modifier.height(28.dp)) {
        if (values.isEmpty()) return@Canvas
        val gap = Gap.toPx()
        val w = (size.width - gap * (values.size - 1)) / values.size
        values.forEachIndexed { i, v ->
            val h = (v.toFloat() / max * size.height).coerceAtLeast(if (v > 0) 2.dp.toPx() else 0f)
            dataBar(i * (w + gap), size.height - h, w, size.height, if (i == values.lastIndex) accent else quiet)
        }
    }
}
