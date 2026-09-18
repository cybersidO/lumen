package com.sidharth.lumen.feature.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sidharth.lumen.R
import com.sidharth.lumen.core.settings.ReaderFont
import com.sidharth.lumen.core.settings.ReaderTextAlign
import com.sidharth.lumen.core.settings.ReaderTypography
import com.sidharth.lumen.core.settings.HighlightStyle
import com.sidharth.lumen.core.settings.PageFlow
import com.sidharth.lumen.core.settings.ReadingSettings
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlin.math.roundToInt

const val TypographyFontSizeSliderTag = "typography_font_size"
const val TypographyPageFlowTag = "typography_page_flow"
const val TypographyHighlightStyleTag = "typography_highlight_style"
const val TypographyWarmTintTag = "typography_warm_tint"

/**
 * §2.2's typography controls. Every change writes through to DataStore immediately and is
 * submitted to the live navigator, so the effect is visible behind the sheet as it is
 * dragged — there is no "apply" button and nothing to lose by dismissing.
 */
@Composable
fun TypographySheet(
    typography: ReaderTypography,
    reading: ReadingSettings,
    onChange: (ReaderTypography) -> Unit,
    onReadingChange: (ReadingSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            // The extra bottom room is deliberate: with M10's controls the sheet fills the
            // screen, and a slider that lands in the gesture-navigation strip cannot be
            // dragged — the system reads the drag as a swipe. This keeps the last control
            // scrollable well clear of it.
            .padding(start = 24.dp, end = 24.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // The most-reached-for switch goes first (M10): it changes how the book moves.
        Label(stringResource(R.string.reader_page_flow))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.testTag(TypographyPageFlowTag),
        ) {
            PageFlow.entries.forEach { flow ->
                FilterChip(
                    selected = reading.pageFlow == flow,
                    onClick = { onReadingChange(reading.copy(pageFlow = flow)) },
                    label = { Text(flow.displayName) },
                )
            }
        }

        Label(stringResource(R.string.reader_font))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReaderFont.entries.forEach { font ->
                FilterChip(
                    selected = typography.font == font,
                    onClick = { onChange(typography.copy(font = font)) },
                    label = { Text(font.displayName) },
                )
            }
        }

        StepperRow(
            label = stringResource(R.string.reader_font_size),
            valueLabel = "${typography.fontSizePercent}%",
        )
        Slider(
            value = typography.fontSizePercent.toFloat(),
            onValueChange = { onChange(typography.withFontSizeClamped(it.roundToInt())) },
            valueRange = ReaderTypography.MIN_FONT_SIZE_PERCENT.toFloat()..
                ReaderTypography.MAX_FONT_SIZE_PERCENT.toFloat(),
            modifier = Modifier.testTag(TypographyFontSizeSliderTag),
        )

        StepperRow(
            label = stringResource(R.string.reader_line_height),
            valueLabel = stringResource(R.string.reader_line_height_value, typography.lineHeight),
        )
        Slider(
            value = typography.lineHeight,
            onValueChange = { onChange(typography.copy(lineHeight = it)) },
            valueRange = ReaderTypography.MIN_LINE_HEIGHT..ReaderTypography.MAX_LINE_HEIGHT,
        )

        StepperRow(
            label = stringResource(R.string.reader_margins),
            valueLabel = "${typography.pageMarginsPercent}%",
        )
        Slider(
            value = typography.pageMarginsPercent.toFloat(),
            onValueChange = { onChange(typography.copy(pageMarginsPercent = it.roundToInt())) },
            valueRange = ReaderTypography.MIN_MARGINS_PERCENT.toFloat()..
                ReaderTypography.MAX_MARGINS_PERCENT.toFloat(),
        )

        Label(stringResource(R.string.reader_alignment))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReaderTextAlign.entries.forEach { align ->
                FilterChip(
                    selected = typography.textAlign == align,
                    onClick = { onChange(typography.copy(textAlign = align)) },
                    label = { Text(align.displayName) },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.reader_hyphenation),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Switch(
                checked = typography.hyphens,
                onCheckedChange = { onChange(typography.copy(hyphens = it)) },
            )
        }

        Label(stringResource(R.string.reader_highlight_style))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.testTag(TypographyHighlightStyleTag),
        ) {
            HighlightStyle.entries.forEach { style ->
                FilterChip(
                    selected = reading.highlightStyle == style,
                    onClick = { onReadingChange(reading.copy(highlightStyle = style)) },
                    label = { Text(style.displayName) },
                )
            }
        }

        StepperRow(
            label = stringResource(R.string.reader_warm_tint),
            valueLabel = if (reading.warmTint <= 0f) {
                stringResource(R.string.reader_warm_tint_off)
            } else {
                "${(reading.warmTint * 100).roundToInt()}%"
            },
        )
        Slider(
            value = reading.warmTint,
            onValueChange = { onReadingChange(reading.copy(warmTint = it)) },
            valueRange = 0f..1f,
            modifier = Modifier.testTag(TypographyWarmTintTag),
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun StepperRow(label: String, valueLabel: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = valueLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
