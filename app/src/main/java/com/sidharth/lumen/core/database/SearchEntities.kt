package com.sidharth.lumen.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4

/**
 * §2.6 asks for "full-text search over highlight text and note bodies (SQLite FTS)".
 *
 * Both are **external-content** FTS tables: they index a column of a real table rather
 * than storing a second copy of the text. Room generates the sync triggers, so the index
 * cannot drift out of step with the highlights and notes themselves — which is the usual
 * way a hand-maintained FTS index goes wrong.
 */
@Entity(tableName = "highlights_fts")
@Fts4(contentEntity = HighlightEntity::class)
data class HighlightFts(
    @ColumnInfo(name = "quote_exact")
    val quoteExact: String,
)

@Entity(tableName = "notes_fts")
@Fts4(contentEntity = NoteEntity::class)
data class NoteFts(
    val body: String,
)
