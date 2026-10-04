package com.sidharth.lumen.feature.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sidharth.lumen.R
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

const val HomeHeroTag = "home_hero"
const val HomeHeatmapTag = "home_heatmap"
const val HomeSettingsButtonTag = "home_settings_button"
const val HomeGoalRingTag = "home_goal_ring"

/**
 * The Home tab (M10.1): a dashboard of the reading habit. Leads with one hero figure —
 * today's minutes — then a KPI row, then the charts in order of how often they are
 * worth a glance: the calendar, the week shape, the hour shape, the pace, the books,
 * the records. Every chart is one series in one hue, so none needs a legend.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    val dayFormatter = remember(locale) { DateTimeFormatter.ofPattern("d MMM", locale) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
                actions = {
                    IconButton(onClick = onOpenSettings, modifier = Modifier.testTag(HomeSettingsButtonTag)) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings_open))
                    }
                },
            )
        },
    ) { padding ->
        val insights = state.insights
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (insights == null) return@LazyColumn

            // --- hero ------------------------------------------------------------------
            item {
                Column(Modifier.fillMaxWidth().testTag(HomeHeroTag)) {
                    Text(
                        text = stringResource(R.string.home_today_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "${insights.todayMinutes}",
                            style = MaterialTheme.typography.displayLarge.copy(fontSize = 64.sp, fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = stringResource(R.string.home_minutes_unit),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 6.dp, bottom = 12.dp),
                        )
                        Spacer(Modifier.weight(1f))
                        if (state.dailyGoalMinutes > 0) {
                            GoalRing(
                                progress = state.goalProgress,
                                modifier = Modifier.size(76.dp).padding(bottom = 8.dp).testTag(HomeGoalRingTag),
                            ) {
                                Text(
                                    text = if (state.goalMet) "✓"
                                    else "${(state.goalProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        } else {
                            Sparkline(
                                values = insights.sparkline,
                                modifier = Modifier.fillMaxWidth(0.42f).padding(bottom = 14.dp),
                            )
                        }
                    }
                    val delta = insights.todayMinutes - insights.yesterdayMinutes
                    if (state.dailyGoalMinutes > 0) {
                        Sparkline(
                            values = insights.sparkline,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp),
                        )
                    }
                    Text(
                        text = when {
                            state.goalMet -> stringResource(R.string.home_goal_met, state.dailyGoalMinutes)
                            state.dailyGoalMinutes > 0 -> stringResource(
                                R.string.home_goal_remaining,
                                (state.dailyGoalMinutes - insights.todayMinutes).coerceAtLeast(0),
                            )
                            insights.yesterdayMinutes == 0L && insights.todayMinutes == 0L -> stringResource(R.string.home_delta_none)
                            delta >= 0 -> stringResource(R.string.home_delta_up, delta)
                            else -> stringResource(R.string.home_delta_down, -delta)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // --- KPI row -----------------------------------------------------------------
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Kpi(
                        pluralStringResource(R.plurals.stats_days, insights.streakDays, insights.streakDays),
                        stringResource(R.string.stats_streak), Modifier.weight(1f),
                    )
                    Kpi(
                        stringResource(R.string.stats_minutes_value, insights.weekMinutes),
                        stringResource(R.string.stats_this_week), Modifier.weight(1f),
                    )
                    Kpi(
                        if (insights.pagesPerHour > 0) "${insights.pagesPerHour}" else "—",
                        stringResource(R.string.stats_pages_per_hour), Modifier.weight(1f),
                    )
                    Kpi(
                        formatHours(insights.totalMinutes),
                        stringResource(R.string.home_total), Modifier.weight(1f),
                    )
                }
            }

            if (state.isEmpty) {
                item {
                    Text(
                        text = stringResource(R.string.stats_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    )
                }
                return@LazyColumn
            }

            // --- calendar ----------------------------------------------------------------
            item {
                ChartCard(
                    title = stringResource(R.string.home_calendar_title),
                    subtitle = pluralStringResource(R.plurals.home_calendar_subtitle, insights.records.longestStreakDays, insights.records.longestStreakDays),
                ) {
                    Heatmap(cells = insights.heatmap, weeks = insights.heatmapWeeks, modifier = Modifier.testTag(HomeHeatmapTag))
                }
            }

            // --- weekday shape ----------------------------------------------------------
            item {
                val best = insights.byWeekday.maxByOrNull { it.averageMinutes }
                ChartCard(
                    title = stringResource(R.string.home_weekday_title),
                    subtitle = best?.takeIf { it.minutes > 0 }?.let {
                        stringResource(R.string.home_weekday_subtitle, it.day.getDisplayName(TextStyle.FULL, locale), it.averageMinutes)
                    } ?: "",
                ) {
                    ColumnChart(
                        values = insights.byWeekday.map { it.averageMinutes },
                        labels = DayOfWeek.entries.map { it.getDisplayName(TextStyle.SHORT, locale).take(2) },
                        emphasisIndex = insights.today.dayOfWeek.ordinal,
                    )
                }
            }

            // --- hour shape -------------------------------------------------------------
            item {
                val peak = insights.peakHour
                ChartCard(
                    title = stringResource(R.string.home_hour_title),
                    subtitle = peak?.let { stringResource(R.string.home_hour_subtitle, hourLabel(it), hourLabel((it + 1) % 24)) } ?: "",
                ) {
                    ColumnChart(
                        values = insights.byHour,
                        labels = listOf("0", "", "", "", "", "", "6", "", "", "", "", "", "12", "", "", "", "", "", "18", "", "", "", "", ""),
                        emphasisIndex = peak,
                        barHeight = 72.dp,
                    )
                }
            }

            // --- pace -------------------------------------------------------------------
            if (insights.pace.size >= 2) {
                item {
                    ChartCard(
                        title = stringResource(R.string.home_pace_title),
                        subtitle = pluralStringResource(R.plurals.home_pace_subtitle, insights.pace.size, insights.pace.size, insights.averagePace.toInt()),
                    ) {
                        TrendLine(points = insights.pace.map { it.pagesPerHour }, mean = insights.averagePace)
                    }
                }
            }

            // --- books ------------------------------------------------------------------
            if (state.perBook.isNotEmpty()) {
                item {
                    ChartCard(title = stringResource(R.string.stats_by_book), subtitle = "") {
                        val removed = stringResource(R.string.stats_removed_book)
                        HorizontalBars(
                            rows = state.perBook.take(6).map { b ->
                                val pct = b.progressFraction?.let { " · ${(it * 100).toInt()}%" } ?: ""
                                BarRow((b.title ?: removed) + pct, b.minutes, formatHours(b.minutes))
                            },
                        )
                    }
                }
            }

            // --- records ----------------------------------------------------------------
            item {
                val r = insights.records
                ChartCard(title = stringResource(R.string.home_records_title), subtitle = "") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        RecordRow(stringResource(R.string.home_record_longest_session), formatHours(r.longestSessionMinutes) + (r.longestSessionBook?.let { " · $it" } ?: ""))
                        RecordRow(stringResource(R.string.home_record_best_day), r.bestDay?.let { "${formatHours(r.bestDayMinutes)} · ${it.format(dayFormatter)}" } ?: "—")
                        RecordRow(stringResource(R.string.home_record_longest_streak), pluralStringResource(R.plurals.stats_days, r.longestStreakDays, r.longestStreakDays))
                        RecordRow(stringResource(R.string.home_record_sessions), "${r.totalSessions}")
                        RecordRow(stringResource(R.string.home_record_since), r.firstReadOn?.format(dayFormatter) ?: "—")
                    }
                }
            }
        }
    }
}

@Composable
private fun Kpi(value: String, label: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 12.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun ChartCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun RecordRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun formatHours(minutes: Long): String =
    if (minutes < 60) stringResource(R.string.stats_minutes_value, minutes)
    else stringResource(R.string.stats_hours_value, minutes / 60, minutes % 60)

private fun hourLabel(h: Int): String = String.format(Locale.ROOT, "%02d:00", h)
