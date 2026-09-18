package com.sidharth.lumen.feature.stats

import com.sidharth.lumen.core.database.ReadingSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ReadingInsightsTest {

    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")
    private val today: LocalDate = LocalDate.of(2026, 9, 18) // a Friday
    private fun at(date: LocalDate, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(date, java.time.LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()
    private val now = at(today, 20)

    private fun session(start: Long, minutes: Long, pages: Int = 0, book: String = "b1") = ReadingSessionEntity(
        bookContentHash = book, startedAt = start, endedAt = start + minutes * 60_000,
        startProgress = 0f, endProgress = 0f, pagesTurned = pages,
    )

    private fun compute(vararg s: ReadingSessionEntity) =
        ReadingInsights.compute(s.toList(), mapOf("b1" to "Middlemarch"), now, zone)

    @Test
    fun emptyIsAllZerosAndAFullGrid() {
        val i = compute()
        assertEquals(0L, i.todayMinutes); assertEquals(0, i.streakDays)
        assertEquals(14, i.sparkline.size); assertEquals(16 * 7, i.heatmap.size)
        assertEquals(24, i.byHour.size); assertNull(i.peakHour)
        assertEquals(0, i.records.totalSessions); assertNull(i.records.firstReadOn)
    }

    @Test
    fun heatmapEndsOnSundayOfThisWeekAndMarksToday() {
        val i = compute()
        assertEquals(DayOfWeek.MONDAY, i.heatmap.first().date.dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, i.heatmap.last().date.dayOfWeek)
        assertEquals(1, i.heatmap.count { it.isToday })
        assertEquals(today, i.heatmap.first { it.isToday }.date)
        // Days after today are in the grid but must carry no minutes.
        assertTrue(i.heatmap.filter { it.date.isAfter(today) }.all { it.minutes == 0L })
    }

    @Test
    fun aSessionAcrossAnHourBoundaryIsSplitBetweenTheHours() {
        // 21:40 → 22:20: 20 minutes in the 21h bucket, 20 in the 22h bucket.
        val i = compute(session(at(today, 21, 40), 40))
        assertEquals(20L, i.byHour[21]); assertEquals(20L, i.byHour[22])
        assertEquals(0L, i.byHour[20])
        assertTrue(i.peakHour == 21 || i.peakHour == 22)
    }

    @Test
    fun hourBucketsUseTheGivenZoneNotUtc() {
        // 21:40 IST is 16:10 UTC. If the zone were ignored the minutes would land in 16h.
        val i = compute(session(at(today, 21, 40), 10))
        assertEquals(10L, i.byHour[21]); assertEquals(0L, i.byHour[16])
    }

    @Test
    fun weekdayAveragesAreOverDaysReadNotCalendarDays() {
        // Two Fridays of 30 and 50 minutes → Friday average 40, not (30+50)/N-weeks.
        val i = compute(session(at(today, 9), 30), session(at(today.minusWeeks(1), 9), 50))
        val friday = i.byWeekday.first { it.day == DayOfWeek.FRIDAY }
        assertEquals(2, friday.days); assertEquals(40L, friday.averageMinutes)
        assertEquals(0L, i.byWeekday.first { it.day == DayOfWeek.MONDAY }.averageMinutes)
    }

    @Test
    fun streaksCurrentAndLongest() {
        // Read today, yesterday, day before (3), then a gap, then a 5-day run a fortnight ago.
        val s = (0..2).map { session(at(today.minusDays(it.toLong()), 8), 10) } +
            (10..14).map { session(at(today.minusDays(it.toLong()), 8), 10) }
        val i = compute(*s.toTypedArray())
        assertEquals(3, i.streakDays)
        assertEquals(5, i.records.longestStreakDays)
    }

    @Test
    fun paceIgnoresShortAndPagelessSittingsAndUsesPagesPerHour() {
        val i = compute(
            session(at(today, 8), 30, pages = 20),   // 40 pages/hour
            session(at(today, 10), 3, pages = 5),    // under 5 min: excluded
            session(at(today, 12), 30, pages = 0),   // no pages: excluded
        )
        assertEquals(1, i.pace.size)
        assertEquals(40f, i.pace.single().pagesPerHour, 0.01f)
    }

    @Test
    fun recordsAndDeltas() {
        val i = compute(
            session(at(today, 8), 45, pages = 30),
            session(at(today.minusDays(1), 8), 20),
            session(at(today.minusDays(1), 21), 70, book = "b1"),
        )
        assertEquals(45L, i.todayMinutes); assertEquals(90L, i.yesterdayMinutes)
        assertEquals(70L, i.records.longestSessionMinutes); assertEquals("Middlemarch", i.records.longestSessionBook)
        assertEquals(today.minusDays(1), i.records.bestDay); assertEquals(90L, i.records.bestDayMinutes)
        assertEquals(today.minusDays(1), i.records.firstReadOn)
        assertEquals(135L, i.weekMinutes); assertEquals(0L, i.previousWeekMinutes)
    }
}
