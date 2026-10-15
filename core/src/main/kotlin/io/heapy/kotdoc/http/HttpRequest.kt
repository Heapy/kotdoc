// Network.HTTP.Client.Request/Types 0.7.19, Copyright (c) 2013 Michael Snoyman.
// MIT; licenses/http-client-MIT.txt. Query encoding: http-types 0.12.6,
// (c) 2011 Aristid Breitkreuz and Michael Snoyman, BSD-3-Clause.
package io.heapy.kotdoc.http

import io.heapy.kotdoc.formats.HaskellString
import io.heapy.kotdoc.formats.HaskellNumber
import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars as Chars
import io.heapy.kotdoc.parsing.RegexCharacters
import io.heapy.kotdoc.uri.NetworkURI
import io.heapy.kotdoc.uri.URI
import io.heapy.kotdoc.uri.URIAuth
import java.io.IOException
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64

/** ByteString fields must retain arbitrary bytes, including malformed UTF-8. */
class HttpBytes(bytes: ByteArray) : Comparable<HttpBytes> {
    private val data = bytes.copyOf()
    fun bytes() = data.copyOf()
    val size get() = data.size
    fun latin1(): String = data.toString(Charsets.ISO_8859_1)
    fun show() = HaskellString.quoteCodePoints(data.map { it.toInt() and 255 }.toIntArray())
    override fun equals(other: Any?) = other is HttpBytes && data.contentEquals(other.data)
    override fun hashCode() = data.contentHashCode()
    override fun compareTo(other: HttpBytes): Int {
        for (i in 0..<minOf(data.size, other.data.size)) {
            val n = (data[i].toInt() and 255).compareTo(other.data[i].toInt() and 255)
            if (n != 0) return n
        }
        return data.size.compareTo(other.data.size)
    }
    operator fun plus(other: HttpBytes) = HttpBytes(data + other.data)
    companion object {
        val empty = HttpBytes(byteArrayOf())
        /** Data.ByteString.Char8.pack truncates each Haskell Char, not UTF-8. */
        fun pack(value: CharacterString) = HttpBytes(value.points().map { it.toByte() }.toByteArray())
        fun pack(value: String) = pack(Chars.fromString(value))
    }
}

data class HttpHeader(val name: HttpBytes, val value: HttpBytes) : Comparable<HttpHeader> {
    constructor(name: String, value: String) : this(HttpBytes.pack(name), HttpBytes.pack(value))
    fun named(other: HttpBytes) = fold(name) == fold(other)
    fun named(other: String) = named(HttpBytes.pack(other))
    override fun equals(other: Any?) = other is HttpHeader && named(other.name) && value == other.value
    override fun hashCode() = 31 * fold(name).hashCode() + value.hashCode()
    override fun compareTo(other: HttpHeader) = fold(name).compareTo(fold(other.name)).takeIf { it != 0 } ?: value.compareTo(other.value)
    companion object {
        // case-insensitive's ByteString instance uses Latin-1 case folding.
        private fun fold(bytes: HttpBytes) = HttpBytes(bytes.bytes().map {
            val b = it.toInt() and 255
            (if (b in 65..90 || b in 192..214 || b in 216..222) b + 32 else b).toByte()
        }.toByteArray())
    }
}

data class HttpProxy(val host: HttpBytes, val port: Long) {
    fun show() = "Proxy {proxyHost = ${host.show()}, proxyPort = $port}"
}
enum class ProxySecureMode { ProxySecureWithConnect, ProxySecureWithoutConnect }
sealed interface HttpResponseTimeout {
    data object Default : HttpResponseTimeout
    data object None : HttpResponseTimeout
    data class Micro(val microseconds: Long) : HttpResponseTimeout
    fun show() = when (this) {
        Default -> "ResponseTimeoutDefault"
        None -> "ResponseTimeoutNone"
        is Micro -> "ResponseTimeoutMicro ${if (microseconds < 0) "($microseconds)" else microseconds}"
    }
}

class InvalidHttpUrl(val url: CharacterString, val reason: String) : RuntimeException(
    "InvalidUrlException ${HaskellString.quoteCodePoints(url.points())} ${HaskellString.quote(reason)}",
)
class WrongRequestBodyStreamSize(val expected: ULong, val actual: ULong) : RuntimeException(
    "WrongRequestBodyStreamSize $expected $actual",
)

