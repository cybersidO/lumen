package com.sidharth.lumen.core.books

import android.graphics.Bitmap
import android.util.Size
import org.readium.r2.shared.publication.Publication
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
    /**
     * What Readium actually decided the file *is*, not what its extension claimed. A PDF
     * saved as `.epub` imports correctly and opens with the right reader, because this is
     * the value the library row is written from.
     */
    val format: BookFormat,
)

/**
 * Reads title / author / cover out of a book using the same Readium stack that will
 * render it — so a book that imports cleanly is a book that opens cleanly, and there is no
 * second, divergent parser to keep correct.
 *
 * Handles both supported formats. The opener is a composite of exactly the EPUB and PDF
 * parsers (see `ReadiumModule`), so anything else — an audiobook, a stray zip — still
 * fails here rather than half-importing.
 */
@Singleton
class BookMetadataReader @Inject constructor(
    private val assetRetriever: AssetRetriever,
    private val publicationOpener: PublicationOpener,
) {

    suspend fun read(file: File): Result<BookMetadata> {
        val asset = assetRetriever.retrieve(file)
            .getOrNull()
            ?: return Result.failure(
                ImportException("This file isn't something Lumen can read. It supports EPUB and PDF."),
            )

        val publication = publicationOpener.open(asset, allowUserInteraction = false)
            .getOrNull()
            ?: run {
                asset.close()
                return Result.failure(
                    ImportException("That book couldn't be opened — it may be corrupt or DRM-protected."),
                )
            }

        return try {
            val metadata = publication.metadata
            val format =
                if (publication.conformsTo(Publication.Profile.PDF)) BookFormat.PDF
                else BookFormat.EPUB

            Result.success(
                BookMetadata(
                    // A book with no title is legal but useless in a grid; the caller
                    // substitutes the filename rather than showing an empty card.
                    title = metadata.title?.takeIf { it.isNotBlank() }.orEmpty(),
                    author = metadata.authors
                        .mapNotNull { it.name.takeIf(String::isNotBlank) }
                        .takeIf { it.isNotEmpty() }
                        ?.joinToString(", "),
                    // For a PDF this renders page one. A PDF that fails to produce a cover
                    // is not a failed import — the grid falls back to a generated card.
                    cover = publication.coverFitting(COVER_SIZE),
                    format = format,
                ),
            )
        } catch (e: Exception) {
            Result.failure(ImportException("Failed while reading that book's metadata.", e))
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
