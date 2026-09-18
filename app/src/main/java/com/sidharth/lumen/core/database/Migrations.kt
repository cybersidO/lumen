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

/**
 * v2 → v3: the dictionary lookup log (§2.4).
 *
 * Purely additive — one new table, no existing column touched — so highlights cannot be
 * affected. `LumenDatabaseMigrationTest` still runs the full v1 → v3 chain and re-asserts
 * that a v1 highlight survives both steps intact.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE lookup_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                word TEXT NOT NULL,
                lemma TEXT,
                book_content_hash TEXT,
                resource_href TEXT,
                chapter_title TEXT,
                sentence TEXT,
                dictionary_id TEXT NOT NULL,
                found INTEGER NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX index_lookup_events_word ON lookup_events(word)")
        db.execSQL(
            "CREATE INDEX index_lookup_events_book_content_hash " +
                "ON lookup_events(book_content_hash)",
        )
        db.execSQL("CREATE INDEX index_lookup_events_created_at ON lookup_events(created_at)")
    }
}

/**
 * v3 → v4: full-text search over highlights and notes (§2.6).
 *
 * The DDL and trigger definitions below are copied **verbatim** from Room's own exported
 * schema (`app/schemas/.../4.json`) rather than written by hand. Room validates the live
 * schema against that JSON on open, so anything less than an exact match — a backtick, a
 * trigger name — fails at runtime with an unhelpful error. Deriving them from the export
 * is the only way to be sure they agree.
 *
 * Both indexes are external-content (`content=`), so they hold no copy of the text and
 * the `INSERT INTO ..._fts(..._fts) VALUES('rebuild')` statements at the end populate them
 * from the existing rows. Highlights themselves are untouched.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE VIRTUAL TABLE IF NOT EXISTS `highlights_fts` " +
                "USING FTS4(`quote_exact` TEXT NOT NULL, content=`highlights`)",
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_highlights_fts_BEFORE_UPDATE " +
                "BEFORE UPDATE ON `highlights` BEGIN " +
                "DELETE FROM `highlights_fts` WHERE `docid`=OLD.`rowid`; END",
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_highlights_fts_BEFORE_DELETE " +
                "BEFORE DELETE ON `highlights` BEGIN " +
                "DELETE FROM `highlights_fts` WHERE `docid`=OLD.`rowid`; END",
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_highlights_fts_AFTER_UPDATE " +
                "AFTER UPDATE ON `highlights` BEGIN " +
                "INSERT INTO `highlights_fts`(`docid`, `quote_exact`) " +
                "VALUES (NEW.`rowid`, NEW.`quote_exact`); END",
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_highlights_fts_AFTER_INSERT " +
                "AFTER INSERT ON `highlights` BEGIN " +
                "INSERT INTO `highlights_fts`(`docid`, `quote_exact`) " +
                "VALUES (NEW.`rowid`, NEW.`quote_exact`); END",
        )

        db.execSQL(
            "CREATE VIRTUAL TABLE IF NOT EXISTS `notes_fts` " +
                "USING FTS4(`body` TEXT NOT NULL, content=`notes`)",
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_notes_fts_BEFORE_UPDATE " +
                "BEFORE UPDATE ON `notes` BEGIN " +
                "DELETE FROM `notes_fts` WHERE `docid`=OLD.`rowid`; END",
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_notes_fts_BEFORE_DELETE " +
                "BEFORE DELETE ON `notes` BEGIN " +
                "DELETE FROM `notes_fts` WHERE `docid`=OLD.`rowid`; END",
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_notes_fts_AFTER_UPDATE " +
                "AFTER UPDATE ON `notes` BEGIN " +
                "INSERT INTO `notes_fts`(`docid`, `body`) VALUES (NEW.`rowid`, NEW.`body`); END",
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_notes_fts_AFTER_INSERT " +
                "AFTER INSERT ON `notes` BEGIN " +
                "INSERT INTO `notes_fts`(`docid`, `body`) VALUES (NEW.`rowid`, NEW.`body`); END",
        )

        // Populate the indexes from rows that already exist.
        db.execSQL("INSERT INTO `highlights_fts`(`highlights_fts`) VALUES('rebuild')")
        db.execSQL("INSERT INTO `notes_fts`(`notes_fts`) VALUES('rebuild')")
    }
}

/**
 * v4 → v5: the review queue (§2.6 / §4's ReviewItem).
 *
 * Purely additive — one new table and its indexes — so highlights, notes and the FTS
 * indexes are untouched. The migration test still runs the whole v1 → v5 chain and
 * re-asserts that a v1 highlight arrives intact at the far end.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE review_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                source_type TEXT NOT NULL,
                source_id INTEGER NOT NULL,
                interval_index INTEGER NOT NULL,
                due_at INTEGER NOT NULL,
                last_result TEXT,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        // Unique on the source pair: seeding is an INSERT OR IGNORE sweep, so this is what
        // stops a word looked up twice from becoming two cards.
        db.execSQL(
            "CREATE UNIQUE INDEX index_review_items_source_type_source_id " +
                "ON review_items(source_type, source_id)",
        )
        db.execSQL("CREATE INDEX index_review_items_due_at ON review_items(due_at)")
    }
}

/**
 * v5 → v6 (M10): reading sessions for the stats screen. Purely additive; nothing that
 * holds highlights is touched. Column order and types mirror Room's exported `6.json`
 * so `MigrationTestHelper` validates the result against the entity.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE reading_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                book_content_hash TEXT NOT NULL,
                started_at INTEGER NOT NULL,
                ended_at INTEGER NOT NULL,
                start_progress REAL NOT NULL,
                end_progress REAL NOT NULL,
                pages_turned INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX index_reading_sessions_book_content_hash " +
                "ON reading_sessions(book_content_hash)",
        )
        db.execSQL("CREATE INDEX index_reading_sessions_started_at ON reading_sessions(started_at)")
    }
}
