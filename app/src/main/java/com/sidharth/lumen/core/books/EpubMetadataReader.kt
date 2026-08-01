package com.sidharth.lumen.core.books

import android.graphics.Bitmap
import android.util.Size
import org.readium.r2.shared.publication.services.coverFitting
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.streamer.PublicationOpener
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** What the library grid needs to show a book, and nothing more. */
data class BookMetadata(
    val title: String,
    val author: String?,
    val cover: Bitmap?,
)

/**
 * Reads title / author / cover out of an EPUB using the same Readium stack that will
 * render it in M2 — so a book that imports cleanly is a book that opens cleanly, and
 * there is no second, divergent EPUB parser to keep correct.
 */
@Singleton
class EpubMetadataReader @Inject constructor(
    private val assetRetriever: AssetRetriever,
    private val publicationOpener: PublicationOpener,
) {

    suspend fun read(file: File): Result<BookMetadata> {
        val asset = assetRetriever.retrieve(file)
            .getOrNull()
            ?: return Result.failure(
                ImportException("This file isn't something Lumen can read as an EPUB."),
            )

        val publication = publicationOpener.open(asset, allowUserInteraction = false)
            .getOrNull()
            ?: run {
                asset.close()
                return Result.failure(
                    ImportException("The EPUB couldn't be opened — it may be corrupt or DRM-protected."),
                )
            }

        return try {
            val metadata = publication.metadata
            Result.success(
                BookMetadata(
                    // An EPUB with no dc:title is legal but useless in a grid; the caller
                    // substitutes the filename rather than showing an empty card.
                    title = metadata.title?.takeIf { it.isNotBlank() }.orEmpty(),
                    author = metadata.authors
                        .mapNotNull { it.name.takeIf(String::isNotBlank) }
                        .takeIf { it.isNotEmpty() }
                        ?.joinToString(", "),
                    cover = publication.coverFitting(COVER_SIZE),
                ),
            )
        } catch (e: Exception) {
            Result.failure(ImportException("Failed while reading the EPUB's metadata.", e))
        } finally {
            publication.close()
        }
    }

    private companion object {
        /** Comfortably larger than a grid cell on a 1080p phone, small enough to cache. */
        val COVER_SIZE = Size(600, 900)
    }
}

class ImportException(message: String, cause: Throwable? = null) : Exception(message, cause)
