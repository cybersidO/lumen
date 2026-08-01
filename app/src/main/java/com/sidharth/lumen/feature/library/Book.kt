package com.sidharth.lumen.feature.library

import com.sidharth.lumen.core.books.BookFormat
import com.sidharth.lumen.core.database.BookEntity

/** What the library screen works with. Deliberately free of Room annotations. */
data class Book(
    val id: Long,
    val contentHash: String,
    val title: String,
    val author: String?,
    val format: BookFormat?,
    val filePath: String,
    val coverPath: String?,
    val addedAt: Long,
    val lastOpenedAt: Long?,
    val progressFraction: Float,
) {
    /** Sorting by "recent" means recently *read*, falling back to recently added. */
    val recencyTimestamp: Long get() = lastOpenedAt ?: addedAt

    val progressPercent: Int get() = (progressFraction * 100).toInt().coerceIn(0, 100)
}

fun BookEntity.toBook(): Book = Book(
    id = id,
    contentHash = contentHash,
    title = title,
    author = author,
    format = BookFormat.fromStorageName(format),
    filePath = filePath,
    coverPath = coverPath,
    addedAt = addedAt,
    lastOpenedAt = lastOpenedAt,
    progressFraction = progressFraction,
)
