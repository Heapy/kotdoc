// Network.HTTP.Client.Core/Client 0.7.19, Copyright (c) 2013 Michael Snoyman.
// MIT; licenses/http-client-MIT.txt. Complete original modules retained in vendor/.
package io.heapy.kotdoc.http

import io.heapy.kotdoc.resources.PandocUtcTime
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Effects corresponding to getCurrentTime and System.Timeout.timeout around getConn. */
interface HttpExecutionRuntime {
    fun now(): PandocUtcTime
    fun acquire(microseconds: Long?, action: () -> KeyedManaged<HttpConnection>): KeyedManaged<HttpConnection>?
    val responseTimeout: HttpTimeoutBoundary get() = HttpSystemTimeout
}

object HttpExecutionSystemRuntime : HttpExecutionRuntime {
    override fun now() = KeyedPoolSystemRuntime.now()
    override fun acquire(microseconds: Long?, action: () -> KeyedManaged<HttpConnection>): KeyedManaged<HttpConnection>? {
        if (microseconds == null || microseconds < 0) return action()
        if (microseconds == 0L) return null
        // A connector supplied by a caller can ignore interruption. Retain ownership
        // until the caller accepts the result, and discard a late acquisition.
        val lock = Any()
        var abandoned = false
        var acquired: KeyedManaged<HttpConnection>? = null
        fun abandon() {
            val late = synchronized(lock) { abandoned = true; acquired.also { acquired = null } }
            late?.release(HttpReuse.DontReuse)
        }
        val task = FutureTask {
            val connection = action()
            val late = synchronized(lock) { if (abandoned) true else { acquired = connection; false } }
            if (late) connection.release(HttpReuse.DontReuse)
            connection
        }
        Thread.ofVirtual().name("kotdoc-http-acquire").inheritInheritableThreadLocals(false).start(task)
        return try {
            val result = task.get(microseconds, TimeUnit.MICROSECONDS)
            synchronized(lock) { acquired = null }
            result
        } catch (_: TimeoutException) {
            abandon(); task.cancel(true); null
        } catch (e: InterruptedException) {
            abandon(); task.cancel(true); throw e
        } catch (e: ExecutionException) {
            throw e.cause!!
        }
    }
}

data class HttpHistoriedResponse<A>(
    val redirects: List<Pair<HttpRequest, HttpResponse<List<HttpBytes>>>>,
    val finalRequest: HttpRequest,
    val finalResponse: HttpResponse<A>,
) {
    fun show(showBody: (A) -> String, precedence: Int = 0): String {
        val history = redirects.joinToString(",", "[", "]") { (request, response) ->
            "(${request.show()},${HttpResponses.showResponse(response, HttpBodies::showLazy)})"
        }
        val result = "HistoriedResponse {hrRedirects = $history, hrFinalRequest = ${finalRequest.show()}, " +
            "hrFinalResponse = ${HttpResponses.showResponse(finalResponse, showBody)}}"
        return if (precedence > 10) "($result)" else result
    }
}

/** Original execution APIs with independently replaceable requestAction and clock effects. */
class HttpExecution(private val runtime: HttpExecutionRuntime = HttpExecutionSystemRuntime) {
    @Volatile var requestAction: (HttpRequest, HttpManager) -> HttpResponse<HttpBodyReader> = ::makeRequest

    fun getModifiedRequestManager(manager: HttpManager, request: HttpRequest): Pair<HttpManager, HttpRequest> {
        val selected = request.requestManagerOverride ?: manager
        return selected to selected.modifyRequest(request)
    }

    fun httpRawWithRequest(input: HttpRequest, manager: HttpManager): Pair<HttpRequest, HttpResponse<HttpBodyReader>> {
        val proxied = manager.setProxy(input)
        val (request, jar) = proxied.cookieJar?.let {
            val now = runtime.now()
            HttpCookies.insertCookiesIntoRequest(proxied, HttpCookies.evictExpiredCookies(it, now), now)
        } ?: (proxied to HttpCookieJar.empty)
        val response = requestAction(request, manager)
        return request to if (proxied.cookieJar == null) response else {
            // httpRaw' discards updateCookieJar's header-stripped response.
            val (updated, _) = HttpCookies.updateCookieJar(response, request, runtime.now(), jar)
            response.copy(cookieJar = updated)
        }
    }
    fun httpRaw(request: HttpRequest, manager: HttpManager) = httpRawWithRequest(request, manager).second

