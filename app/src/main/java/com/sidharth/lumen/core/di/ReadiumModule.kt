package com.sidharth.lumen.core.di

import android.content.Context
import com.sidharth.lumen.core.readium.NoNetworkHttpClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.readium.adapter.pdfium.document.PdfiumDocumentFactory
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.HttpClient
import org.readium.r2.shared.util.pdf.PdfDocumentFactory
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.CompositePublicationParser
import org.readium.r2.streamer.parser.epub.EpubParser
import org.readium.r2.streamer.parser.pdf.PdfParser
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ReadiumModule {

    @Provides
    @Singleton
    fun provideHttpClient(client: NoNetworkHttpClient): HttpClient = client

    @Provides
    @Singleton
    fun provideAssetRetriever(
        @ApplicationContext context: Context,
        httpClient: HttpClient,
    ): AssetRetriever = AssetRetriever(context.contentResolver, httpClient)

    /**
     * The PDFium engine, shared by the parser here and by the reader's navigator so a PDF
     * is not decoded twice.
     */
    @Provides
    @Singleton
    fun providePdfDocumentFactory(
        @ApplicationContext context: Context,
    ): PdfDocumentFactory<*> = PdfiumDocumentFactory(context)

    /**
     * EPUB and PDF only — still not `DefaultPublicationParser`, which would also wire the
     * audiobook and webpub parsers for formats this app cannot read. A composite of
     * exactly the two supported formats keeps the "not built yet" boundary honest: an
     * audiobook still fails to import rather than half-opening.
     */
    @Provides
    @Singleton
    fun providePublicationOpener(
        @ApplicationContext context: Context,
        httpClient: HttpClient,
        pdfDocumentFactory: PdfDocumentFactory<*>,
    ): PublicationOpener = PublicationOpener(
        CompositePublicationParser(
            EpubParser(httpClient),
            PdfParser(context, pdfDocumentFactory),
        ),
    )
}
