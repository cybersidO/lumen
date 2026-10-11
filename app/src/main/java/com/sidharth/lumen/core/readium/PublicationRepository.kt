package com.sidharth.lumen.core.readium

import com.sidharth.lumen.core.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.streamer.PublicationOpener
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the single [Publication] currently being read.
 *
 * Opening an EPUB is expensive and its resources must be closed, so exactly one is kept
 * open at a time and the previous one is closed when a different book is opened. The
 * navigator needs the *same* instance the reader screen is driving, which is why this is a
 * singleton rather than something the ViewModel owns — a ViewModel is recreated on
 * configuration change, and reopening the file there would drop the reader's position.
 */
@Singleton
class PublicationRepository @Inject constructor(
    private val assetRetriever: AssetRetriever,
    private val publicationOpener: PublicationOpener,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val mutex = Mutex()

    private var openBookId: Long? = null
    private var openPublication: Publication? = null

    /** Non-suspending peek, for code that can only proceed if the book is already open. */
    fun peek(bookId: Long): Publication? =
        openPublication.takeIf { openBookId == bookId }

    suspend fun open(bookId: Long, file: File): Result<Publication> = mutex.withLock {
        peek(bookId)?.let { return Result.success(it) }

        withContext(ioDispatcher) {
            if (!file.exists()) {
                return@withContext Result.failure(
                    ReaderException("This book's file is missing. Try importing it again."),
                )
            }

            val asset = assetRetriever.retrieve(file).getOrNull()
                ?: return@withContext Result.failure(
                    ReaderException("Lumen couldn't read this file as an EPUB."),
                )

            val publication = publicationOpener.open(
                asset = asset,
                allowUserInteraction = false,
                // Lumen counts pages by the book's own length, not by how well it zipped.
                onCreatePublication = { useLumenPagePositions() },
            )
                .getOrNull()
                ?: run {
                    asset.close()
                    return@withContext Result.failure(
                        ReaderException("The EPUB couldn't be opened — it may be corrupt or DRM-protected."),
                    )
                }

            closeOpenPublication()
            openBookId = bookId
            openPublication = publication
            Result.success(publication)
        }
    }

    suspend fun close(bookId: Long) = mutex.withLock {
        if (openBookId == bookId) closeOpenPublication()
    }

    private fun closeOpenPublication() {
        openPublication?.close()
        openPublication = null
        openBookId = null
    }
}

class ReaderException(message: String) : Exception(message)
