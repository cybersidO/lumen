package com.sidharth.lumen.core.books

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import com.sidharth.lumen.core.database.BookDao
import com.sidharth.lumen.core.database.BookEntity
import com.sidharth.lumen.core.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ImportProgress {
    /** [totalBytes] is null when the content provider won't disclose a size. */
    data class Copying(val copiedBytes: Long, val totalBytes: Long?) : ImportProgress
    data object Parsing : ImportProgress
}

sealed interface ImportResult {
    data class Imported(val bookId: Long, val title: String) : ImportResult

    /** The same bytes are already in the library; the existing book is returned. */
    data class AlreadyPresent(val bookId: Long, val title: String) : ImportResult

    data class Failed(val message: String) : ImportResult
}

/**
 * Copy first, identify second.
 *
 * The file is streamed into private storage and hashed in the same pass, before anything
 * tries to parse it — so a book can never be half-imported because the user moved the
 * original, and a 40MB EPUB never has to be held in memory (§5).
 */
@Singleton
class BookImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storage: BookStorage,
    private val metadataReader: EpubMetadataReader,
    private val bookDao: BookDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun import(
        uri: Uri,
        onProgress: (ImportProgress) -> Unit = {},
    ): ImportResult = withContext(ioDispatcher) {
        val displayName = queryDisplayName(uri)
        val declaredSize = queryDeclaredSize(uri)
        val staging = storage.newStagingFile()

        try {
            val contentHash = copyToStaging(uri, staging, declaredSize, onProgress)
                ?: return@withContext ImportResult.Failed("Couldn't read that file.")

            currentCoroutineContext().ensureActive()

            bookDao.findByContentHash(contentHash)?.let { existing ->
                return@withContext ImportResult.AlreadyPresent(existing.id, existing.title)
            }

            onProgress(ImportProgress.Parsing)

            // Parsed from the staging copy: if it turns out not to be a readable EPUB,
            // nothing has been written to the library directory to clean up.
            val metadata = metadataReader.read(staging).getOrElse { error ->
                return@withContext ImportResult.Failed(
                    error.message ?: "That file couldn't be read as an EPUB.",
                )
            }

            val format = BookFormat.EPUB
            val destination = storage.bookFile(contentHash, format)
            if (!staging.renameTo(destination)) {
                staging.copyTo(destination, overwrite = true)
            }

            val coverPath = metadata.cover?.let { writeCover(contentHash, it) }
            val title = metadata.title.ifBlank { displayName ?: "Untitled" }

            val id = bookDao.insert(
                BookEntity(
                    contentHash = contentHash,
                    title = title,
                    author = metadata.author,
                    format = format.name,
                    filePath = destination.absolutePath,
                    coverPath = coverPath,
                    addedAt = System.currentTimeMillis(),
                    lastOpenedAt = null,
                ),
            )
            ImportResult.Imported(id, title)
        } catch (e: ImportException) {
            ImportResult.Failed(e.message ?: "Import failed.")
        } catch (e: Exception) {
            ImportResult.Failed("Import failed: ${e.message ?: e::class.simpleName}")
        } finally {
            staging.delete()
        }
    }

    private fun copyToStaging(
        uri: Uri,
        staging: File,
        declaredSize: Long?,
        onProgress: (ImportProgress) -> Unit,
    ): String? {
        val stream = runCatching { context.contentResolver.openInputStream(uri) }
            .getOrNull() ?: return null

        return stream.use { input ->
            var lastReported = 0L
            ContentHasher.copyAndHash(input, staging) { copied ->
                // Throttled: a 40MB file would otherwise emit ~640 updates that the UI
                // cannot use and that would swamp the main thread.
                if (copied - lastReported >= PROGRESS_STEP_BYTES) {
                    lastReported = copied
                    onProgress(ImportProgress.Copying(copied, declaredSize))
                }
            }
        }
    }

    private fun writeCover(contentHash: String, cover: Bitmap): String? {
        val file = storage.coverFile(contentHash)
        return runCatching {
            file.outputStream().buffered().use { out ->
                cover.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            file.absolutePath
        }.getOrElse {
            // A missing cover is a cosmetic loss — the grid falls back to a generated
            // card — so it must not fail the whole import.
            file.delete()
            null
        }
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) {
                    cursor.getString(0).substringBeforeLast('.')
                } else {
                    null
                }
            }
    }.getOrNull()

    private fun queryDeclaredSize(uri: Uri): Long? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
            }
    }.getOrNull()

    private companion object {
        const val PROGRESS_STEP_BYTES = 512 * 1024L
    }
}
