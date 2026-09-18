package com.sidharth.lumen.feature.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sidharth.lumen.R
import com.sidharth.lumen.core.database.BookReadingTotal

const val StatsStreakTag = "stats_streak"
const val StatsTodayTag = "stats_today"
const val StatsBarsTag = "stats_bars"

/**
 * M10's reading stats. Four numbers, two weeks of bars, and a per-book list — enough to
 * answer "am I actually reading?" without turning a quiet habit into a scoreboard.
 * The bars are a Canvas rather than a chart library: nothing new to depend on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, end = 20.dp, bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        value = pluralStringResource(R.plurals.stats_days, state.streakDays, state.streakDays),
                        label = stringResource(R.string.stats_streak),
                        modifier = Modifier.weight(1f).testTag(StatsStreakTag),
                    )
                    StatTile(
                        value = stringResource(R.string.stats_minutes_value, state.todayMinutes),
                        label = stringResource(R.string.stats_today),
                        modifier = Modifier.weight(1f).testTag(StatsTodayTag),
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        value = stringResource(R.string.stats_minutes_value, state.weekMinutes),
                        label = stringResource(R.string.stats_this_week),
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        value = if (state.pagesPerHour > 0) "${state.pagesPerHour}" else "—",
                        label = stringResource(R.string.stats_pages_per_hour),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.stats_last_two_weeks),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(12.dp))
                        DayBars(
                            bars = state.lastTwoWeeks,
                            modifier = Modifier.fillMaxWidth().height(96.dp).testTag(StatsBarsTag),
                        )
                    }
                }
            }
            if (state.loaded && state.perBook.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.stats_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    )
                }
            } else if (state.perBook.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.stats_by_book),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                items(state.perBook, key = { it.bookContentHash }) { BookRow(it) }
            }
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DayBars(bars: List<DayBar>, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val max = (bars.maxOfOrNull { it.minutes } ?: 0L).coerceAtLeast(1L)
    Canvas(modifier) {
        if (bars.isEmpty()) return@Canvas
        val gap = 6.dp.toPx()
        val barWidth = (size.width - gap * (bars.size - 1)) / bars.size
        bars.forEachIndexed { i, bar ->
            val x = i * (barWidth + gap)
            // Track first so a zero day still shows as a day.
            drawRoundRect(
                color = track,
                topLeft = Offset(x, 0f),
                size = Size(barWidth, size.height),
                cornerRadius = CornerRadius(4.dp.toPx()),
            )
            val h = (bar.minutes.toFloat() / max) * size.height
            if (h > 0f) {
                drawRoundRect(
                    color = if (bar.isToday) primary else primary.copy(alpha = 0.6f),
                    topLeft = Offset(x, size.height - h),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                )
            }
        }
    }
}

@Composable
private fun BookRow(total: BookReadingTotal) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = total.title ?: stringResource(R.string.stats_removed_book),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
            )
            Text(
                text = pluralStringResource(R.plurals.stats_pages_value, total.pagesTurned.toInt(), total.pagesTurned),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = formatMinutes(total.minutes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun formatMinutes(minutes: Long): String =
    if (minutes < 60) {
        stringResource(R.string.stats_minutes_value, minutes)
    } else {
        stringResource(R.string.stats_hours_value, minutes / 60, minutes % 60)
    }
