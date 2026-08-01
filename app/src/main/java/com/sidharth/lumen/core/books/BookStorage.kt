package com.sidharth.lumen.core.books

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the on-disk layout. Everything lives under app-private `filesDir`, so nothing is
 * readable by other apps and nothing survives an uninstall.
 *
 *   files/books/<content-hash>.epub
 *   files/covers/<content-hash>.png
 *   files/import-staging/<random>.tmp
 *
 * Files are named by content hash, which makes the copy idempotent: importing the same
 * book twice writes the same path rather than accumulating duplicates.
 */
@Singleton
class BookStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val booksDir: File get() = context.filesDir.resolve("books").apply { mkdirs() }
    val coversDir: File get() = context.filesDir.resolve("covers").apply { mkdirs() }
    private val stagingDir: File get() = context.filesDir.resolve("import-staging").apply { mkdirs() }

    fun bookFile(contentHash: String, format: BookFormat): File =
        booksDir.resolve("$contentHash.${format.extension}")

    fun coverFile(contentHash: String): File = coversDir.resolve("$contentHash.png")

    /** Staging file for a copy whose hash — and therefore final name — isn't known yet. */
    fun newStagingFile(): File =
        stagingDir.resolve("${System.nanoTime()}.tmp")

    /** Called on startup: a staging file left behind means a previous import was killed. */
    fun clearStaging() {
        stagingDir.listFiles()?.forEach { it.delete() }
    }

    fun deleteFilesFor(contentHash: String) {
        BookFormat.entries.forEach { bookFile(contentHash, it).delete() }
        coverFile(contentHash).delete()
    }
}