/** A provider owns the lifetime of its popper (for example an open file). */
fun interface HttpBodyProvider { fun withPopper(consume: (() -> HttpBytes) -> Unit) }
sealed interface HttpRequestBody {
    data class Strict(val data: HttpBytes) : HttpRequestBody
    data class Lazy(val chunks: List<HttpBytes>) : HttpRequestBody
    data class Builder(val length: Long, val build: () -> HttpBytes) : HttpRequestBody
    data class Stream(val length: Long?, val provider: HttpBodyProvider) : HttpRequestBody
    data class IO(val action: () -> HttpRequestBody) : HttpRequestBody
}
data class HttpStreamFileStatus(val fileSize: Long, val readSoFar: Long, val thisChunkSize: Int)

/** Request policy hooks remain effects; requestBuilder calls only the body hook.
 * Response checking/redirect/cookie/manager behavior is owned by the transport. */
data class HttpRequest(
    val method: HttpBytes = HttpBytes.pack("GET"),
    val secure: Boolean = false,
    val host: HttpBytes = HttpBytes.pack("localhost"),
    val port: Long = 80,
    val path: HttpBytes = HttpBytes.pack("/"),
    val queryString: HttpBytes = HttpBytes.empty,
    val requestHeaders: List<HttpHeader> = emptyList(),
    val requestBody: HttpRequestBody = HttpRequestBody.Lazy(emptyList()),
    val proxy: HttpProxy? = null,
    val rawBody: Boolean = false,
    val decompress: (HttpBytes) -> Boolean = HttpRequests::browserDecompress,
    val redirectCount: Long = 10,
    val checkStatus: Boolean = false,
    val responseTimeout: HttpResponseTimeout = HttpResponseTimeout.Default,
    val versionMajor: Long = 1,
    val versionMinor: Long = 1,
    val onRequestBodyException: (Exception) -> Unit = { if (it !is IOException) throw it },
    val proxySecureMode: ProxySecureMode = ProxySecureMode.ProxySecureWithConnect,
    val redactHeaders: List<HttpBytes> = listOf(HttpBytes.pack("Authorization")),
    val earlyHintHeadersReceived: (List<HttpHeader>) -> Unit = {},
) {
    fun show(): String = buildString {
        fun field(name: String, value: Any) { append("  ").append(name.padEnd(21)).append("= ").append(value).append('\n') }
        fun bool(value: Boolean) = if (value) "True" else "False"
        append("Request {\n")
        field("host", host.show()); field("port", port); field("secure", bool(secure))
        field("requestHeaders", requestHeaders.joinToString(",", "[", "]") { h ->
            val value = if (redactHeaders.any(h::named)) HttpBytes.pack("<REDACTED>") else h.value
            "(${h.name.show()},${value.show()})"
        })
        field("path", path.show()); field("queryString", queryString.show()); field("method", method.show())
        field("proxy", proxy?.let { "Just (${it.show()})" } ?: "Nothing")
        field("rawBody", bool(rawBody)); field("redirectCount", redirectCount)
        field("responseTimeout", responseTimeout.show()); field("requestVersion", "HTTP/$versionMajor.$versionMinor")
        field("proxySecureMode", proxySecureMode); append("}\n")
    }
}

sealed interface HttpUriResult {
    data class Success(val request: HttpRequest) : HttpUriResult
    data class Failure(val reason: String) : HttpUriResult
}
sealed interface HttpEscapeItem {
    data class QE(val bytes: HttpBytes) : HttpEscapeItem
    data class QN(val bytes: HttpBytes) : HttpEscapeItem
}

