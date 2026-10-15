// Network.HTTP.Client.Connection/Headers/Body/Response 0.7.19.
// Copyright (c) 2013 Michael Snoyman, MIT; licenses/http-client-MIT.txt.
package io.heapy.kotdoc.http

import java.io.ByteArrayOutputStream
import java.lang.ref.Cleaner
import java.lang.ref.Reference
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars as Chars
import io.heapy.kotdoc.uri.NetworkURI

sealed interface HttpFailureContent {
    data object IncompleteHeaders : HttpFailureContent
    data object OverlongHeaders : HttpFailureContent
    data object TooManyHeaderFields : HttpFailureContent
    data object ResponseTimeout : HttpFailureContent
    data object ConnectionClosed : HttpFailureContent
    data object NoResponseDataReceived : HttpFailureContent
    data object InvalidChunkHeaders : HttpFailureContent
    class TooManyRedirects(responses: List<HttpResponse<List<HttpBytes>>>) : HttpFailureContent {
        val responses: List<HttpResponse<List<HttpBytes>>> = java.util.Collections.unmodifiableList(responses.toList())
    }
    data class InvalidStatusLine(val bytes: HttpBytes) : HttpFailureContent
    data class ResponseBodyTooShort(val expected: ULong, val actual: ULong) : HttpFailureContent
    data class Zlib(val code: Int) : HttpFailureContent
    fun show(): String = when (this) {
        IncompleteHeaders -> "IncompleteHeaders"
        OverlongHeaders -> "OverlongHeaders"
        TooManyHeaderFields -> "TooManyHeaderFields"
        ResponseTimeout -> "ResponseTimeout"
        ConnectionClosed -> "ConnectionClosed"
        NoResponseDataReceived -> "NoResponseDataReceived"
        InvalidChunkHeaders -> "InvalidChunkHeaders"
        is TooManyRedirects -> "TooManyRedirects " + responses.joinToString(",", "[", "]") {
            HttpResponses.showResponse(it, HttpBodies::showLazy)
        }
        is InvalidStatusLine -> "InvalidStatusLine ${bytes.show()}"
        is ResponseBodyTooShort -> "ResponseBodyTooShort $expected $actual"
        is Zlib -> "HttpZlibException (ZlibException ${if (code < 0) "($code)" else code})"
    }
    fun showsPrec(precedence: Int): String {
        val fields = this is TooManyRedirects || this is InvalidStatusLine || this is ResponseBodyTooShort || this is Zlib
        return if (fields && precedence > 10) "(${show()})" else show()
    }
}
class HttpContentException(val failure: HttpFailureContent) : RuntimeException() {
    override val message: String? get() = "HttpExceptionContentWrapper {unHttpExceptionContentWrapper = ${failure.show()}}"
}
class HttpRequestException(val request: HttpRequest, val failure: HttpFailureContent) : RuntimeException() {
    override val message: String? get() = "HttpExceptionRequest ${request.show()} ${failure.showsPrec(11)}"
}
/** Exact ResourceVanished condition; transport-specific IO error mapping is separate. */
class HttpResourceVanishedException(message: String) : java.io.IOException(message)
private fun fail(content: HttpFailureContent): Nothing = throw HttpContentException(content)

