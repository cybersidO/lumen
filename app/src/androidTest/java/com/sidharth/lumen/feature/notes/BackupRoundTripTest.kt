package com.sidharth.lumen.feature.notes

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sidharth.lumen.core.database.BookEntity
import com.sidharth.lumen.core.database.HighlightEntity
import com.sidharth.lumen.core.database.LumenDatabase
import com.sidharth.lumen.core.database.MIGRATION_1_2
import com.sidharth.lumen.core.database.MIGRATION_2_3
import com.sidharth.lumen.core.database.MIGRATION_3_4
import com.sidharth.lumen.core.database.NoteEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * §2.6: "Backup / restore … This is my safety net; test the round-trip."
 *
 * The test is the real thing end to end — a real on-disk Room database, a real ZIP written
 * by [BackupManager], the data then destroyed, and the same ZIP restored. Anything less
 * (mocking the zip, or asserting only that a file was produced) would not catch the
 * failure that actually matters: a backup that restores to something different.
 */
@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: LumenDatabase
    private lateinit var backupManager: BackupManager
    // JUnit builds a fresh instance per test method, and DataStore refuses two live
    // instances over one file — so each test gets its own file and its own scope, both
    // torn down afterwards.
    private val settingsFile =
        File(context.filesDir, "backup-test-${System.nanoTime()}.preferences_pb")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val settingsStore = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { settingsFile },
    )

    private fun openDatabase() = Room.databaseBuilder(
        context,
        LumenDatabase::class.java,
        LumenDatabase.NAME,
    ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()

    @Before
    fun setUp() {
        // Start from nothing so counts are unambiguous.
        context.deleteDatabase(LumenDatabase.NAME)
        settingsFile.delete()
        database = openDatabase()
        backupManager = BackupManager(context, database, settingsStore, Dispatchers.IO)
    }

    @After
    fun tearDown() {
        runCatching { database.close() }
        context.deleteDatabase(LumenDatabase.NAME)
        scope.cancel()
        settingsFile.delete()
    }

    private suspend fun seed() {
        database.bookDao().insert(
            BookEntity(
                contentHash = "hash-round-trip",
                title = "Middlemarch",
                author = "George Eliot",
                format = "EPUB",
                filePath = "/books/hash-round-trip.epub",
                coverPath = null,
                addedAt = 1_000,
                lastOpenedAt = null,
            ),
        )
        val highlightId = database.highlightDao().insert(
            HighlightEntity(
                bookContentHash = "hash-round-trip",
                colorId = 2,
                createdAt = 111,
                updatedAt = 222,
                resourceHref = "/OEBPS/ch1.xhtml",
                resourceProgression = 0.5,
                locatorJson = """{"href":"/OEBPS/ch1.xhtml"}""",
                quotePrefix = "before ",
                quoteExact = "the exact words worth keeping",
                quoteSuffix = " after",
                chapterTitle = "Chapter One",
                progressFraction = 0.25f,
            ),
        )
        database.noteDao().insert(
            NoteEntity(
                highlightId = highlightId,
                bookContentHash = "hash-round-trip",
                body = "why this passage matters",
                createdAt = 333,
                updatedAt = 333,
            ),
        )
        settingsStore.edit { it[stringPreferencesKey("theme_mode")] = "amoled" }
    }

    @Test
    fun backupThenWipeThenRestoreBringsEverythingBack() = runTest {
        seed()

        val backupBytes = ByteArrayOutputStream().use { out ->
            val result = backupManager.writeBackup(out)
            assertTrue("backup failed: $result", result is BackupResult.Success)
            out.toByteArray()
        }
        assertTrue("backup should not be empty", backupBytes.isNotEmpty())

        // Destroy everything, the way "wipe app data" would.
        database.close()
        context.deleteDatabase(LumenDatabase.NAME)
        settingsStore.edit { it.clear() }
        database = openDatabase()
        backupManager = BackupManager(context, database, settingsStore, Dispatchers.IO)
        assertEquals(0, database.highlightDao().countForBook("hash-round-trip"))

        val restore = backupManager.restoreBackup(ByteArrayInputStream(backupBytes))
        assertTrue("restore failed: $restore", restore is RestoreResult.Success)
        assertEquals(1, (restore as RestoreResult.Success).highlights)

        // Reopen against the restored file and check the data field by field — a count
        // alone would pass even if every column had been mangled.
        database = openDatabase()
        val highlights = database.highlightDao().forBook("hash-round-trip")
        assertEquals(1, highlights.size)
        val restored = highlights.single()
        assertEquals("the exact words worth keeping", restored.quoteExact)
        assertEquals("before ", restored.quotePrefix)
        assertEquals(" after", restored.quoteSuffix)
        assertEquals(2, restored.colorId)
        assertEquals("Chapter One", restored.chapterTitle)
        assertEquals(0.25f, restored.progressFraction, 1e-6f)
        assertEquals("/OEBPS/ch1.xhtml", restored.resourceHref)

        assertEquals(
            "why this passage matters",
            database.noteDao().forHighlight(restored.id)?.body,
        )
        assertEquals("Middlemarch", database.bookDao().findByHash("hash-round-trip")?.title)
    }

    /** The search index has to come back working, not just the rows. */
    @Test
    fun fullTextSearchStillWorksAfterRestore() = runTest {
        seed()

        val bytes = ByteArrayOutputStream().use { out ->
            backupManager.writeBackup(out)
            out.toByteArray()
        }
        database.close()
        context.deleteDatabase(LumenDatabase.NAME)
        database = openDatabase()
        backupManager = BackupManager(context, database, settingsStore, Dispatchers.IO)
        backupManager.restoreBackup(ByteArrayInputStream(bytes))
        database = openDatabase()

        val hits = database.query(
            "SELECT quote_exact FROM highlights WHERE id IN " +
                "(SELECT rowid FROM highlights_fts WHERE highlights_fts MATCH ?)",
            arrayOf("\"keeping\"*"),
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        assertEquals(listOf("the exact words worth keeping"), hits)
    }

    @Test
    fun restoringSomethingThatIsNotABackupLeavesDataAlone() = runTest {
        seed()
        val before = database.highlightDao().countForBook("hash-round-trip")

        val result = backupManager.restoreBackup(
            ByteArrayInputStream("this is not a zip file".toByteArray()),
        )

        assertTrue("expected failure, got $result", result is RestoreResult.Failed)
        // The existing library must be untouched by a rejected restore.
        assertEquals(before, database.highlightDao().countForBook("hash-round-trip"))
    }
}
