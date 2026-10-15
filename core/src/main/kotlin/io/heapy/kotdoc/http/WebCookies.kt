// Web.Cookie 0.5.1, Copyright (c) 2010 Michael Snoyman, http://www.yesodweb.com/.
// MIT; full original notice in licenses/cookie-MIT.txt.
package io.heapy.kotdoc.http

import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.parsing.Utf8Text
import io.heapy.kotdoc.resources.PandocUtcTime
import java.io.ByteArrayOutputStream
import java.math.BigInteger

enum class CookieSameSite { Lax, Strict, None }

/** Max-Age remains lazy: the upstream empty-value read error must not hide name/value. */
class HttpSetCookie internal constructor(
    val name: HttpBytes,
    val value: HttpBytes,
    val path: HttpBytes?,
    val expires: PandocUtcTime?,
    private val maxAge: Lazy<BigInteger?>,
    val domain: HttpBytes?,
    val httpOnly: Boolean,
    val secure: Boolean,
    val sameSite: CookieSameSite?,
    val partitioned: Boolean,
) {
    constructor(
        name: HttpBytes = HttpBytes.pack("name"), value: HttpBytes = HttpBytes.pack("value"),
        path: HttpBytes? = null, expires: PandocUtcTime? = null, maxAgePicoseconds: BigInteger? = null,
        domain: HttpBytes? = null, httpOnly: Boolean = false, secure: Boolean = false,
        sameSite: CookieSameSite? = null, partitioned: Boolean = false,
    ) : this(name, value, path, expires, lazyOf(maxAgePicoseconds), domain, httpOnly, secure, sameSite, partitioned)

    val maxAgePicoseconds: BigInteger? get() = maxAge.value

    /** A shared Lazy default preserves record-update demand order; lazyOf replaces Max-Age. */
    fun copy(
        name: HttpBytes = this.name, value: HttpBytes = this.value,
        path: HttpBytes? = this.path, expires: PandocUtcTime? = this.expires,
        maxAge: Lazy<BigInteger?> = this.maxAge, domain: HttpBytes? = this.domain,
        httpOnly: Boolean = this.httpOnly, secure: Boolean = this.secure,
        sameSite: CookieSameSite? = this.sameSite, partitioned: Boolean = this.partitioned,
    ) = HttpSetCookie(name, value, path, expires, maxAge, domain, httpOnly, secure, sameSite, partitioned)

    override fun equals(other: Any?): Boolean = other is HttpSetCookie &&
        name == other.name && value == other.value && path == other.path && expires == other.expires &&
        maxAgePicoseconds == other.maxAgePicoseconds && domain == other.domain && httpOnly == other.httpOnly &&
        secure == other.secure && sameSite == other.sameSite && partitioned == other.partitioned

    override fun hashCode() = listOf(name, value, path, expires, maxAgePicoseconds, domain, httpOnly, secure, sameSite, partitioned).hashCode()

    fun show(): String {
        fun <T> maybe(value: T?, show: (T) -> String) = value?.let { "Just ${show(it)}" } ?: "Nothing"
        fun bool(value: Boolean) = if (value) "True" else "False"
        return "SetCookie {setCookieName = ${name.show()}, setCookieValue = ${value.show()}, " +
            "setCookiePath = ${maybe(path, HttpBytes::show)}, setCookieExpires = ${maybe(expires, CookieDates::showUtc)}, " +
            "setCookieMaxAge = ${maybe(maxAgePicoseconds) { CookieDates.showFixed(it) + "s" }}, " +
            "setCookieDomain = ${maybe(domain, HttpBytes::show)}, setCookieHttpOnly = ${bool(httpOnly)}, " +
            "setCookieSecure = ${bool(secure)}, setCookieSameSite = ${maybe(sameSite) { it.name }}, " +
            "setCookiePartitioned = ${bool(partitioned)}}"
    }
    override fun toString() = show()
}

class CookieReadException : RuntimeException("Prelude.read: no parse")

object WebCookies {
    val defaultSetCookie = HttpSetCookie()
    val def get() = defaultSetCookie
    val sameSiteLax = CookieSameSite.Lax
    val sameSiteStrict = CookieSameSite.Strict
    val sameSiteNone = CookieSameSite.None
    const val expiresFormat = "%a, %d-%b-%Y %X GMT"

