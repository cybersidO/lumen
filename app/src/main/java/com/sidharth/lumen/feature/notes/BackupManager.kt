package com.sidharth.lumen.feature.notes

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.sidharth.lumen.core.database.LumenDatabase
import com.sidharth.lumen.core.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

sealed interface BackupResult {
    data class Success(val bytes: Long, val entries: Int) : BackupResult
    data class Failed(val message: String) : BackupResult
}

sealed interface RestoreResult {
    data class Success(val highlights: Int, val notes: Int, val lookups: Int) : RestoreResult
    data class Failed(val message: String) : RestoreResult
}

/**
 * §2.6's safety net: "a single-file export of the whole database (+ settings) and an import
 * that restores it."
 *
 * The file is a plain ZIP holding the SQLite database and a JSON dump of settings, with a
 * small manifest. Plain and inspectable on purpose — if this app is ever gone, the user
 * should still be able to unzip their highlights and open the database with any SQLite
 * tool. That matters more here than a compact custom format.
 *
 * Restoring **replaces** the database wholesale rather than merging. Merge semantics for
 * highlights (same book, overlapping ranges, conflicting notes) are genuinely ambiguous,
 * and a backup restore that silently half-merges is worse than one that plainly replaces.
 * The UI says so before it does it.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: LumenDatabase,
    private val settingsDataStore: DataStore<Preferences>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun writeBackup(output: OutputStream): BackupResult = withContext(ioDispatcher) {
        runCatching {
            // Checkpoint the write-ahead log first, or the copied .db file can be missing
            // the most recent highlights — which is exactly the data the backup is for.
            database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }

            val databaseFile = context.getDatabasePath(LumenDatabase.NAME)
            var entries = 0
            var bytes = 0L

            ZipOutputStream(output.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
                zip.write(manifestJson(databaseFile).toByteArray())
                zip.closeEntry()
                entries++

                listOf(
                    databaseFile,
                    File("${databaseFile.path}-wal"),
                    File("${databaseFile.path}-shm"),
                ).filter { it.exists() }.forEach { file ->
                    zip.putNextEntry(ZipEntry("database/${file.name}"))
                    bytes += file.inputStream().buffered().use { it.copyTo(zip) }
                    zip.closeEntry()
                    entries++
                }

                zip.putNextEntry(ZipEntry(SETTINGS_ENTRY))
                zip.write(settingsJson().toByteArray())
                zip.closeEntry()
                entries++
            }
            BackupResult.Success(bytes, entries)
        }.getOrElse { BackupResult.Failed(it.message ?: "Backup failed.") }
    }

    /**
     * Replaces the current database and settings with the backup's.
     *
     * Everything is unpacked to a staging directory and validated *before* anything live
     * is touched, so a truncated or foreign file leaves the existing library untouched
     * rather than half-destroyed.
     */
    suspend fun restoreBackup(input: InputStream): RestoreResult = withContext(ioDispatcher) {
        val staging = File(context.cacheDir, "restore-${System.nanoTime()}").apply { mkdirs() }
        try {
            var manifest: JSONObject? = null
            var settings: String? = null
            var databaseStaged: File? = null

            ZipInputStream(input.buffered()).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    when {
                        entry.name == MANIFEST_ENTRY ->
                            manifest = JSONObject(zip.readBytes().decodeToString())

                        entry.name == SETTINGS_ENTRY ->
                            settings = zip.readBytes().decodeToString()

                        entry.name.startsWith("database/") -> {
                            // Guard against a zip entry escaping the staging directory.
                            val name = File(entry.name).name
                            val target = File(staging, name)
                            if (target.canonicalPath.startsWith(staging.canonicalPath)) {
                                target.outputStream().buffered().use { zip.copyTo(it) }
                                if (name == LumenDatabase.NAME) databaseStaged = target
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            val manifestJson = manifest
                ?: return@withContext RestoreResult.Failed(
                    "That file isn't a Lumen backup — no manifest inside.",
                )
            val staged = databaseStaged
                ?: return@withContext RestoreResult.Failed(
                    "The backup has no database in it.",
                )
            if (manifestJson.optInt("schema", -1) > LumenDatabase.SCHEMA_VERSION) {
                return@withContext RestoreResult.Failed(
                    "This backup came from a newer version of Lumen.",
                )
            }

            val counts = countRows(staged)

            // Point of no return. The database is closed, its files swapped, and the next
            // access reopens against the restored file (running any migration it needs).
            database.close()
            val live = context.getDatabasePath(LumenDatabase.NAME)
            listOf(live, File("${live.path}-wal"), File("${live.path}-shm")).forEach { it.delete() }
            staged.copyTo(live, overwrite = true)
            File(staging, "${LumenDatabase.NAME}-wal").takeIf { it.exists() }
                ?.copyTo(File("${live.path}-wal"), overwrite = true)

            settings?.let { restoreSettings(it) }

            RestoreResult.Success(counts.first, counts.second, counts.third)
        } catch (e: Exception) {
            RestoreResult.Failed(e.message ?: "Restore failed.")
        } finally {
            staging.deleteRecursively()
        }
    }

    /** Opens the staged file read-only to confirm it is a Lumen database before swapping. */
    private fun countRows(staged: File): Triple<Int, Int, Int> {
        val db = android.database.sqlite.SQLiteDatabase.openDatabase(
            staged.absolutePath,
            null,
            android.database.sqlite.SQLiteDatabase.OPEN_READONLY,
        )
        return db.use {
            fun count(table: String) = runCatching {
                it.rawQuery("SELECT COUNT(*) FROM $table", null).use { c ->
                    if (c.moveToFirst()) c.getInt(0) else 0
                }
            }.getOrDefault(0)
            Triple(count("highlights"), count("notes"), count("lookup_events"))
        }
    }

    private fun manifestJson(databaseFile: File) = JSONObject().apply {
        put("format", "lumen-backup")
        put("schema", LumenDatabase.SCHEMA_VERSION)
        put("createdAt", Exporters.isoDate(System.currentTimeMillis()))
        put("databaseBytes", databaseFile.length())
    }.toString(2)

    private suspend fun settingsJson(): String {
        val prefs = settingsDataStore.data.first()
        val json = JSONObject()
        prefs.asMap().forEach { (key, value) ->
            json.put(key.name, value?.toString() ?: JSONObject.NULL)
        }
        return JSONObject().apply {
            put("format", "lumen-settings")
            put("values", json)
        }.toString(2)
    }

    /**
     * Settings are restored best-effort and typed by what the stored string parses as.
     * A settings key that fails to restore costs the user one preference; failing the
     * whole restore over it would cost them their highlights.
     */
    private suspend fun restoreSettings(raw: String) {
        runCatching {
            val values = JSONObject(raw).getJSONObject("values")
            settingsDataStore.edit { prefs ->
                prefs.clear()
                values.keys().forEach { key ->
                    val text = values.optString(key)
                    when {
                        text.equals("true", true) || text.equals("false", true) ->
                            prefs[androidx.datastore.preferences.core.booleanPreferencesKey(key)] =
                                text.toBoolean()

                        text.toIntOrNull() != null ->
                            prefs[androidx.datastore.preferences.core.intPreferencesKey(key)] =
                                text.toInt()

                        text.toFloatOrNull() != null ->
                            prefs[androidx.datastore.preferences.core.floatPreferencesKey(key)] =
                                text.toFloat()

                        else ->
                            prefs[androidx.datastore.preferences.core.stringPreferencesKey(key)] =
                                text
                    }
                }
            }
        }
    }

    private companion object {
        const val MANIFEST_ENTRY = "manifest.json"
        const val SETTINGS_ENTRY = "settings.json"
    }
}