object HttpRequests {
    val defaultRequest get() = HttpRequest()
    fun parseRequest(value: String) = parseRequest(Chars.fromString(value))
    fun parseRequest(value: CharacterString): HttpRequest {
        val points = value.points(); val space = points.indexOf(32)
        val hasMethod = space >= 0 && (0..<space).all { points[it] in 65..90 }
        val url = if (hasMethod) CharacterString(points.copyOfRange(space + 1, points.size)) else value
        val encoded = NetworkURI.escapeURIString(NetworkURI::isAllowedInURI, url)
        val uri = NetworkURI.parseURI(encoded) ?: throw InvalidHttpUrl(url, "Invalid URL")
        val request = setUri(defaultRequest, uri)
        return if (hasMethod) request.copy(method = HttpBytes.pack(CharacterString(points.copyOfRange(0, space)))) else request
    }
    fun parseUrlThrow(value: CharacterString) = setRequestCheckStatus(parseRequest(value))
    fun parseUrlThrow(value: String) = parseUrlThrow(Chars.fromString(value))
    fun parseUrl(value: CharacterString) = parseUrlThrow(value)
    fun parseUrl(value: String) = parseUrlThrow(value)
    fun parseRequest_(value: CharacterString) = parseRequest(value)
    fun parseRequest_(value: String) = parseRequest(value)
    fun requestFromURI(uri: URI) = setUri(defaultRequest, uri)
    fun requestFromURI_(uri: URI) = requestFromURI(uri)
    fun getUri(request: HttpRequest): URI = URI(
        scheme = if (request.secure) "https:" else "http:",
        authority = URIAuth(regName = request.host.latin1(), port =
            if (request.port == if (request.secure) 443L else 80L) "" else ":${request.port}"),
        path = request.path.latin1(),
        query = request.queryString.latin1().let { if (it.isNotEmpty() && it[0] != '?') "?$it" else it },
    )
    fun setUriRelative(request: HttpRequest, uri: URI) = setUri(request, NetworkURI.relativeTo(uri, getUri(request)))
    fun setUri(request: HttpRequest, uri: URI): HttpRequest = when (val result = setUriEither(request, uri)) {
        is HttpUriResult.Success -> result.request
        is HttpUriResult.Failure -> throw InvalidHttpUrl(uri.showCharacters(), result.reason)
    }
    fun setUriEither(request: HttpRequest, uri: URI): HttpUriResult {
        val scheme = uri.scheme.points().map(RegexCharacters::toLower).toIntArray()
        val secure = when {
            scheme.contentEquals("http:".codePoints().toArray()) -> false
            scheme.contentEquals("https:".codePoints().toArray()) -> true
            else -> return HttpUriResult.Failure("Invalid scheme")
        }
        val auth = uri.authority ?: return HttpUriResult.Failure("URL must be absolute")
        val portString = auth.port.points()
        val port = if (portString.firstOrNull() == 58) {
            readPositiveInt(Chars.build(CharacterString(portString.copyOfRange(1, portString.size))))
                ?: return HttpUriResult.Failure("Invalid port")
        } else if (secure) 443L else 80L
        val updated = request.copy(host = HttpBytes.pack(auth.regName), port = port, secure = secure,
            path = if (uri.path.points().isEmpty()) HttpBytes.pack("/") else HttpBytes.pack(uri.path),
            queryString = HttpBytes.pack(uri.query))
        val credentials = extractBasicAuthInfo(uri) ?: return HttpUriResult.Success(updated)
        return HttpUriResult.Success(applyBasicAuth(credentials.first, credentials.second, updated))
    }

