package com.sidharth.lumen.core.di

import android.content.Context
import com.sidharth.lumen.core.readium.NoNetworkHttpClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.HttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.epub.EpubParser
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
     * [EpubParser] rather than `DefaultPublicationParser`: the default composite also
     * wires audiobook, webpub and PDF parsers, and the PDF one needs a
     * `PdfDocumentFactory` that does not exist until M8. Importing only what can actually
     * be opened keeps the "not built yet" boundary honest.
     */
    @Provides
    @Singleton
    fun providePublicationOpener(httpClient: HttpClient): PublicationOpener =
        PublicationOpener(EpubParser(httpClient))
}