    fun parseCookies(input: HttpBytes): List<Pair<HttpBytes, HttpBytes>> {
        val text = input.latin1()
        val result = mutableListOf<Pair<HttpBytes, HttpBytes>>()
        var at = 0
        while (at < text.length) {
            val end = text.indexOf(';', at).takeIf { it >= 0 } ?: text.length
            val (key, value) = pair(text.substring(at, end))
            result.add(HttpBytes.pack(key.dropWhile { it == ' ' }) to HttpBytes.pack(dropQuotes(value)))
            at = end + 1
        }
        return result
    }

    fun parseCookiesText(input: HttpBytes): List<Pair<String, String>> = parseCookies(input).map { (key, value) ->
        Utf8Text.decodeLenient(key.bytes()) to Utf8Text.decodeLenient(value.bytes())
    }

    fun renderCookies(cookies: List<Pair<HttpBytes, HttpBytes>>): HttpBytes {
        val output = ByteArrayOutputStream()
        cookies.forEachIndexed { index, (key, value) ->
            if (index != 0) output.write(';'.code)
            output.writeBytes(key.bytes())
            output.write('='.code)
            output.writeBytes(value.bytes())
        }
        return HttpBytes(output.toByteArray())
    }

    fun renderCookiesBS(cookies: List<Pair<HttpBytes, HttpBytes>>) = renderCookies(cookies)

    fun renderCookiesText(cookies: List<Pair<String, String>>): HttpBytes = renderCookies(cookies.map { (key, value) ->
        HttpBytes(TextChars.fromString(key).toByteArray(Charsets.UTF_8)) to
            HttpBytes(TextChars.fromString(value).toByteArray(Charsets.UTF_8))
    })

    fun parseSetCookie(input: HttpBytes): HttpSetCookie {
        val pairs = input.latin1().split(';').map { pair(it.dropWhile { c -> c == ' ' }) }
        val (name, value) = pairs.first()
        val flags = pairs.drop(1).map { (key, value) -> key.map { it.lowercaseChar() }.joinToString("") to value }
        fun flag(name: String) = flags.firstOrNull { it.first == name }?.second
        return HttpSetCookie(
            HttpBytes.pack(name), HttpBytes.pack(dropQuotes(value)), flag("path")?.let(HttpBytes::pack),
            flag("expires")?.let { parseCookieExpires(HttpBytes.pack(it)) },
            lazy {
                flag("max-age")?.let { text ->
                    if (text.any { it !in '0'..'9' }) null
                    else if (text.isEmpty()) throw CookieReadException()
                    else BigInteger(text) * CookieDates.picosPerSecond
                }
            },
            flag("domain")?.let(HttpBytes::pack), flag("httponly") != null, flag("secure") != null,
            when (flag("samesite")) { "Lax" -> CookieSameSite.Lax; "Strict" -> CookieSameSite.Strict; "None" -> CookieSameSite.None; else -> null },
            flag("partitioned") != null,
        )
    }

    fun renderSetCookie(cookie: HttpSetCookie): HttpBytes {
        val output = ByteArrayOutputStream()
        fun raw(value: HttpBytes) = output.writeBytes(value.bytes())
        fun string(value: String) = raw(HttpBytes.pack(value))
        raw(cookie.name); string("="); raw(cookie.value)
        cookie.path?.let { string("; Path="); raw(it) }
        cookie.expires?.let { string("; Expires="); raw(formatCookieExpires(it)) }
        cookie.maxAgePicoseconds?.let { string("; Max-Age=${CookieDates.floorDiv(it, CookieDates.picosPerSecond)}") }
        cookie.domain?.let { string("; Domain="); raw(it) }
        if (cookie.httpOnly) string("; HttpOnly")
        if (cookie.secure) string("; Secure")
        cookie.sameSite?.let { string("; SameSite=${it.name}") }
        if (cookie.partitioned) string("; Partitioned")
        return HttpBytes(output.toByteArray())
    }

    fun renderSetCookieBS(cookie: HttpSetCookie) = renderSetCookie(cookie)
    fun parseCookieExpires(input: HttpBytes): PandocUtcTime? = CookieDates.parse(input.latin1())
    fun formatCookieExpires(time: PandocUtcTime): HttpBytes = HttpBytes.pack(CookieDates.format(time))

    private fun pair(text: String): Pair<String, String> {
        val equals = text.indexOf('=')
        return if (equals < 0) text to "" else text.substring(0, equals) to text.substring(equals + 1)
    }
    private fun dropQuotes(text: String) = if (text.length >= 2 && text.first() == '"' && text.last() == '"') text.substring(1, text.length - 1) else text
}
