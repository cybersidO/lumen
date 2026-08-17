package com.sidharth.lumen.feature.review

import com.sidharth.lumen.core.database.ReviewCard
import com.sidharth.lumen.core.database.ReviewDao
import com.sidharth.lumen.core.database.ReviewItemEntity
import com.sidharth.lumen.core.database.ReviewSourceType
import com.sidharth.lumen.core.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReviewRepository @Inject constructor(
    private val reviewDao: ReviewDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    fun observeDueCount(now: Long = System.currentTimeMillis()): Flow<Int> =
        reviewDao.observeDueCount(now, ReviewSchedule.GRADUATED_INDEX)

    fun observeDue(now: Long = System.currentTimeMillis()): Flow<List<ReviewCard>> =
        reviewDao.observeDue(now, ReviewSchedule.GRADUATED_INDEX)

    /**
     * Tops the queue up from everything worth reviewing that has no card yet.
     *
     * Cheap and idempotent: the unique index on (source_type, source_id) plus INSERT OR
     * IGNORE means running this on every visit adds only what is genuinely new and never
     * disturbs an existing card's schedule.
     *
     * A card is due from the moment its source was captured, so a word looked up yesterday
     * is in today's queue — which is exactly §M7's acceptance condition.
     */
    suspend fun seed(): Int = withContext(ioDispatcher) {
        val lookups = reviewDao.unseededLookupIds().mapNotNull { id ->
            val capturedAt = reviewDao.lookupCreatedAt(id) ?: return@mapNotNull null
            newItem(ReviewSourceType.LOOKUP, id, capturedAt)
        }
        val highlights = reviewDao.unseededHighlightIds().mapNotNull { id ->
            val capturedAt = reviewDao.highlightCreatedAt(id) ?: return@mapNotNull null
            newItem(ReviewSourceType.HIGHLIGHT, id, capturedAt)
        }
        val all = lookups + highlights
        if (all.isNotEmpty()) reviewDao.insertIgnoring(all)
        all.size
    }

    suspend fun record(card: ReviewCard, result: ReviewResult, now: Long = System.currentTimeMillis()) =
        withContext(ioDispatcher) {
            reviewDao.record(
                id = card.itemId,
                index = ReviewSchedule.nextIntervalIndex(card.intervalIndex, result),
                dueAt = ReviewSchedule.nextDueAt(now, card.intervalIndex, result),
                result = result.name,
            )
        }

    private fun newItem(type: String, sourceId: Long, capturedAt: Long) = ReviewItemEntity(
        sourceType = type,
        sourceId = sourceId,
        intervalIndex = 0,
        dueAt = ReviewSchedule.initialDueAt(capturedAt),
        lastResult = null,
        createdAt = System.currentTimeMillis(),
    )
}
