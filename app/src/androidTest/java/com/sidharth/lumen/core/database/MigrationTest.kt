package com.sidharth.lumen.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * §4: "Any migration that risks highlights must be written with a test that populates the
 * old schema, migrates, and asserts every highlight still resolves." This is that test
 * for v1 → v2.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        LumenDatabase::class.java,
    )

    @Test
    fun migrate1To2_preservesEveryHighlightField() {
        helper.createDatabase(dbName, 1).use { db ->
            // Two v1 highlights, written with raw SQL against the v1 schema.
            db.execSQL(
                """
                INSERT INTO highlights
                    (book_content_hash, color_id, created_at, updated_at, resource_href,
                     text_position_start, text_position_end,
                     quote_prefix, quote_exact, quote_suffix, chapter_title, progress_fraction)
                VALUES
                    ('hash-a', 2, 111, 222, '/OEBPS/ch1.xhtml', 10, 25,
                     'before ', 'the exact quote', ' after', 'Chapter One', 0.25),
                    ('hash-b', 4, 333, 444, '/OEBPS/ch9.xhtml', 0, 5,
                     '', 'lone', '', NULL, 0.9)
                """.trimIndent(),
            )
        }

        val db = helper.runMigrationsAndValidate(dbName, 2, true, MIGRATION_1_2)

        db.query("SELECT * FROM highlights ORDER BY id").use { cursor ->
            assertEquals(2, cursor.count)

            cursor.moveToFirst()
            fun col(name: String) = cursor.getColumnIndexOrThrow(name)

            assertEquals("hash-a", cursor.getString(col("book_content_hash")))
            assertEquals(2, cursor.getInt(col("color_id")))
            assertEquals(111, cursor.getLong(col("created_at")))
            assertEquals(222, cursor.getLong(col("updated_at")))
            assertEquals("/OEBPS/ch1.xhtml", cursor.getString(col("resource_href")))
            assertEquals("before ", cursor.getString(col("quote_prefix")))
            assertEquals("the exact quote", cursor.getString(col("quote_exact")))
            assertEquals(" after", cursor.getString(col("quote_suffix")))
            assertEquals("Chapter One", cursor.getString(col("chapter_title")))
            assertEquals(0.25f, cursor.getFloat(col("progress_fraction")), 1e-5f)
            // The new anchor columns start empty; the quote selector carries resolution.
            assertTrue(cursor.isNull(col("resource_progression")))
            assertTrue(cursor.isNull(col("locator_json")))

            cursor.moveToNext()
            assertEquals("hash-b", cursor.getString(col("book_content_hash")))
            assertEquals("lone", cursor.getString(col("quote_exact")))
            assertNull(cursor.getString(col("chapter_title")))
            assertEquals(0.9f, cursor.getFloat(col("progress_fraction")), 1e-5f)
        }

        // The new tables exist and are empty.
        db.query("SELECT COUNT(*) FROM notes").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        db.query("SELECT COUNT(*) FROM tags").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        db.query("SELECT COUNT(*) FROM highlight_tags").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
    }

    /**
     * The other half of "still resolves": a migrated highlight, whose position anchor is
     * gone, must be findable by its quote selector alone.
     */
    @Test
    fun migratedHighlightResolvesByQuoteFallback() {
        helper.createDatabase(dbName, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO highlights
                    (book_content_hash, color_id, created_at, updated_at, resource_href,
                     text_position_start, text_position_end,
                     quote_prefix, quote_exact, quote_suffix, chapter_title, progress_fraction)
                VALUES
                    ('hash-c', 0, 1, 1, '/ch2.xhtml', 100, 120,
                     'nuzzled into his ', 'breast in an effort', ' to escape the',
                     'Ch 2', 0.1)
                """.trimIndent(),
            )
        }

        val db = helper.runMigrationsAndValidate(dbName, 2, true, MIGRATION_1_2)

        val document = "Winston Smith, his chin nuzzled into his breast in an effort to " +
            "escape the vile wind, slipped quickly through the glass doors."

        db.query("SELECT quote_prefix, quote_exact, quote_suffix FROM highlights").use { cursor ->
            cursor.moveToFirst()
            val match = com.sidharth.lumen.feature.highlight.QuoteFinder.find(
                document = document,
                prefix = cursor.getString(0),
                exact = cursor.getString(1),
                suffix = cursor.getString(2),
            )
            assertEquals(
                "breast in an effort",
                match?.let { document.substring(it.start, it.end) },
            )
            assertTrue(match!!.contextMatched)
        }
    }
}
