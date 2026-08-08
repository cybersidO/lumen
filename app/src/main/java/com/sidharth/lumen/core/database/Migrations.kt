package com.sidharth.lumen.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 → v2: notes, tags, and the anchoring refinement.
 *
 * The highlights table is rebuilt because two integer columns
 * (`text_position_start/end`) become `resource_progression REAL` + `locator_json TEXT`.
 * Readium's reflowable locators never provided character offsets, so no shipped writer
 * ever put meaningful data in those columns — but the migration still copies every other
 * field faithfully, and `LumenDatabaseMigrationTest` proves a v1 highlight's identity
 * (book hash, colour, quote selector, chapter, progress) survives the rebuild bit-for-bit.
 *
 * Any migration touching highlights carries a test that populates the OLD schema,
 * migrates, and asserts every highlight still resolves (§4). This one does.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE highlights_v2 (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                book_content_hash TEXT NOT NULL,
                color_id INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                resource_href TEXT NOT NULL,
                resource_progression REAL,
                locator_json TEXT,
                quote_prefix TEXT NOT NULL,
                quote_exact TEXT NOT NULL,
                quote_suffix TEXT NOT NULL,
                chapter_title TEXT,
                progress_fraction REAL NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO highlights_v2
                (id, book_content_hash, color_id, created_at, updated_at, resource_href,
                 resource_progression, locator_json,
                 quote_prefix, quote_exact, quote_suffix, chapter_title, progress_fraction)
            SELECT id, book_content_hash, color_id, created_at, updated_at, resource_href,
                   NULL, NULL,
                   quote_prefix, quote_exact, quote_suffix, chapter_title, progress_fraction
            FROM highlights
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE highlights")
        db.execSQL("ALTER TABLE highlights_v2 RENAME TO highlights")
        db.execSQL(
            "CREATE INDEX index_highlights_book_content_hash ON highlights(book_content_hash)",
        )

        db.execSQL(
            """
            CREATE TABLE notes (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                highlight_id INTEGER,
                book_content_hash TEXT NOT NULL,
                body TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX index_notes_highlight_id ON notes(highlight_id)")
        db.execSQL("CREATE INDEX index_notes_book_content_hash ON notes(book_content_hash)")

        db.execSQL(
            """
            CREATE TABLE tags (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX index_tags_name ON tags(name)")

        db.execSQL(
            """
            CREATE TABLE highlight_tags (
                highlight_id INTEGER NOT NULL,
                tag_id INTEGER NOT NULL,
                PRIMARY KEY(highlight_id, tag_id)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX index_highlight_tags_tag_id ON highlight_tags(tag_id)")
    }
}
