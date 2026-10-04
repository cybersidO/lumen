package com.sidharth.lumen.feature.stats

import com.sidharth.lumen.core.settings.ReadingSettings
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The daily-goal ring's arithmetic (M13). */
class DailyGoalTest {

    private fun state(todayMinutes: Long, goal: Int) = HomeUiState(
        loaded = true,
        insights = ReadingInsights(
            today = LocalDate.of(2026, 10, 4),
            todayMinutes = todayMinutes,
            yesterdayMinutes = 0,
            weekMinutes = todayMinutes,
            previousWeekMinutes = 0,
            totalMinutes = todayMinutes,
            totalPages = 0,
            streakDays = 0,
            sparkline = emptyList(),
            heatmap = emptyList(),
            heatmapWeeks = 0,
            byWeekday = emptyList(),
            byHour = List(24) { 0L },
            pace = emptyList(),
            records = ReadingRecords(0, null, null, 0, 0, null, 1),
        ),
        dailyGoalMinutes = goal,
    )

    @Test
    fun noGoalMeansNoRingAndNoProgress() {
        val s = state(todayMinutes = 30, goal = 0)
        assertEquals(0f, s.goalProgress)
        assertFalse(s.goalMet)
    }

    @Test
    fun progressIsTheFractionOfTheGoal() {
        assertEquals(0.5f, state(10, 20).goalProgress, 0.001f)
        assertEquals(0.25f, state(5, 20).goalProgress, 0.001f)
    }

    @Test
    fun exactlyMeetingTheGoalCounts() {
        val s = state(20, 20)
        assertEquals(1f, s.goalProgress, 0.001f)
        assertTrue(s.goalMet)
    }

    @Test
    fun overshootingFillsTheRingRatherThanLapping() {
        val s = state(90, 20)
        assertEquals("the ring shows done, not a score", 1f, s.goalProgress, 0.001f)
        assertTrue(s.goalMet)
    }

    @Test
    fun nothingReadYetIsAnEmptyRing() {
        val s = state(0, 20)
        assertEquals(0f, s.goalProgress, 0.001f)
        assertFalse(s.goalMet)
    }

    @Test
    fun theGoalIsClampedToSaneValues() {
        assertEquals(0, ReadingSettings(dailyGoalMinutes = -5).sanitised().dailyGoalMinutes)
        assertEquals(
            ReadingSettings.MAX_DAILY_GOAL_MINUTES,
            ReadingSettings(dailyGoalMinutes = 10_000).sanitised().dailyGoalMinutes,
        )
    }

    @Test
    fun theShippedDefaultsAreAGoalAndFullScreen() {
        val d = ReadingSettings.Defaults
        assertEquals(20, d.dailyGoalMinutes)
        assertTrue(d.fullScreen)
    }
}
