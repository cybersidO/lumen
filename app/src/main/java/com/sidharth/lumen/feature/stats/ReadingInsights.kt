package com.sidharth.lumen.feature.stats

import com.sidharth.lumen.core.database.ReadingSessionEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** One cell of the contribution-style calendar. */
data class HeatCell(val date: LocalDate, val minutes: Long, val isToday: Boolean = false)

/** A day-of-week column: total minutes and how many distinct days contributed. */
data class WeekdayLoad(val day: DayOfWeek, val minutes: Long, val days: Int) {
    val averageMinutes: Long get() = if (days == 0) 0 else minutes / days
}

/** One session's pace, for the trend line. */
data class PacePoint(val endedAt: Long, val pagesPerHour: Float)

data class ReadingRecords(
    val longestSessionMinutes: Long,
    val longestSessionBook: String?,
    val bestDay: LocalDate?,
    val bestDayMinutes: Long,
    val longestStreakDays: Int,
    val firstReadOn: LocalDate?,
    val totalSessions: Int,
)

/**
 * Everything the Home dashboard shows, computed from raw sessions in one pass.
 *
 * Kept as plain arithmetic over `ReadingSessionEntity` rather than a dozen SQL
 * aggregates: the queries were the part that went wrong last time (a UTC/local slip
 * that the tests caught), and a pure function with the zone passed in is far easier to
 * test for exactly that class of bug.
 */
data class ReadingInsights(
    val today: LocalDate,
    val todayMinutes: Long,
    val yesterdayMinutes: Long,
    val weekMinutes: Long,
    val previousWeekMinutes: Long,
    val totalMinutes: Long,
    val totalPages: Long,
    val streakDays: Int,
    /** Last 14 days, oldest first, zero-filled. */
    val sparkline: List<Long>,
    /** [weeks] × 7 cells, oldest first, ending today. */
    val heatmap: List<HeatCell>,
    val heatmapWeeks: Int,
    val byWeekday: List<WeekdayLoad>,
    /** 24 buckets of minutes, apportioned across hour boundaries. */
    val byHour: List<Long>,
    val pace: List<PacePoint>,
    val records: ReadingRecords,
) {
    val pagesPerHour: Int get() = if (totalMinutes <= 0) 0 else (totalPages * 60 / totalMinutes).toInt()
    val peakHour: Int? get() = byHour.indices.maxByOrNull { byHour[it] }?.takeIf { byHour[it] > 0 }
    val averagePace: Float get() = if (pace.isEmpty()) 0f else pace.map { it.pagesPerHour }.average().toFloat()

    companion object {
        const val SPARKLINE_DAYS = 14
        const val HEATMAP_WEEKS = 16

        fun compute(
            sessions: List<ReadingSessionEntity>,
            titles: Map<String, String>,
            now: Long,
            zone: ZoneId,
        ): ReadingInsights {
            val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            fun dayOf(millis: Long) = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

            // --- per-day minutes (a session belongs to the day it started) ---------------
            val minutesByDay = HashMap<LocalDate, Long>()
            for (s in sessions) {
                val d = dayOf(s.startedAt)
                minutesByDay[d] = (minutesByDay[d] ?: 0) + (s.endedAt - s.startedAt) / 60_000
            }
            fun day(d: LocalDate) = minutesByDay[d] ?: 0L

            val sparkline = (SPARKLINE_DAYS - 1 downTo 0).map { day(today.minusDays(it.toLong())) }

            // --- heatmap: whole weeks ending with today's week, Monday-first -------------
            val endOfGrid = today.plusDays(((DayOfWeek.SUNDAY.value - today.dayOfWeek.value) + 7L) % 7)
            val startOfGrid = endOfGrid.minusDays(HEATMAP_WEEKS * 7L - 1)
            val heatmap = (0 until HEATMAP_WEEKS * 7).map { i ->
                val d = startOfGrid.plusDays(i.toLong())
                HeatCell(d, if (d.isAfter(today)) 0 else day(d), isToday = d == today)
            }

            // --- weekday load, over every day that has any reading ---------------------
            val byWeekday = DayOfWeek.entries.map { dow ->
                val days = minutesByDay.filter { (d, m) -> d.dayOfWeek == dow && m > 0 }
                WeekdayLoad(dow, days.values.sum(), days.size)
            }

            // --- hour of day, minutes apportioned across boundaries ---------------------
            val byHour = LongArray(24)
            for (s in sessions) {
                var cursor = s.startedAt
                while (cursor < s.endedAt) {
                    val zt = Instant.ofEpochMilli(cursor).atZone(zone)
                    val nextHour = zt.truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant().toEpochMilli()
                    val chunkEnd = minOf(nextHour, s.endedAt)
                    byHour[zt.hour] += (chunkEnd - cursor) / 60_000
                    cursor = chunkEnd
                }
            }

            // --- pace: sessions with real reading in them, newest last ----------------
            val pace = sessions
                .filter { it.pagesTurned > 0 && it.endedAt - it.startedAt >= 5 * 60_000 }
                .sortedBy { it.endedAt }
                .map { PacePoint(it.endedAt, it.pagesTurned * 3_600_000f / (it.endedAt - it.startedAt)) }
                .takeLast(30)

            // --- streaks ----------------------------------------------------------------
            val readingDays = minutesByDay.filterValues { it > 0 }.keys
            val current = run {
                var cursor = if (today in readingDays) today else today.minusDays(1)
                var n = 0
                while (cursor in readingDays) { n++; cursor = cursor.minusDays(1) }
                n
            }
            val longest = run {
                var best = 0; var run = 0; var prev: LocalDate? = null
                for (d in readingDays.sorted()) {
                    run = if (prev != null && prev.plusDays(1) == d) run + 1 else 1
                    best = maxOf(best, run); prev = d
                }
                best
            }

            val longestSession = sessions.maxByOrNull { it.endedAt - it.startedAt }
            val bestDay = minutesByDay.maxByOrNull { it.value }

            val week = (0..6).sumOf { day(today.minusDays(it.toLong())) }
            val previousWeek = (7..13).sumOf { day(today.minusDays(it.toLong())) }

            return ReadingInsights(
                today = today,
                todayMinutes = day(today),
                yesterdayMinutes = day(today.minusDays(1)),
                weekMinutes = week,
                previousWeekMinutes = previousWeek,
                totalMinutes = sessions.sumOf { (it.endedAt - it.startedAt) / 60_000 },
                totalPages = sessions.sumOf { it.pagesTurned.toLong() },
                streakDays = current,
                sparkline = sparkline,
                heatmap = heatmap,
                heatmapWeeks = HEATMAP_WEEKS,
                byWeekday = byWeekday,
                byHour = byHour.toList(),
                pace = pace,
                records = ReadingRecords(
                    longestSessionMinutes = longestSession?.let { (it.endedAt - it.startedAt) / 60_000 } ?: 0,
                    longestSessionBook = longestSession?.let { titles[it.bookContentHash] },
                    bestDay = bestDay?.key,
                    bestDayMinutes = bestDay?.value ?: 0,
                    longestStreakDays = longest,
                    firstReadOn = sessions.minOfOrNull { it.startedAt }?.let(::dayOf),
                    totalSessions = sessions.size,
                ),
            )
        }
    }
}
