package com.sidharth.lumen.core.books

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The whole-device walk (1.4.0), against a temp tree rather than real storage — so the
 * rules that decide what the reader is shown are pinned down: which files count, which
 * folders are skipped, and that nothing is listed twice.
 */
@RunWith(AndroidJUnit4::class)
class DeviceBookSearchTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var root: File
    private lateinit var search: DeviceBookSearch

    private fun file(path: String, bytes: Int = 16): File =
        File(root, path).apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(bytes))
        }

    @Before
    fun setUp() {
        root = File(context.cacheDir, "search-test-${System.nanoTime()}").apply { mkdirs() }
        search = DeviceBookSearch(context, Dispatchers.IO)
    }

    @After
    fun tearDown() { root.deleteRecursively() }

    @Test
    fun findsEpubsAtAnyDepthAndIgnoresEverythingElse() = runTest {
        file("top.epub")
        file("Books/novel.epub")
        file("Books/Classics/deep/old.epub")
        file("Books/notes.txt")
        file("Books/scan.pdf")

        val found = search.searchRoots(listOf(root))
        assertEquals(
            listOf("deep/old.epub", "novel.epub", "top.epub").sorted(),
            found.map { it.displayName }.sorted().map { it },
        )
        assertEquals(3, found.size)
    }

    @Test
    fun matchingIsCaseInsensitiveOnTheExtension() = runTest {
        file("Shouty.EPUB")
        assertEquals(1, search.searchRoots(listOf(root)).size)
    }

    @Test
    fun hiddenFoldersAndMediaFoldersAreSkipped() = runTest {
        file(".thumbnails/hidden.epub")
        file("DCIM/camera.epub")
        file("Android/data/com.other/files/sandboxed.epub")
        file("Books/real.epub")

        val found = search.searchRoots(listOf(root))
        assertEquals(listOf("real.epub"), found.map { it.displayName })
    }

    @Test
    fun theRelativePathDistinguishesTwoBooksWithTheSameName() = runTest {
        file("Books/A/same.epub")
        file("Books/B/same.epub")
        val found = search.searchRoots(listOf(root))
        assertEquals(2, found.size)
        assertTrue(found.all { it.relativePath.isNotEmpty() })
        assertEquals(setOf("Books/A", "Books/B"), found.map { it.relativePath }.toSet())
    }

    @Test
    fun overlappingRootsDoNotProduceDuplicates() = runTest {
        file("Books/one.epub")
        val found = search.searchRoots(listOf(root, File(root, "Books")))
        assertEquals(1, found.size)
    }

    @Test
    fun progressCountsFoldersVisited() = runTest {
        file("Books/Classics/one.epub")
        var seen = 0
        search.searchRoots(listOf(root)) { seen = it }
        // root + Books + Classics
        assertTrue("expected at least 3 folders, saw $seen", seen >= 3)
    }

    @Test
    fun anEmptyTreeFindsNothingAndDoesNotThrow() = runTest {
        assertEquals(emptyList<FoundBook>(), search.searchRoots(listOf(root)))
    }
}
