package com.sidharth.lumen.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "books",
    indices = [Index(value = ["content_hash"], unique = true)],
)
data class BookEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /**
     * SHA-256 of the file's bytes. This — not the filename, not the row id — is a book's
     * real identity: re-importing the same file after a delete re-attaches its highlights.
     */
    @ColumnInfo(name = "content_hash")
    val contentHash: String,

    val title: String,
    val author: String?,
    val format: String,

    /** Absolute path inside app-private storage. The original file is never referenced. */
    @ColumnInfo(name = "file_path")
    val filePath: String,

    @ColumnInfo(name = "cover_path")
    val coverPath: String?,

    @ColumnInfo(name = "added_at")
    val addedAt: Long,

    @ColumnInfo(name = "last_opened_at")
    val lastOpenedAt: Long?,

    @ColumnInfo(name = "progress_fraction")
    val progressFraction: Float = 0f,

    /** Readium locator, serialised. Written by the reader in M2. */
    @ColumnInfo(name = "current_locator_json")
    val currentLocatorJson: String? = null,
)
