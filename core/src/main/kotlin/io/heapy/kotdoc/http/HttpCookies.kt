// http-client-0.7.19 Network.HTTP.Client.Cookies and Cookie/CookieJar Types.
// Copyright (c) 2013 Michael Snoyman. MIT; licenses/http-client-MIT.txt.
package io.heapy.kotdoc.http

import io.heapy.kotdoc.json.PrettySort
import io.heapy.kotdoc.parsing.Utf8Text
import io.heapy.kotdoc.resources.PandocUtcTime
import java.math.BigInteger
import java.util.Collections

/** No structural Eq instance: equalCookie and equivCookie are deliberately distinct. */
class HttpCookie internal constructor(
    val name: HttpBytes, val value: HttpBytes, private val expiry: Lazy<PandocUtcTime>,
    val domain: HttpBytes, val path: HttpBytes, val creationTime: PandocUtcTime, val lastAccessTime: PandocUtcTime,
    val persistent: Boolean, val hostOnly: Boolean, val secureOnly: Boolean, val httpOnly: Boolean,
) {
    constructor(name: HttpBytes, value: HttpBytes, expiryTime: PandocUtcTime, domain: HttpBytes, path: HttpBytes,
        creationTime: PandocUtcTime, lastAccessTime: PandocUtcTime, persistent: Boolean, hostOnly: Boolean, secureOnly: Boolean, httpOnly: Boolean,
    ) : this(name, value, lazyOf(expiryTime), domain, path, creationTime, lastAccessTime, persistent, hostOnly, secureOnly, httpOnly)
    val expiryTime: PandocUtcTime get() = expiry.value
    fun copy(name: HttpBytes = this.name, value: HttpBytes = this.value, expiryTime: Lazy<PandocUtcTime> = expiry,
        domain: HttpBytes = this.domain, path: HttpBytes = this.path, creationTime: PandocUtcTime = this.creationTime,
        lastAccessTime: PandocUtcTime = this.lastAccessTime, persistent: Boolean = this.persistent,
        hostOnly: Boolean = this.hostOnly, secureOnly: Boolean = this.secureOnly, httpOnly: Boolean = this.httpOnly,
    ) = HttpCookie(name, value, expiryTime, domain, path, creationTime, lastAccessTime, persistent, hostOnly, secureOnly, httpOnly)
    fun show() = "Cookie {cookie_name = ${name.show()}, cookie_value = ${value.show()}, " +
        "cookie_expiry_time = ${CookieDates.showUtc(expiryTime)}, cookie_domain = ${domain.show()}, cookie_path = ${path.show()}, " +
        "cookie_creation_time = ${CookieDates.showUtc(creationTime)}, cookie_last_access_time = ${CookieDates.showUtc(lastAccessTime)}, " +
        "cookie_persistent = ${bool(persistent)}, cookie_host_only = ${bool(hostOnly)}, " +
        "cookie_secure_only = ${bool(secureOnly)}, cookie_http_only = ${bool(httpOnly)}}"
    override fun toString() = show()
    private fun bool(value: Boolean) = if (value) "True" else "False"
}

class HttpCookieJar(cookies: List<HttpCookie>) {
    val cookies: List<HttpCookie> = Collections.unmodifiableList(cookies.toList())
    operator fun plus(other: HttpCookieJar) = HttpCookies.combine(this, other)
    fun show() = cookies.joinToString(",", "CJ {expose = [", "]}", transform = HttpCookie::show)
    override fun toString() = show()
    companion object { val empty = HttpCookieJar(emptyList()) }
}

class CookieBytesException(operation: String) : RuntimeException("Data.ByteString.$operation: empty ByteString")