data class HttpConnection(
    val read: () -> HttpBytes,
    val unread: (HttpBytes) -> Unit,
    val write: (HttpBytes) -> Unit,
    val close: () -> Unit,
)
class HttpDummyConnection(input: List<HttpBytes>) {
    private val incoming = ArrayDeque(input)
    private val outgoing = mutableListOf<HttpBytes>()
    val connection = HttpConnection(
        { incoming.removeFirstOrNull() ?: HttpBytes.empty }, { incoming.addFirst(it) },
        { outgoing.add(it) }, {},
    )
    fun input() = incoming.toList()
    fun output() = outgoing.toList().also { outgoing.clear() }
}
object HttpConnections {
    private val cleaner = Cleaner.create()
    private class CloseState(val action: () -> Unit) : Runnable {
        val closed = AtomicBoolean()
        override fun run() { if (!closed.getAndSet(true)) action() }
    }
    fun makeConnection(read: () -> HttpBytes, write: (HttpBytes) -> Unit, close: () -> Unit): HttpConnection {
        val stack = ConcurrentLinkedDeque<HttpBytes>()
        val state = CloseState(close)
        cleaner.register(stack, state)
        fun guard() { if (state.closed.get()) fail(HttpFailureContent.ConnectionClosed) }
        return HttpConnection(
            { guard(); stack.pollFirst() ?: read() },
            { guard(); stack.addFirst(it) },
            { guard(); write(it) },
            { state.run() },
        )
    }
    fun strippedHostName(host: String): String = Chars.build(strippedHostName(Chars.fromString(host)))
    fun strippedHostName(host: CharacterString): CharacterString {
        val p = host.points()
        if (p.firstOrNull() != 91 || p.getOrNull(1) == 118) return host
        val end = p.indexOf(93)
        return if (end == p.lastIndex && end >= 1) CharacterString(p.copyOfRange(1, end)) else host
    }
    fun readLine(maxLength: Long?, conn: HttpConnection): HttpBytes {
        val bytes = conn.read()
        if (bytes.size == 0) fail(HttpFailureContent.IncompleteHeaders)
        return readLineWith(maxLength, conn, bytes)
    }
    fun readLineWith(maxLength: Long?, conn: HttpConnection, initial: HttpBytes): HttpBytes {
        val result = ByteArrayOutputStream()
        var bytes = initial.bytes(); var total = 0L
        while (true) {
            val lf = bytes.indexOf(10.toByte())
            if (lf < 0) {
                total += bytes.size
                if (maxLength != null && total > maxLength) fail(HttpFailureContent.OverlongHeaders)
                result.write(bytes)
                bytes = conn.read().bytes()
                if (bytes.isEmpty()) fail(HttpFailureContent.IncompleteHeaders)
            } else {
                if (lf + 1 < bytes.size) conn.unread(HttpBytes(bytes.copyOfRange(lf + 1, bytes.size)))
                result.write(bytes, 0, lf)
                val line = result.toByteArray()
                return HttpBytes(if (line.lastOrNull() == 13.toByte()) line.copyOf(line.size - 1) else line)
            }
        }
    }
    fun unreadLine(conn: HttpConnection, line: HttpBytes) { conn.unread(HttpBytes.pack("\r\n")); conn.unread(line) }
    fun dropTillBlankLine(maxLength: Long?, conn: HttpConnection) { while (readLine(maxLength, conn).size != 0) {} }
}

