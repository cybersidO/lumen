package com.sidharth.lumen.core.readium

import org.readium.r2.shared.util.DebugError
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.http.HttpClient
import org.readium.r2.shared.util.http.HttpError
import org.readium.r2.shared.util.http.HttpRequest
import org.readium.r2.shared.util.http.HttpStreamResponse
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Readium's parsers take an [HttpClient] because they can also open publications served
 * over HTTP. Lumen only ever opens files it has already copied into private storage, and
 * the app declares no INTERNET permission at all.
 *
 * Supplying Readium's `DefaultHttpClient` would compile and then fail at runtime with a
 * confusing security exception. This fails immediately and says why — and if this error
 * ever surfaces in a log, it means something tried to reach the network, which is a bug
 * worth seeing rather than hiding.
 */
@Singleton
class NoNetworkHttpClient @Inject constructor() : HttpClient {

    override suspend fun stream(
        request: HttpRequest,
    ): Try<HttpStreamResponse, HttpError> = Try.failure(
        HttpError.Unreachable(
            DebugError("Lumen makes no network requests; refused ${request.url}"),
        ),
    )
}
