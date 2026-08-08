package com.sidharth.lumen.core.dictionary

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * §M5's done-when, as a test rather than a demo: *"looking up 'running', 'mice', and
 * 'better' all resolve, offline"*.
 *
 * This runs against the **real bundled asset** — the same 20MB WordNet database that ships
 * in the APK, installed through the same copy-out-of-assets path the app uses. It makes no
 * network call because there is no network code to make one, and the app declares no
 * INTERNET permission at all, so a regression here cannot be masked by a silent fallback
 * to some online service.
 */
@RunWith(AndroidJUnit4::class)
class WordNetDictionaryTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun dictionary(): WordNetDictionary {
        // Start from a clean slate so the asset-install path is exercised, not just a
        // database left behind by an earlier run.
        File(context.filesDir, "dictionary").deleteRecursively()
        return WordNetDictionary(context, Dispatchers.IO)
    }

    private suspend fun headwords(word: String) =
        dictionary().lookup(word).map { it.headword }

    // --- the three acceptance words --------------------------------------------------

    @Test
    fun runningResolvesToRun() = runTest {
        val entries = dictionary().lookup("running")
        val heads = entries.map { it.headword }
        assertTrue("expected 'run' among $heads", "run" in heads)
        // It is also a headword itself (adjective and noun), and that comes first.
        assertEquals("running", heads.first())

        val run = entries.first { it.headword == "run" }
        assertTrue("'run' should have senses", run.senses.isNotEmpty())
        assertEquals("running", run.resolvedFrom)
    }

    @Test
    fun miceResolvesToMouse() = runTest {
        val entries = dictionary().lookup("mice")
        assertEquals(listOf("mouse"), entries.map { it.headword })

        val mouse = entries.single()
        assertEquals("mice", mouse.resolvedFrom)
        assertTrue(
            "expected a rodent sense, got ${mouse.senses.map { it.definition }}",
            mouse.senses.any { it.definition.contains("rodent", ignoreCase = true) },
        )
    }

    @Test
    fun betterResolvesToGoodAndWell() = runTest {
        val heads = headwords("better")
        assertTrue("expected 'good' among $heads", "good" in heads)
        assertTrue("expected 'well' among $heads", "well" in heads)
    }

    // --- the rest of the sheet's contract --------------------------------------------

    @Test
    fun entriesCarryPronunciationWhenTheSourceHasOne() = runTest {
        val mouse = dictionary().lookup("mice").single()
        assertNotNull("CMUdict has a pronunciation for 'mouse'", mouse.pronunciation)
        assertTrue(mouse.pronunciation!!.isNotBlank())
    }

    @Test
    fun sensesAreGroupedByPartOfSpeechAndNumbered() = runTest {
        val run = dictionary().lookup("run").first { it.headword == "run" }
        assertTrue("expected both noun and verb senses", run.senses.map { it.pos }.toSet().size > 1)
        run.senses.groupBy { it.pos }.forEach { (_, senses) ->
            assertEquals(senses.map { it.senseNumber }.sorted(), senses.map { it.senseNumber })
        }
    }

    @Test
    fun punctuationAttachedByATapIsIgnored() = runTest {
        assertEquals(listOf("mouse"), headwords("“mice,”"))
        assertEquals(listOf("mouse"), headwords("mice."))
    }

    /** An unknown word must come back empty so the sheet offers the web escape hatch. */
    @Test
    fun anUnknownWordResolvesToNothing() = runTest {
        assertEquals(emptyList<String>(), headwords("zzzblargwibble"))
    }

    @Test
    fun theDatabaseIsInstalledFromAssetsOnFirstUse() = runTest {
        val installed = File(context.filesDir, "dictionary/wordnet.db")
        File(context.filesDir, "dictionary").deleteRecursively()
        assertTrue("precondition: not installed", !installed.exists())

        WordNetDictionary(context, Dispatchers.IO).lookup("mouse")

        assertTrue("database should have been copied out of assets", installed.exists())
        assertTrue("database should be non-trivial", installed.length() > 1_000_000)
    }
}