    /** readMaybe Int accepts Haskell integer syntax and wraps before its >=0 guard. */
    internal fun readPositiveInt(value: String): Long? = HaskellNumber.readInteger(value)?.toLong()?.takeIf { it >= 0 }
    fun extractBasicAuthInfo(uri: URI): Pair<HttpBytes, HttpBytes>? {
        val userInfo = uri.authority?.userInfo?.points() ?: return null
        if (58 !in userInfo) return null
        val end = userInfo.indexOf(64).let { if (it < 0) userInfo.size else it }
        val colon = (0..<end).firstOrNull { userInfo[it] == 58 }
            ?: throw IllegalStateException("Non-exhaustive patterns in (username, ':' : password)")
        fun literal(start: Int, last: Int) = HttpBytes.pack(NetworkURI.unEscapeString(CharacterString(userInfo.copyOfRange(start, last))))
        return literal(0, colon) to literal(colon + 1, end)
    }
    fun browserDecompress(mime: HttpBytes) = mime != HttpBytes.pack("application/x-tar")
    fun alwaysDecompress(@Suppress("UNUSED_PARAMETER") mime: HttpBytes) = true
    private fun header(name: String, value: HttpBytes, request: HttpRequest) = request.copy(requestHeaders = listOf(HttpHeader(HttpBytes.pack(name), value)) + request.requestHeaders)
    private fun basic(user: HttpBytes, password: HttpBytes) = HttpBytes.pack("Basic ") + HttpBytes(Base64.getEncoder().encode(user.bytes() + byteArrayOf(58) + password.bytes()))
    fun applyBasicAuth(user: HttpBytes, password: HttpBytes, request: HttpRequest) = header("Authorization", basic(user, password), request)
    fun applyBasicProxyAuth(user: HttpBytes, password: HttpBytes, request: HttpRequest) = header("Proxy-Authorization", basic(user, password), request)
    fun applyBearerAuth(token: HttpBytes, request: HttpRequest) = header("Authorization", HttpBytes.pack("Bearer ") + token, request)
    fun addProxy(host: HttpBytes, port: Long, request: HttpRequest) = request.copy(proxy = HttpProxy(host, port))
    fun addProxySecureWithoutConnect(request: HttpRequest) = request.copy(proxySecureMode = ProxySecureMode.ProxySecureWithoutConnect)
    fun setRequestIgnoreStatus(request: HttpRequest) = request.copy(checkStatus = false)
    fun setRequestCheckStatus(request: HttpRequest) = request.copy(checkStatus = true)
    fun needsGunzip(request: HttpRequest, headers: List<HttpHeader>) = !request.rawBody &&
        headers.any { it.named("content-encoding") && it.value == HttpBytes.pack("gzip") } &&
        request.decompress(headers.firstOrNull { it.named("content-type") }?.value ?: HttpBytes.empty)
    private fun encode(bytes: HttpBytes): HttpBytes = HttpBytes.pack(buildString {
        for (byte in bytes.bytes()) {
            val b = byte.toInt() and 255
            if (b in 65..90 || b in 97..122 || b in 48..57 || b.toChar() in "-_.~") append(b.toChar())
            else append('%').append(b.toString(16).uppercase().padStart(2, '0'))
        }
    })
    fun setQueryString(query: List<Pair<HttpBytes, HttpBytes?>>, request: HttpRequest) = request.copy(
        queryString = HttpBytes.pack(query.joinToString("&", if (query.isEmpty()) "" else "?") { (k, v) ->
            encode(k).latin1() + (v?.let { "=" + encode(it).latin1() } ?: "")
        }))
    fun setQueryStringPartialEscape(query: List<Pair<HttpBytes, List<HttpEscapeItem>>>, request: HttpRequest) = request.copy(
        queryString = HttpBytes.pack(query.joinToString("&", if (query.isEmpty()) "" else "?") { (k, vs) ->
            encode(k).latin1() + if (vs.isEmpty()) "" else "=" + vs.joinToString("") {
                when (it) { is HttpEscapeItem.QE -> encode(it.bytes); is HttpEscapeItem.QN -> it.bytes }.latin1()
            }
        }))
    fun urlEncodedBody(query: List<Pair<HttpBytes, HttpBytes>>, request: HttpRequest): HttpRequest {
        val data = setQueryString(query, defaultRequest).queryString.bytes().drop(1).toByteArray()
        return request.copy(method = HttpBytes.pack("POST"), requestBody = HttpRequestBody.Lazy(listOf(HttpBytes(data))),
            requestHeaders = listOf(HttpHeader("Content-Type", "application/x-www-form-urlencoded")) + request.requestHeaders.filterNot { it.named("Content-Type") })
    }

    /** Returns the deferred body action only for exact Expect: 100-continue.
     * Empty writes are observable Blaze flushes, not omitted socket data. */
    fun requestBuilder(request: HttpRequest, write: (HttpBytes) -> Unit): (() -> Unit)? {
        class PopperException(val original: Exception) : RuntimeException(original)
        fun checked(action: () -> Unit) {
            try { action() } catch (e: PopperException) { throw e.original }
            catch (e: Exception) { request.onRequestBodyException(e) }
        }
        fun resolved(body: HttpRequestBody): HttpRequestBody = if (body is HttpRequestBody.IO) resolved(body.action()) else body
        val body = resolved(request.requestBody)
        val length = when (body) {
            is HttpRequestBody.Strict -> body.data.size.toLong()
            is HttpRequestBody.Lazy -> body.chunks.sumOf { it.size.toLong() }
            is HttpRequestBody.Builder -> body.length
            is HttpRequestBody.Stream -> body.length
            is HttpRequestBody.IO -> error("resolved IO")
        }
        val headers = buildHeaders(request, length)
        fun blazeWrite(parts: Sequence<HttpBytes?>) {
            // Blaze.toByteStringIO and bytestring.byteStringThreshold: small
            // segments copy into a 32752-byte buffer, >8160-byte segments insert.
            val buffer = ByteArrayOutputStream(32752)
            fun emit() { write(HttpBytes(buffer.toByteArray())); buffer.reset() }
            for (part in parts) {
                if (part == null) { if (buffer.size() > 0) emit(); continue }
                val bytes = part.bytes()
                if (bytes.size > 8160) {
                    if (buffer.size() > 0) emit()
                    write(part)
                } else {
                    var at = 0
                    while (at < bytes.size) {
                        if (buffer.size() == 32752) emit()
                        val count = minOf(bytes.size - at, 32752 - buffer.size())
                        buffer.write(bytes, at, count); at += count
                    }
                }
            }
            emit() // Done also exposes an empty buffer.
        }
        fun flushHeaders() { blazeWrite(sequence { yieldAll(headers); yield(null) }) }
        fun bodyParts(): Sequence<HttpBytes> = sequence {
            when (body) {
                is HttpRequestBody.Strict -> yield(body.data)
                is HttpRequestBody.Lazy -> yieldAll(body.chunks)
                is HttpRequestBody.Builder -> yield(body.build())
                else -> error("non-streaming")
            }
        }
        fun sendBody() {
            when (body) {
                is HttpRequestBody.Strict, is HttpRequestBody.Lazy, is HttpRequestBody.Builder -> blazeWrite(bodyParts())
                is HttpRequestBody.Stream -> body.provider.withPopper { pop ->
                    var sent = 0L
                    while (true) {
                        val bytes = try { pop() } catch (e: Exception) { throw PopperException(e) }
                        if (bytes.size == 0) {
                            if (length == null) write(HttpBytes.pack("0\r\n\r\n"))
                            else if (length != sent) throw WrongRequestBodyStreamSize(length.toULong(), sent.toULong())
                            break
                        }
                        write(if (length == null) HttpBytes.pack(bytes.size.toString(16) + "\r\n") + bytes + HttpBytes.pack("\r\n") else bytes)
                        sent += bytes.size
                    }
                }
                is HttpRequestBody.IO -> error("resolved IO")
            }
        }
        if (request.requestHeaders.firstOrNull { it.named("Expect") }?.value == HttpBytes.pack("100-continue")) {
            flushHeaders()
            return { checked(::sendBody) }
        }
        if (body is HttpRequestBody.Stream) {
            flushHeaders() // The upstream body-exception hook does not cover these writes.
            checked(::sendBody)
        } else checked { blazeWrite(sequence { yieldAll(headers); yieldAll(bodyParts()) }) }
        return null
    }