class HttpStatus(val code: Long, val message: HttpBytes) : Comparable<HttpStatus> {
    override fun equals(other: Any?) = other is HttpStatus && code == other.code
    override fun hashCode() = code.hashCode()
    override fun compareTo(other: HttpStatus) = code.compareTo(other.code)
    fun show() = "Status {statusCode = $code, statusMessage = ${message.show()}}"
}
data class HttpVersion(val major: Long, val minor: Long) : Comparable<HttpVersion> {
    override fun compareTo(other: HttpVersion) = major.compareTo(other.major).takeIf { it != 0 } ?: minor.compareTo(other.minor)
    fun show() = "HTTP/$major.$minor"
}
data class HttpStatusHeaders(val status: HttpStatus, val version: HttpVersion, val early: List<HttpHeader>, val headers: List<HttpHeader>)
interface HttpTimeoutBoundary {
    fun <A> run(microseconds: Long, action: () -> A): A
}
object HttpSystemTimeout : HttpTimeoutBoundary {
    override fun <A> run(microseconds: Long, action: () -> A): A {
        if (microseconds < 0) return action()
        if (microseconds == 0L) fail(HttpFailureContent.ResponseTimeout)
        val task = FutureTask(action)
        Thread.ofVirtual().start(task)
        return try { task.get(microseconds, TimeUnit.MICROSECONDS) }
        catch (_: TimeoutException) { task.cancel(true); fail(HttpFailureContent.ResponseTimeout) }
        catch (e: InterruptedException) { task.cancel(true); throw e }
        catch (e: ExecutionException) { throw e.cause!! }
    }
}
object HttpHeaders {
    /** Unlike Read Int, bytestring-0.12.2.0 readInt rejects overflow. */
    private fun readInt(bytes: String): Long? {
        var at = 0
        if (bytes.firstOrNull() in listOf('+', '-')) at++
        if (at == bytes.length) return null
        while (at < bytes.length) { if (bytes[at++] !in '0'..'9') return null }
        return bytes.toLongOrNull()
    }
    private fun parseStatus(bytes: HttpBytes): Pair<HttpStatus, HttpVersion> {
        val s = bytes.latin1(); val space = s.indexOf(' ').let { if (it < 0) s.length else it }
        val version = s.take(space)
        var at = space; while (at < s.length && s[at] == ' ') at++
        val end = s.indexOf(' ', at).let { if (it < 0) s.length else it }
        val code = readInt(s.substring(at, end))
        at = end; while (at < s.length && s[at] == ' ') at++
        val dot = version.indexOf('.', 5)
        val major = if (version.startsWith("HTTP/")) readInt(if (dot < 0) version.drop(5) else version.substring(5, dot)) else null
        val minor = if (dot >= 0) readInt(version.substring(dot + 1)) else null
        if (code == null || major == null || minor == null) fail(HttpFailureContent.InvalidStatusLine(bytes))
        return HttpStatus(code, HttpBytes.pack(s.substring(at))) to HttpVersion(major, minor)
    }
    private fun parseHeader(bytes: HttpBytes): HttpHeader? {
        val s = bytes.latin1(); val colon = s.indexOf(':'); if (colon < 0) return null
        return HttpHeader(s.substring(0, colon).trim(' '), s.substring(colon + 1).trim(' '))
    }
    fun validate(headers: List<HttpHeader>): HttpBytes? {
        val reasons = headers.filter { 10.toByte() in it.value.bytes() }
        return if (reasons.isEmpty()) null else HttpBytes.pack(reasons.joinToString("") { "Header ${it.name.latin1()} has newlines\n" })
    }
    fun parseStatusHeaders(maxLength: Long?, maxHeaders: Long?, conn: HttpConnection,
        timeout: Long? = null, onEarlyHints: (List<HttpHeader>) -> Unit = {}, continuation: (() -> Unit)? = null,
        timeoutBoundary: HttpTimeoutBoundary = HttpSystemTimeout,
    ): HttpStatusHeaders {
        fun parseHeaders(early: Boolean): List<HttpHeader> {
            val headers = mutableListOf<HttpHeader>(); var count = 0L
            while (true) {
                if (maxHeaders != null && count >= maxHeaders) fail(HttpFailureContent.TooManyHeaderFields)
                val line = HttpConnections.readLine(maxLength, conn)
                if (line.size == 0) return headers
                val header = parseHeader(line)
                if (header == null) {
                    if (early) { HttpConnections.unreadLine(conn, line); return headers }
                } else { headers.add(header); count++ }
            }
        }
        fun next(): HttpStatusHeaders? {
            val early = mutableListOf<HttpHeader>()
            while (true) {
                val bytes = conn.read(); if (bytes.size == 0) fail(HttpFailureContent.NoResponseDataReceived)
                var line = HttpConnections.readLineWith(maxLength, conn, bytes); var skips = 3
                while (line.size == 0 && skips > 0) { line = HttpConnections.readLine(maxLength, conn); skips-- }
                val (status, version) = parseStatus(line)
                when (status.code) {
                    100L -> { HttpConnections.dropTillBlankLine(maxLength, conn); return null }
                    103L -> { val hs = parseHeaders(true); onEarlyHints(hs); early.addAll(hs) }
                    else -> return HttpStatusHeaders(status, version, early, parseHeaders(false))
                }
            }
        }
        fun <A> timed(action: () -> A) = if (timeout == null) action() else timeoutBoundary.run(timeout, action)
        fun getStatus(): HttpStatusHeaders = timed { var result = next(); while (result == null) result = next(); result }
        if (continuation == null) return getStatus()
        val status = timed(::next)
        return status ?: run { continuation(); getStatus() }
    }
}

