package com.sidharth.lumen.core.books

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.sidharth.lumen.core.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** An EPUB found by [FolderScanner], not yet imported. */
data class FoundBook(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long?,
    /** Path under the chosen folder, for telling two same-named files apart. */
    val relativePath: String,
)

/**
 * Finds every `.epub` under a folder the user picked (M10).
 *
 * This is the permission-free version of "scan my phone": the Storage Access Framework
 * grants access to one tree at a time, so the walk is bounded by what was chosen and the
 * manifest stays at zero permissions. Walking is done with [DocumentsContract] child
 * queries rather than `DocumentFile`, which does one round-trip per file and is slow on
 * big folders.
 *
 * Matching is by extension *or* MIME, because providers disagree about which they set.
 */
@Singleton
class FolderScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun scan(treeUri: Uri, onProgress: (scannedFolders: Int) -> Unit = {}): List<FoundBook> =
        withContext(ioDispatcher) {
            val found = mutableListOf<FoundBook>()
            val rootId = DocumentsContract.getTreeDocumentId(treeUri)
            var folders = 0
            walk(treeUri, rootId, "", found, maxDepth = MAX_DEPTH) {
                folders++
                onProgress(folders)
            }
            found.sortedWith(compareBy({ it.relativePath }, { it.displayName }))
        }

    private suspend fun walk(
        treeUri: Uri,
        documentId: String,
        pathPrefix: String,
        into: MutableList<FoundBook>,
        maxDepth: Int,
        onFolder: () -> Unit,
    ) {
        if (maxDepth < 0) return
        currentCoroutineContext().ensureActive()
        onFolder()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        val cursor = runCatching {
            context.contentResolver.query(childrenUri, projection, null, null, null)
        }.getOrNull() ?: return

        val subfolders = mutableListOf<Pair<String, String>>()
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getString(0)
                val name = it.getString(1) ?: continue
                val mime = it.getString(2)
                val size = if (it.isNull(3)) null else it.getLong(3)
                when {
                    mime == DocumentsContract.Document.MIME_TYPE_DIR ->
                        subfolders += id to name
                    isEpub(name, mime) -> into += FoundBook(
                        uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
                        displayName = name,
                        sizeBytes = size,
                        relativePath = pathPrefix,
                    )
                }
            }
        }
        // Depth-first after the cursor is closed, so only one cursor is open at a time.
        for ((id, name) in subfolders) {
            walk(treeUri, id, "$pathPrefix$name/", into, maxDepth - 1, onFolder)
        }
    }

    private fun isEpub(name: String, mime: String?): Boolean =
        name.endsWith(".epub", ignoreCase = true) || mime == EPUB_MIME

    companion object {
        const val EPUB_MIME = "application/epub+zip"

        /** Deep enough for any sane library layout; a guard against symlink-like loops. */
        const val MAX_DEPTH = 12
    }
}