object HttpCookies {
    fun createCookieJar(cookies: List<HttpCookie>) = HttpCookieJar(cookies)
    fun destroyCookieJar(jar: HttpCookieJar): List<HttpCookie> = jar.cookies
    fun equalCookie(a: HttpCookie, b: HttpCookie) = a.name == b.name && a.value == b.value && a.expiryTime == b.expiryTime &&
        a.domain == b.domain && a.path == b.path && a.creationTime == b.creationTime && a.lastAccessTime == b.lastAccessTime &&
        a.persistent == b.persistent && a.hostOnly == b.hostOnly && a.secureOnly == b.secureOnly && a.httpOnly == b.httpOnly
    fun equivCookie(a: HttpCookie, b: HttpCookie) = a.name == b.name && fold(a.domain) == fold(b.domain) && a.path == b.path
    fun compareCookies(a: HttpCookie, b: HttpCookie): Int = when {
        a.path.size > b.path.size -> -1
        a.path.size < b.path.size -> 1
        compareTime(a.creationTime, b.creationTime) > 0 -> 1
        else -> -1
    }
    fun equalCookieJar(a: HttpCookieJar, b: HttpCookieJar) = a.cookies.zip(b.cookies).all { (x,y) -> equalCookie(x,y) }
    fun equivCookieJar(a: HttpCookieJar, b: HttpCookieJar) = sort(a.cookies).zip(sort(b.cookies)).all { (x,y) -> equivCookie(x,y) }
    private fun sort(cookies: List<HttpCookie>) = PrettySort.sort(cookies) { a,b -> compareCookies(a,b) > 0 }
    fun combine(a: HttpCookieJar, b: HttpCookieJar): HttpCookieJar {
        val sorted = PrettySort.sort(a.cookies + b.cookies) { x,y -> compareTime(x.creationTime, y.creationTime) <= 0 }
        val kept = mutableListOf<HttpCookie>()
        for (cookie in sorted) if (kept.none { equivCookie(cookie, it) }) kept.add(cookie)
        return HttpCookieJar(kept)
    }
    fun isIpAddress(input: HttpBytes): Boolean {
        var remaining = input.latin1()
        repeat(4) {
            val dot = remaining.indexOf('.')
            val part = if (dot < 0) remaining else remaining.substring(0, dot)
            remaining = if (dot < 0) "" else remaining.substring(dot + 1)
            val digits = part.removePrefix("+").removePrefix("-")
            if (digits.isEmpty() || digits.any { it !in '0'..'9' }) return false
            val number = part.toLongOrNull() ?: return false
            if (number !in 0..255) return false
        }
        return remaining.isEmpty()
    }
    fun domainMatches(host: HttpBytes, domain: HttpBytes): Boolean {
        val string = fold(host); val suffix = fold(domain)
        if (string == suffix) return true
        if (string.length < suffix.length + 1) return false
        return string.endsWith(suffix) && string[string.length - suffix.length - 1] == '.' && !isIpAddress(HttpBytes.pack(string))
    }
    fun defaultPath(request: HttpRequest): HttpBytes {
        val path = request.path.latin1()
        return HttpBytes.pack(if (!path.startsWith('/') || path.count { it == '/' } <= 1) "/" else path.substringBeforeLast('/'))
    }
    fun pathMatches(requestPath: HttpBytes, cookiePath: HttpBytes): Boolean {
        val request = requestPath.latin1(); val cookie = cookiePath.latin1()
        val normalized = if (request.startsWith('/')) request else "/$request"
        if (cookie == normalized) return true
        if (!normalized.startsWith(cookie)) return false
        if (cookie.isEmpty()) throw CookieBytesException("last")
        if (cookie.last() == '/') return true
        // The original drops from requestPath, not its slash-prefixed path'.
        val remainder = request.drop(cookie.length)
        if (remainder.isEmpty()) throw CookieBytesException("head")
        return remainder.first() == '/'
    }
    fun isPotentiallyTrustworthyOrigin(secure: Boolean, host: HttpBytes): Boolean {
        if (secure) return true
        val text = host.latin1()
        if (CookieIp.ipv4(text)?.firstOrNull() == 127) return true
        if (text.startsWith('[') && text.endsWith(']')) {
            val address = CookieIp.ipv6(text.substring(1, text.length - 1))
            if (address != null && address.take(7).all { it == 0 } && address[7] == 1) return true
        }
        return text == "localhost" || text == "localhost." || text.endsWith(".localhost") || text.endsWith(".localhost.")
    }
    fun removeExistingCookieFromCookieJar(cookie: HttpCookie, jar: HttpCookieJar): Pair<HttpCookie?, HttpCookieJar> {
        val index = jar.cookies.indexOfFirst { equivCookie(cookie, it) }
        return if (index < 0) null to jar else jar.cookies[index] to HttpCookieJar(jar.cookies.filterIndexed { i,_ -> i != index })
    }
    fun insertCheckedCookie(cookie: HttpCookie, jar: HttpCookieJar, isHttpApi: Boolean): HttpCookieJar {
        val (old, remaining) = removeExistingCookieFromCookieJar(cookie, jar)
        if (old != null && !isHttpApi && old.httpOnly) return jar
        val inserted = if (old == null) cookie else cookie.copy(creationTime=old.creationTime)
        return HttpCookieJar(listOf(inserted) + remaining.cookies)
    }
    fun evictExpiredCookies(jar: HttpCookieJar, now: PandocUtcTime) = HttpCookieJar(jar.cookies.filter { compareTime(it.expiryTime, now) >= 0 })
    fun generateCookie(set: HttpSetCookie, request: HttpRequest, now: PandocUtcTime, isHttpApi: Boolean): HttpCookie? {
        var domain = set.domain?.latin1() ?: ""
        if (domain.endsWith('.')) return null
        if (domain.startsWith('.')) domain = domain.drop(1)
        if (domain.isNotEmpty() && PublicSuffixList.isSuffix(Utf8Text.decodeLenient(HttpBytes.pack(domain).bytes()))) {
            if (HttpBytes.pack(domain) != request.host) return null
            domain = ""
        }
        val hostOnly = domain.isEmpty()
        if (!hostOnly && !domainMatches(request.host, HttpBytes.pack(domain))) return null
        if (!isHttpApi && set.httpOnly) return null
        val finalDomain = if (hostOnly) request.host else HttpBytes.pack(domain)
        return HttpCookie(set.name, set.value, lazy {
            if (set.hasMaxAge) addTime(now, set.maxAgePicoseconds!!)
            else set.expires ?: PandocUtcTime(now.day + BigInteger.valueOf(365000), BigInteger.ZERO)
        }, finalDomain, set.path ?: defaultPath(request), now, now,
            set.expires != null || set.hasMaxAge, hostOnly, set.secure, set.httpOnly)
    }
    fun receiveSetCookie(set: HttpSetCookie, request: HttpRequest, now: PandocUtcTime, isHttpApi: Boolean, jar: HttpCookieJar): HttpCookieJar =
        generateCookie(set, request, now, isHttpApi)?.let { insertCheckedCookie(it, jar, isHttpApi) } ?: jar
    fun computeCookieString(request: HttpRequest, jar: HttpCookieJar, now: PandocUtcTime, isHttpApi: Boolean): Pair<HttpBytes, HttpCookieJar> {
        val matching = jar.cookies.filter { cookie ->
            (if (cookie.hostOnly) fold(request.host) == fold(cookie.domain) else domainMatches(request.host, cookie.domain)) &&
                pathMatches(request.path, cookie.path) && (!cookie.secureOnly || isPotentiallyTrustworthyOrigin(request.secure, request.host)) &&
                (!cookie.httpOnly || isHttpApi)
        }
        val output = WebCookies.renderCookies(sort(matching).map { it.name to it.value })
        var updated = jar
        for (cookie in matching) {
            val (old, remaining) = removeExistingCookieFromCookieJar(cookie, updated)
            updated = if (old == null) remaining else HttpCookieJar(listOf(old.copy(lastAccessTime=now)) + remaining.cookies)
        }
        return output to updated
    }
    fun insertCookiesIntoRequest(request: HttpRequest, jar: HttpCookieJar, now: PandocUtcTime): Pair<HttpRequest, HttpCookieJar> {
        val (header, updated) = computeCookieString(request, jar, now, true)
        if (header.size == 0) return request to updated
        val index = request.requestHeaders.indexOfFirst { it.named("Cookie") }
        val remaining = request.requestHeaders.filterIndexed { i,_ -> i != index }
        return request.copy(requestHeaders=listOf(HttpHeader("Cookie", header.latin1())) + remaining) to updated
    }
    fun <A> updateCookieJar(response: HttpResponse<A>, request: HttpRequest, now: PandocUtcTime, jar: HttpCookieJar): Pair<HttpCookieJar, HttpResponse<A>> {
        var updated = jar
        for (header in response.headers) if (header.named("Set-Cookie")) updated = receiveSetCookie(WebCookies.parseSetCookie(header.value), request, now, true, updated)
        return updated to response.copy(headers=response.headers.filterNot { it.named("Set-Cookie") })
    }
    private fun compareTime(a: PandocUtcTime, b: PandocUtcTime) = a.day.compareTo(b.day).takeIf { it != 0 } ?: a.dayPicoseconds.compareTo(b.dayPicoseconds)
    private fun addTime(now: PandocUtcTime, picos: BigInteger) = PandocUtcTime.fromPosixPicoseconds(now.posixPicoseconds() + picos)
    private fun fold(bytes: HttpBytes) = bytes.latin1().map { it.lowercaseChar() }.joinToString("")
}