fun interface HttpBodyReader { fun read(): HttpBytes }
object HttpBodies {
    /** Lazy ByteString Show joins chunks without exposing chunk boundaries. */
    fun showLazy(chunks: List<HttpBytes>): String {
        val bytes = ByteArrayOutputStream()
        chunks.forEach { bytes.write(it.bytes()) }
        return HttpBytes(bytes.toByteArray()).show()
    }
    val empty = HttpBodyReader { HttpBytes.empty }
    fun const(chunks: List<HttpBytes>): HttpBodyReader { val iter = chunks.iterator(); return HttpBodyReader { if (iter.hasNext()) iter.next() else HttpBytes.empty } }
    fun consume(reader: HttpBodyReader): List<HttpBytes> = buildList { while (true) { val b = reader.read(); if (b.size == 0) break; add(b) } }
    fun readSome(reader: HttpBodyReader, count: Long): List<HttpBytes> = buildList { var n = count; while (n > 0) { val b = reader.read(); if (b.size == 0) break; add(b); n -= b.size } }
    fun unlimited(cleanup: () -> Unit, conn: HttpConnection) = HttpBodyReader { conn.read().also { if (it.size == 0) cleanup() } }
    fun length(cleanup: () -> Unit, initial: Long, conn: HttpConnection): HttpBodyReader {
        var count = initial
        return HttpBodyReader {
            if (count <= 0) HttpBytes.empty else {
                val bytes = conn.read()
                if (bytes.size == 0) fail(HttpFailureContent.ResponseBodyTooShort(initial.toULong(), (initial - count).toULong()))
                when {
                    count < bytes.size -> { val data = bytes.bytes(); val head = HttpBytes(data.copyOf(count.toInt())); conn.unread(HttpBytes(data.copyOfRange(count.toInt(), data.size))); count = -1; cleanup(); head }
                    count == bytes.size.toLong() -> { count = -1; cleanup(); bytes }
                    else -> { count -= bytes.size; bytes }
                }
            }
        }
    }
    fun chunked(maxLength: Long?, cleanup: () -> Unit, raw: Boolean, conn: HttpConnection): HttpBodyReader {
        var remaining = 0L
        fun readHeader(): Pair<HttpBytes, Long> {
            val line = HttpConnections.readLine(maxLength, conn); val bytes = line.bytes(); var n = 0L; var at = 0
            while (at < bytes.size) { val c = bytes[at].toInt() and 255; val digit = when(c) { in 48..57 -> c - 48; in 65..70 -> c - 55; in 97..102 -> c - 87; else -> break }; n = n * 16 + digit; at++ }
            if (at == 0) fail(HttpFailureContent.InvalidChunkHeaders)
            return (line + HttpBytes.pack("\r\n")) to n
        }
        return HttpBodyReader {
            val (header, count) = if (remaining == 0L) readHeader() else HttpBytes.empty to remaining
            val result = if (count <= 0) {
                remaining = -1
                if (count != -1L && raw) {
                    var trailers = header
                    while (true) { val line = HttpConnections.readLine(maxLength, conn); trailers += line + HttpBytes.pack("\r\n"); if (line.size == 0) break }
                    trailers
                } else { if (count != -1L) HttpConnections.dropTillBlankLine(maxLength, conn); HttpBytes.empty }
            } else {
                val bytes = conn.read(); if (bytes.size == 0) fail(HttpFailureContent.InvalidChunkHeaders)
                val data = if (count < bytes.size) { val b = bytes.bytes(); conn.unread(HttpBytes(b.copyOfRange(count.toInt(), b.size))); HttpBytes(b.copyOf(count.toInt())) } else bytes
                val done = count <= bytes.size
                if (done && HttpConnections.readLine(maxLength, conn).size != 0) fail(HttpFailureContent.InvalidChunkHeaders)
                remaining = if (done) 0 else count - bytes.size
                (if (raw) header else HttpBytes.empty) + data + (if (raw && done) HttpBytes.pack("\r\n") else HttpBytes.empty)
            }
            if (result.size == 0) cleanup()
            result
        }
    }
    fun gzip(reader: HttpBodyReader): HttpBodyReader {
        val inflate = StreamingInflate(31)
        var popper: (() -> InflatePop)? = null
        return HttpBodyReader {
            var result: HttpBytes? = null
            var current = popper
            while (result == null) {
                if (current == null) {
                    val bytes = reader.read()
                    if (bytes.size == 0) result = HttpBytes.empty else current = inflate.feed(bytes)
                }
                val active = current
                if (active != null) when (val pop = active()) {
                    InflatePop.Done -> { val b = inflate.flush(); current = null; if (b.size != 0) { popper = null; result = b } }
                    is InflatePop.Next -> { popper = current; result = pop.bytes }
                    is InflatePop.Error -> fail(HttpFailureContent.Zlib(pop.code))
                }
            }
            checkNotNull(result)
        }
    }
}
enum class HttpReuse { Reuse, DontReuse }
class HttpManagedConnection(val connection: HttpConnection, val release: (HttpReuse) -> Unit, val keepAlive: () -> Unit = {})
data class HttpResponse<A>(val status: HttpStatus, val version: HttpVersion, val headers: List<HttpHeader>,
    val body: A, val close: () -> Unit, val originalRequest: HttpRequest, val early: List<HttpHeader>, val cookieJar: HttpCookieJar = HttpCookieJar.empty)
