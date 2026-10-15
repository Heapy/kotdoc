// Network.HTTP.Client.Core 0.7.19, Copyright (c) 2013 Michael Snoyman.
// MIT; licenses/http-client-MIT.txt. Upstream is retained under vendor/.
package io.heapy.kotdoc.http

data class HttpRedirectStep(
    val response: HttpResponse<HttpBodyReader>,
    val request: HttpRequest,
    val isRedirect: Boolean,
)

object HttpCore {
    /** Original httpRedirect callback API; the final response remains open. */
    fun httpRedirect(count: Long, perform: (HttpRequest) -> Pair<HttpResponse<HttpBodyReader>, HttpRequest?>,
        initial: HttpRequest,
    ): HttpResponse<HttpBodyReader> = httpRedirectWithRequest(count, { current ->
        val (response, next) = perform(current)
        HttpRedirectStep(response, next ?: initial, next != null)
    }, initial).second

    /** httpRedirect': includes the callback's final, potentially modified request. */
    fun httpRedirectWithRequest(count: Long, perform: (HttpRequest) -> HttpRedirectStep,
        initial: HttpRequest,
    ): Pair<HttpRequest, HttpResponse<HttpBodyReader>> {
        var remaining = count
        var current = initial
        val history = ArrayDeque<HttpResponse<List<HttpBytes>>>()
        while (true) {
            if (remaining < 0) throw HttpContentException(HttpFailureContent.TooManyRedirects(history.toList()))
            val (response, next, redirect) = perform(current)
            if (!redirect) return next to response
            val body = try {
                HttpBodies.readSome(response.body, 1024)
            } catch (e: Throwable) {
                handleClosedRead(e)
            }
            response.close()
            history.addFirst(HttpResponse(response.status, response.version, response.headers, body,
                response.close, response.originalRequest, response.early, response.cookieJar))
            current = next
            remaining--
        }
    }

    /** Suppress only the three original closed-read exception shapes. */
    fun handleClosedRead(exception: Throwable): List<HttpBytes> = when {
        exception is HttpContentException && exception.failure == HttpFailureContent.ConnectionClosed -> emptyList()
        exception is HttpRequestException && exception.failure == HttpFailureContent.ConnectionClosed -> emptyList()
        exception is HttpResourceVanishedException -> emptyList()
        else -> throw exception
    }
}
