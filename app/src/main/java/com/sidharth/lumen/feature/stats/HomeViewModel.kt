package com.sidharth.lumen.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sidharth.lumen.core.database.BookDao
import com.sidharth.lumen.core.database.BookReadingTotal
import com.sidharth.lumen.core.database.ReadingSessionDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId
import javax.inject.Inject

data class HomeUiState(
    val loaded: Boolean = false,
    val insights: ReadingInsights? = null,
    val perBook: List<BookReadingTotal> = emptyList(),
) {
    val isEmpty: Boolean get() = loaded && (insights?.records?.totalSessions ?: 0) == 0
}

/**
 * The Home tab: every number derives from `reading_sessions` plus book titles, computed
 * in [ReadingInsights] with the device zone passed in. Re-computed whenever a session row
 * changes, so the dashboard is live while a book is open behind it.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    sessionDao: ReadingSessionDao,
    bookDao: BookDao,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        sessionDao.observeAll(),
        sessionDao.observePerBook(),
        bookDao.observeAll(),
    ) { sessions, perBook, books ->
        val titles = books.associate { it.contentHash to it.title }
        HomeUiState(
            loaded = true,
            insights = ReadingInsights.compute(
                sessions = sessions,
                titles = titles,
                now = System.currentTimeMillis(),
                zone = ZoneId.systemDefault(),
            ),
            perBook = perBook,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
