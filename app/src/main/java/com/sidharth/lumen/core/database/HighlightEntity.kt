package com.sidharth.lumen.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Created in schema v1 even though highlighting itself is M4.
 *
 * Two reasons. First, M1 has to ask what to do with a book's highlights when deleting it,
 * which needs somewhere to count them. Second, §4 calls highlights the durable asset and
 * demands that any migration touching them be proved safe — so the anchoring columns are
 * defined once, up front, rather than bolted on later by a migration that could lose them.
 *
 * The anchor is deliberately document-relative (§2.5): a resource href, a character range
 * within that resource, and a text-quote selector with context on both sides. No pixel
 * positions, no page numbers.
 */
@Entity(
    tableName = "highlights",
    indices = [Index(value = ["book_content_hash"])],
)
data class HighlightEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /**
     * Points at [BookEntity.contentHash], not its row id, and has no foreign key on
     * purpose: deleting a book may leave its highlights behind (the user chooses), and
     * re-importing the same file must silently reunite them.
     */
    @ColumnInfo(name = "book_content_hash")
    val bookContentHash: String,

    @ColumnInfo(name = "color_id")
    val colorId: Int,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "resource_href")
    val resourceHref: String,

    @ColumnInfo(name = "text_position_start")
    val textPositionStart: Int,

    @ColumnInfo(name = "text_position_end")
    val textPositionEnd: Int,

    @ColumnInfo(name = "quote_prefix")
    val quotePrefix: String,

    @ColumnInfo(name = "quote_exact")
    val quoteExact: String,

    @ColumnInfo(name = "quote_suffix")
    val quoteSuffix: String,

    @ColumnInfo(name = "chapter_title")
    val chapterTitle: String?,

    @ColumnInfo(name = "progress_fraction")
    val progressFraction: Float,
)
