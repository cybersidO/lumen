package com.sidharth.lumen.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A free-text note. Usually attached to a highlight; [highlightId] is nullable because
 * §4 allows standalone notes (their UI arrives with the notes database in M6).
 *
 * No foreign key to highlights, same reasoning as highlights→books: cascades are managed
 * in repository transactions so the rules stay visible in code rather than hidden in DDL.
 */
@Entity(
    tableName = "notes",
    indices = [Index("highlight_id"), Index("book_content_hash")],
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "highlight_id")
    val highlightId: Long?,

    @ColumnInfo(name = "book_content_hash")
    val bookContentHash: String,

    val body: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)

@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)],
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
)

@Entity(
    tableName = "highlight_tags",
    primaryKeys = ["highlight_id", "tag_id"],
    indices = [Index("tag_id")],
)
data class HighlightTagEntity(
    @ColumnInfo(name = "highlight_id")
    val highlightId: Long,

    @ColumnInfo(name = "tag_id")
    val tagId: Long,
)
