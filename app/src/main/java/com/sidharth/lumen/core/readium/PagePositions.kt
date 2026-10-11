package com.sidharth.lumen.core.readium

import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.positionsServiceFactory
import org.readium.r2.streamer.parser.epub.EpubPositionsService

/**
 * How much of a resource counts as one page (M14).
 *
 * Readium's own recommendation measures the *compressed* size of each file, which makes a
 * page count a fact about how the EPUB was zipped rather than about the book: the same
 * text packed tighter reports fewer pages, and a repetitive text collapses to almost
 * none — a four-chapter test book came out as four pages. Measuring the original length
 * instead makes the number a property of the book, identical on every device and after
 * every re-download.
 *
 * 2 KB of markup landed closest to the printed editions of the books measured: The
 * Martian comes out at 358 against a print 369. It is still an estimate — EPUB carries no
 * page numbers of its own unless the publisher ships a page map, and most do not — but it
 * is consistent and monotonic, which is what "page 120 of 354" has to be to mean anything.
 */
const val PAGE_LENGTH_BYTES = 2048

/**
 * Installs Lumen's page-length strategy on a publication being opened.
 *
 * Applied at [org.readium.r2.streamer.PublicationOpener.open] rather than through the
 * opener's constructor: in Readium 3.3 the constructor's `onCreatePublication` is not
 * invoked on open — verified on device, where the page count stayed on the default
 * compressed-length strategy until this moved to the call site.
 */
fun Publication.Builder.useLumenPagePositions() {
    servicesBuilder.positionsServiceFactory = EpubPositionsService.createFactory(
        EpubPositionsService.ReflowableStrategy.OriginalLength(pageLength = PAGE_LENGTH_BYTES),
    )
}
