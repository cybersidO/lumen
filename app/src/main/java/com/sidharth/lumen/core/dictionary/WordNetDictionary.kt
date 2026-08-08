package com.sidharth.lumen.core.dictionary

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.sidharth.lumen.core.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The bundled offline dictionary: WordNet 3.1 for senses, CMUdict for pronunciation,
 * built into a single SQLite file by `tools/build_dictionary.py`.
 *
 * Offline is the whole point (§2.4) — there is no network path here at all, and the app
 * declares no INTERNET permission. The database ships as an asset and is copied into
 * private storage on first use, because an asset inside the APK is compressed and cannot
 * be opened by SQLite in place.
 */
@Singleton
class WordNetDictionary @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : Dictionary {

    override val id = "wordnet-3.1"
    override val displayName = "WordNet"
    override val attribution =
        "WordNet 3.1 © Princeton University · pronunciations from CMUdict"

    private val installLock = Mutex()
    @Volatile private var database: SQLiteDatabase? = null

    private val morphy = Morphy(
        isKnownLemma = ::hasLemma,
        exceptionsFor = ::exceptionsFor,
    )

    override suspend fun lookup(surfaceForm: String): List<DictionaryEntry> =
        withContext(ioDispatcher) {
            val db = open() ?: return@withContext emptyList()
            val candidates = morphy.candidates(surfaceForm)
            if (candidates.isEmpty()) return@withContext emptyList()

            val normalisedSurface = surfaceForm.trim().lowercase()

            // Group by headword so a word that resolves to the same lemma under several
            // parts of speech shows as one entry with all its senses.
            candidates
                .groupBy { it.lemma }
                .mapNotNull { (lemma, group) ->
                    val senses = sensesFor(db, lemma)
                    if (senses.isEmpty()) {
                        null
                    } else {
                        DictionaryEntry(
                            headword = lemma,
                            pronunciation = pronunciationFor(db, lemma),
                            senses = senses,
                            resolvedFrom = lemma.takeIf { it != normalisedSurface }
                                ?.let { normalisedSurface },
                        )
                    }
                }
        }

    // --- queries ---------------------------------------------------------------------

    private fun sensesFor(db: SQLiteDatabase, lemma: String): List<Sense> =
        db.rawQuery(
            """
            SELECT s.pos, s.sense_num, y.definition, y.examples
            FROM senses s
            JOIN synsets y ON y.id = s.synset_id
            WHERE s.lemma = ?
            ORDER BY s.pos, s.sense_num
            """.trimIndent(),
            arrayOf(lemma),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val pos = WordPos.fromCode(cursor.getString(0)) ?: continue
                    add(
                        Sense(
                            pos = pos,
                            senseNumber = cursor.getInt(1),
                            definition = cursor.getString(2),
                            examples = cursor.getString(3)
                                ?.split('|')
                                ?.filter { it.isNotBlank() }
                                .orEmpty(),
                        ),
                    )
                }
            }
        }

    private fun pronunciationFor(db: SQLiteDatabase, lemma: String): String? =
        db.rawQuery("SELECT ipa FROM pronunciations WHERE lemma = ?", arrayOf(lemma))
            .use { if (it.moveToFirst()) it.getString(0) else null }

    private fun hasLemma(lemma: String, pos: WordPos): Boolean {
        val db = database ?: return false
        return db.rawQuery(
            "SELECT 1 FROM senses WHERE lemma = ? AND pos = ? LIMIT 1",
            arrayOf(lemma, pos.code),
        ).use { it.moveToFirst() }
    }

    private fun exceptionsFor(surface: String): List<Pair<String, WordPos>> {
        val db = database ?: return emptyList()
        return db.rawQuery(
            "SELECT base, pos FROM exceptions WHERE inflected = ?",
            arrayOf(surface),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    WordPos.fromCode(cursor.getString(1))?.let { add(cursor.getString(0) to it) }
                }
            }
        }
    }

    // --- installation ----------------------------------------------------------------

    /** Opens the database, installing it from assets the first time. */
    private suspend fun open(): SQLiteDatabase? {
        database?.let { return it }
        return installLock.withLock {
            database?.let { return it }
            val file = installIfNeeded() ?: return null
            runCatching {
                SQLiteDatabase.openDatabase(
                    file.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READONLY,
                )
            }.getOrNull()?.also { database = it }
        }
    }

    private fun installIfNeeded(): File? {
        val target = File(context.filesDir, "dictionary/$DATABASE_FILE")
        if (target.exists() && target.length() > 0) return target

        target.parentFile?.mkdirs()
        // Written to a temp name and renamed, so an interrupted copy can never leave a
        // truncated database that would then be treated as installed.
        val staging = File(target.parentFile, "$DATABASE_FILE.tmp")
        return runCatching {
            context.assets.open("dictionary/$DATABASE_FILE").use { input ->
                staging.outputStream().buffered().use(input::copyTo)
            }
            check(staging.renameTo(target)) { "could not move the dictionary into place" }
            target
        }.getOrElse {
            staging.delete()
            null
        }
    }

    private companion object {
        const val DATABASE_FILE = "wordnet.db"
    }
}
