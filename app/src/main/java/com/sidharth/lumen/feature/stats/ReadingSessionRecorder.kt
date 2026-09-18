package com.sidharth.lumen.feature.stats

import com.sidharth.lumen.core.database.ReadingSessionDao
import com.sidharth.lumen.core.database.ReadingSessionEntity
import com.sidharth.lumen.core.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Owns the one open reading session for the book currently on screen.
 *
 * Lifecycle: [start] when the book is open and visible, [pageTurned] on every locator
 * change, [stop] when the reader leaves the foreground. The row is written on start and
 * updated in place on every turn, so a process kill loses only the seconds since the last
 * page rather than the whole sitting.
 *
 * Sessions shorter than [MIN_SESSION_MILLIS] are discarded on stop: opening a book by
 * mistake and backing out is not reading, and would otherwise litter the stats.
 */
class ReadingSessionRecorder @Inject constructor(
    private val dao: ReadingSessionDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val mutex = Mutex()
    private var open: Open? = null

    private class Open(
        val rowId: Long,
        val bookContentHash: String,
        val startedAt: Long,
        var lastProgress: Float,
        var pagesTurned: Int,
    )

    suspend fun start(bookContentHash: String, progress: Float, now: Long) = mutex.withLock {
        if (open?.bookContentHash == bookContentHash) return@withLock
        closeLocked(now)
        val id = withContext(ioDispatcher) {
            dao.insert(
                ReadingSessionEntity(
                    bookContentHash = bookContentHash,
                    startedAt = now,
                    endedAt = now,
                    startProgress = progress,
                    endProgress = progress,
                    pagesTurned = 0,
                ),
            )
        }
        open = Open(id, bookContentHash, now, progress, 0)
    }

    suspend fun pageTurned(progress: Float, now: Long) = mutex.withLock {
        val session = open ?: return@withLock
        session.pagesTurned++
        session.lastProgress = progress
        withContext(ioDispatcher) {
            dao.touch(session.rowId, now, progress, session.pagesTurned)
        }
    }

    suspend fun stop(now: Long) = mutex.withLock { closeLocked(now) }

    private suspend fun closeLocked(now: Long) {
        val session = open ?: return
        open = null
        withContext(ioDispatcher) {
            if (now - session.startedAt < MIN_SESSION_MILLIS) {
                dao.delete(session.rowId)
            } else {
                dao.touch(session.rowId, now, session.lastProgress, session.pagesTurned)
            }
        }
    }

    companion object {
        const val MIN_SESSION_MILLIS = 20_000L
    }
}