    fun makeRequest(request: HttpRequest, manager: HttpManager): HttpResponse<HttpBodyReader> {
        while (true) {
            val timeout = responseTimeout(request.responseTimeout, manager.responseTimeout)
            val before = if (timeout == null) null else runtime.now()
            val managed = runtime.acquire(timeout) { HttpManagers.getConnection(request, manager) }
                ?: throw HttpContentException(HttpFailureContent.ConnectionTimeout)
            val remaining = if (timeout == null) null else {
                val elapsed = runtime.now().posixPicoseconds() - before!!.posixPicoseconds()
                // NominalDiffTime is exact to picoseconds; round is ties-to-even,
                // followed by the source Int's signed 64-bit conversion.
                val micros = (BigDecimal.valueOf(timeout) - BigDecimal(elapsed, 6))
                    .setScale(0, RoundingMode.HALF_EVEN).toBigInteger().toLong()
                if (micros <= 0) {
                    managed.release(HttpReuse.DontReuse)
                    throw HttpContentException(HttpFailureContent.ConnectionTimeout)
                }
                micros
            }
            try {
                val continuation = HttpRequests.requestBuilder(HttpManagers.dropProxyAuthSecure(request), managed.resource.write)
                return HttpResponses.getResponse(manager.maxHeaderLength, manager.maxNumberHeaders, remaining,
                    request, HttpManagers.managedConnection(managed), continuation, runtime.responseTimeout)
            } catch (e: Throwable) {
                // Match guard evaluation before release, including a throwing predicate.
                val retry = managed.reused && manager.retryableException(e)
                managed.release(HttpReuse.DontReuse)
                if (!retry) throw e
            }
        }
    }

    fun responseOpen(input: HttpRequest, manager: HttpManager): HttpResponse<HttpBodyReader> {
        HttpHeaders.validate(input.requestHeaders)?.let {
            throw HttpContentException(HttpFailureContent.InvalidRequestHeader(it))
        }
        val (selected, initial) = getModifiedRequestManager(manager, input)
        return wrap(initial) {
            selected.wrapException.wrap(initial) {
                val (request, response) = HttpCore.httpRedirectWithRequest(initial.redirectCount, { current ->
                    val (active, modified) = getModifiedRequestManager(selected, current)
                    val (sent, result) = httpRawWithRequest(modified, active)
                    val next = if (modified.redirectCount == 0L) null else HttpResponses.getRedirectedRequest(
                        initial, sent, result.headers, result.cookieJar, result.status.code)
                    HttpRedirectStep(result, next ?: sent, next != null)
                }, initial)
                request.checkResponse(request, response)
                selected.modifyResponse(response.copy(body = HttpBodyReader { wrap(initial) { response.body.read() } }))
            }
        }
    }

    fun <A> withResponse(request: HttpRequest, manager: HttpManager, action: (HttpResponse<HttpBodyReader>) -> A): A {
        val response = responseOpen(request, manager)
        return try { action(response) } finally { response.close() }
    }
    fun httpLbs(request: HttpRequest, manager: HttpManager): HttpResponse<List<HttpBytes>> =
        withResponse(request, manager, HttpResponses::lbsResponse)
    fun httpNoBody(request: HttpRequest, manager: HttpManager): HttpResponse<Unit> = withResponse(request, manager) {
        HttpResponse(it.status, it.version, it.headers, Unit, it.close, it.originalRequest, it.early, it.cookieJar)
    }

    fun responseOpenHistory(input: HttpRequest, manager: HttpManager): HttpHistoriedResponse<HttpBodyReader> = wrap(input) {
        var finalRequest = input
        val history = mutableListOf<Pair<HttpRequest, HttpResponse<List<HttpBytes>>>>()
        val (_, finalResponse) = HttpCore.httpRedirectWithRequest(input.redirectCount, { current ->
            val (active, modified) = getModifiedRequestManager(manager, current)
            val (sent, raw) = httpRawWithRequest(modified, active)
            val response = raw.copy(body = HttpBodyReader { wrap(current) { raw.body.read() } })
            val next = HttpResponses.getRedirectedRequest(modified, sent, response.headers, response.cookieJar, response.status.code)
            if (next != null) {
                finalRequest = next
                val body = try { HttpBodies.readSome(response.body, 1024) }
                    catch (e: Throwable) { HttpCore.handleClosedRead(e) }
                history.add(modified to HttpResponse(response.status, response.version, response.headers, body,
                    response.close, response.originalRequest, response.early, response.cookieJar))
            }
            HttpRedirectStep(response, next ?: sent, next != null)
        }, input)
        HttpHistoriedResponse(history.toList(), finalRequest, finalResponse)
    }

    fun <A> withResponseHistory(request: HttpRequest, manager: HttpManager, action: (HttpHistoriedResponse<HttpBodyReader>) -> A): A {
        val history = responseOpenHistory(request, manager)
        return try { action(history) } finally { history.finalResponse.close() }
    }

    fun <A> withConnection(request: HttpRequest, manager: HttpManager, action: (HttpConnection) -> A): A {
        val managed = HttpManagers.getConnection(manager.setProxy(request), manager)
        // In the source, finally binds to keepAlive (more tightly than <*).
        // A failing action leaves release to Managed's finalizer.
        val result = action(managed.resource)
        try { managed.keepAlive() } finally { managed.release(HttpReuse.DontReuse) }
        return result
    }

    private fun <A> wrap(request: HttpRequest, action: () -> A): A = try { action() }
        catch (e: HttpContentException) { throw HttpRequestException(request, e.failure) }

    companion object {
        val system = HttpExecution()
        fun responseTimeout(request: HttpResponseTimeout, manager: HttpResponseTimeout): Long? =
            when (val selected = if (request == HttpResponseTimeout.Default) manager else request) {
                HttpResponseTimeout.Default -> 30_000_000
                HttpResponseTimeout.None -> null
                is HttpResponseTimeout.Micro -> selected.microseconds
            }
    }
}