object HttpResponses {
    fun <A> showResponse(response: HttpResponse<A>, showBody: (A) -> String, precedence: Int = 0): String {
        fun headers(hs: List<HttpHeader>) = hs.joinToString(",", "[", "]") { "(${it.name.show()},${it.value.show()})" }
        val shown = "Response {responseStatus = ${response.status.show()}, responseVersion = ${response.version.show()}, " +
            "responseHeaders = ${headers(response.headers)}, responseBody = ${showBody(response.body)}, " +
            "responseCookieJar = ${response.cookieJar.show()}, responseClose' = ResponseClose, " +
            "responseOriginalRequest = ${response.originalRequest.show()}, responseEarlyHints = ${headers(response.early)}}"
        return if (precedence > 10) "($shown)" else shown
    }
    /** Original http-client policy: compare against the first host in the chain. */
    fun getRedirectedRequest(original: HttpRequest, request: HttpRequest, headers: List<HttpHeader>,
        cookieJar: HttpCookieJar, status: Long,
    ): HttpRequest? {
        if (status !in 300..399) return null
        val location = headers.firstOrNull { it.named("location") }?.value ?: return null
        val escaped = NetworkURI.escapeURIString(NetworkURI::isAllowedInURI, Chars.fromString(location.latin1()))
        val uri = NetworkURI.parseURIReference(escaped) ?: return null
        val resolved = NetworkURI.relativeTo(uri, HttpRequests.getUri(request))
        val updated = when (val result = HttpRequests.setUriEither(request, resolved)) {
            is HttpUriResult.Failure -> return null
            is HttpUriResult.Success -> result.request
        }
        fun selected(header: HttpHeader) = request.shouldStripHeaderOnRedirect(HttpHeaderName(header.name))
        val stripped = if (original.host != updated.host || !request.shouldStripHeaderOnRedirectIfOnDifferentHostOnly) {
            updated.requestHeaders.filterNot(::selected)
        } else {
            // nubBy keeps the first current value and original spelling, even
            // for duplicate names unrelated to the stripping predicate.
            val seen = HashSet<HttpHeaderName>()
            (updated.requestHeaders + original.requestHeaders.filter(::selected))
                .filter { seen.add(HttpHeaderName(it.name)) }
        }
        val result = updated.copy(requestHeaders = stripped, cookieJar = if (request.cookieJar == null) null else cookieJar)
        return if (status == 302L || status == 303L) result.copy(
            method = HttpBytes.pack("GET"), requestBody = HttpRequestBody.Strict(HttpBytes.empty),
            requestHeaders = result.requestHeaders.filterNot { it.named("content-type") },
        ) else result
    }
    fun hasNoBody(method: HttpBytes, status: Long) = method == HttpBytes.pack("HEAD") || status == 204L || status == 304L || status in 100..199
    fun getResponse(maxLength: Long?, maxHeaders: Long?, timeout: Long?, request: HttpRequest, managed: HttpManagedConnection,
        continuation: (() -> Unit)? = null, timeoutBoundary: HttpTimeoutBoundary = HttpSystemTimeout,
    ): HttpResponse<HttpBodyReader> {
        val status = HttpHeaders.parseStatusHeaders(maxLength, maxHeaders, managed.connection, timeout, request.earlyHintHeadersReceived, continuation, timeoutBoundary)
        val hs = status.headers
        val length = hs.firstOrNull { it.named("content-length") }?.value?.latin1()?.let(HttpRequests::readPositiveInt)
        val chunked = hs.any { it.named("transfer-encoding") && HttpHeader(it.value, HttpBytes.empty).named("chunked") }
        val toPut = hs.firstOrNull { it.named("connection") }?.value != HttpBytes.pack("close") && status.version > HttpVersion(1, 0)
        fun cleanup(consumed: Boolean) { managed.release(if (toPut && consumed) HttpReuse.Reuse else HttpReuse.DontReuse); managed.keepAlive(); Reference.reachabilityFence(managed) }
        val body = if (hasNoBody(request.method, status.status.code) || length == 0L && !chunked) { cleanup(true); HttpBodies.empty } else {
            val b = if (chunked) HttpBodies.chunked(maxLength, { cleanup(true) }, request.rawBody, managed.connection)
                else if (length != null) HttpBodies.length({ cleanup(true) }, length, managed.connection)
                else HttpBodies.unlimited({ cleanup(true) }, managed.connection)
            if (HttpRequests.needsGunzip(request, hs)) HttpBodies.gzip(b) else b
        }
        return HttpResponse(status.status, status.version, hs, body, { cleanup(false) }, request.copy(requestBody = HttpRequestBody.Strict(HttpBytes.empty)), status.early)
    }
    fun lbsResponse(response: HttpResponse<HttpBodyReader>) = HttpResponse(response.status, response.version, response.headers, HttpBodies.consume(response.body), response.close, response.originalRequest, response.early, response.cookieJar)
    fun <A> getOriginalRequest(response: HttpResponse<A>) = response.originalRequest
}