    private fun buildHeaders(request: HttpRequest, length: Long?): List<HttpBytes> {
        val hh = request.host + HttpBytes.pack(if (request.port == if (request.secure) 443L else 80L) "" else ":${request.port}")
        var headers = request.requestHeaders
        headers = if (length == null) listOf(HttpHeader("Transfer-Encoding", "chunked")) + headers
            else if (request.method.latin1() in listOf("GET", "HEAD") && length == 0L) headers
            else listOf(HttpHeader("Content-Length", length.toString())) + headers
        headers = when (request.requestHeaders.firstOrNull { it.named("Accept-Encoding") }?.value) {
            null -> listOf(HttpHeader("Accept-Encoding", "gzip")) + headers
            HttpBytes.empty -> headers.filterNot { it.named("Accept-Encoding") }
            else -> headers
        }
        if (headers.none { it.named("Host") }) headers = listOf(HttpHeader(HttpBytes.pack("Host"), hh)) + headers
        val origin = if (request.proxy != null && (!request.secure || request.proxySecureMode == ProxySecureMode.ProxySecureWithoutConnect))
            (if (request.secure) "https://" else "http://") + hh.latin1() else ""
        val query = request.queryString.latin1().let { if (it.isEmpty() || it.startsWith('?')) it else "?$it" }
        return buildList {
            add(request.method); add(HttpBytes.pack(" "))
            if (origin.isNotEmpty()) { add(HttpBytes.pack(if (request.secure) "https://" else "http://")); add(hh) }
            if (!request.path.latin1().startsWith('/')) add(HttpBytes.pack("/"))
            add(request.path)
            if (query.isNotEmpty() && !request.queryString.latin1().startsWith('?')) add(HttpBytes.pack("?"))
            add(request.queryString)
            add(HttpBytes.pack(" HTTP/${request.versionMajor}.${request.versionMinor}\r\n"))
            for (h in headers) { add(h.name); add(HttpBytes.pack(": ")); add(h.value); add(HttpBytes.pack("\r\n")) }
            add(HttpBytes.pack("\r\n"))
        }
    }

    fun streamFile(path: Path) = observedStreamFile({}, path)
    fun observedStreamFile(observer: (HttpStreamFileStatus) -> Unit, path: Path): HttpRequestBody {
        val size = Files.size(path)
        return HttpRequestBody.Stream(size, HttpBodyProvider { consume ->
            Files.newInputStream(path).use { input ->
                var position = 0L
                consume {
                    // bytestring's lazy defaultChunkSize = 32KiB - 2 machine words.
                    val buffer = ByteArray(32752)
                    val n = input.read(buffer).let { if (it < 0) 0 else it }
                    position += n
                    observer(HttpStreamFileStatus(size, position, n))
                    HttpBytes(buffer.copyOf(n))
                }
            }
        })
    }
}
