package com.sidharth.lumen.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sidharth.lumen.core.database.BookReadingTotal
import com.sidharth.lumen.core.database.DailyReading
import com.sidharth.lumen.core.database.ReadingSessionDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class StatsUiState(
    val loaded: Boolean = false,
    val todayMinutes: Long = 0,
    val weekMinutes: Long = 0,
    val totalMinutes: Long = 0,
    val totalPages: Long = 0,
    val streakDays: Int = 0,
    /** Last 14 days, oldest first, zero-filled so the bars line up with the calendar. */
    val lastTwoWeeks: List<DayBar> = emptyList(),
    val perBook: List<BookReadingTotal> = emptyList(),
) {
    val pagesPerHour: Int
        get() = if (totalMinutes <= 0) 0 else (totalPages * 60 / totalMinutes).toInt()
}

data class DayBar(val dayEpoch: Long, val minutes: Long, val isToday: Boolean)

/**
 * The stats screen's numbers. Everything here derives from `reading_sessions`; nothing is
 * counted anywhere else, so deleting that table is deleting the stats, and the backup
 * ZIP carries them because it carries the database.
 */
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val dao: ReadingSessionDao,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val today: LocalDate = LocalDate.now(zone)
    private val todayEpoch: Long = today.atStartOfDay(zone).toInstant().toEpochMilli()
    private val twoWeeksAgoEpoch: Long =
        today.minusDays(13).atStartOfDay(zone).toInstant().toEpochMilli()

    val uiState: StateFlow<StatsUiState> = combine(
        dao.observeDaily(twoWeeksAgoEpoch),
        dao.observeTotalMinutes(),
        dao.observeTotalPages(),
        dao.observePerBook(),
    ) { daily, totalMinutes, totalPages, perBook ->
        val byDay = daily.associate { it.dayEpoch to it.minutes }
        val bars = (13 downTo 0).map { back ->
            val epoch = today.minusDays(back.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
            DayBar(epoch, byDay[epoch] ?: 0, isToday = back == 0)
        }
        StatsUiState(
            loaded = true,
            todayMinutes = byDay[todayEpoch] ?: 0,
            weekMinutes = bars.takeLast(7).sumOf { it.minutes },
            totalMinutes = totalMinutes,
            totalPages = totalPages,
            streakDays = ReadingStreak.compute(dao.readingDays(), todayEpoch),
            lastTwoWeeks = bars,
            perBook = perBook,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())
}
