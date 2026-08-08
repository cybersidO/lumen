package com.sidharth.lumen.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The durable asset (§4). The anchor is document-relative and threefold:
 *
 *  1. [resourceHref] — which resource in the publication;
 *  2. [resourceProgression] — position-first resolution: how far through that resource,
 *     0..1. **Schema v2 refinement of §4's `textPositionStart/End`:** Readium's
 *     reflowable-EPUB locators expose progression, not character offsets — there is
 *     nothing that could ever have been written into integer offset columns. The v1→v2
 *     migration is covered by a test that proves highlight data survives;
 *  3. the text-quote selector [quotePrefix]/[quoteExact]/[quoteSuffix] (~32 chars context
 *     each side) — the fallback when position no longer matches, and the part that makes
 *     a highlight survive re-import and repagination.
 *
 * [locatorJson] carries the full Readium locator verbatim; it is the fast path handed to
 * the rendering engine, never the source of truth — the three fields above are.
 */
@Entity(
    tableName = "highlights",
    indices = [Index(value = ["book_content_hash"])],
)
data class HighlightEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Points at [BookEntity.contentHash]; no FK, so orphans survive deletion (§2.1). */
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

    @ColumnInfo(name = "resource_progression")
    val resourceProgression: Double?,

    @ColumnInfo(name = "locator_json")
    val locatorJson: String?,

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
