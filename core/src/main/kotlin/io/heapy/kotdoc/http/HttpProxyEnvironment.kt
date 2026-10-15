// Network.HTTP.Proxy 0.7.19, original authors and full BSD-3-Clause notice:
// licenses/http-proxy-BSD-3-Clause.txt. This file ports the common environment
// branch; the Windows registry branch remains pending.
package io.heapy.kotdoc.http

import io.heapy.kotdoc.parsing.RegexCharacters
import io.heapy.kotdoc.parsing.Text212Lowercase
import io.heapy.kotdoc.uri.NetworkURI
import io.heapy.kotdoc.layout.CharacterStringChars as Chars

enum class HttpProxyProtocol {
    HTTPProxy, HTTPSProxy;
    fun show() = if (this == HTTPProxy) "http" else "https"
    override fun toString() = show()
}

sealed interface HttpEnvHelper {
    data object FromRequest : HttpEnvHelper
    data object NoProxy : HttpEnvHelper
    data class UseProxy(val proxy: HttpProxy) : HttpEnvHelper
}

class HttpProxySettings internal constructor(val proxy: HttpProxy, private val credentials: Lazy<Pair<HttpBytes, HttpBytes>?>) {
    constructor(proxy: HttpProxy, auth: Pair<HttpBytes, HttpBytes>? = null) : this(proxy, lazyOf(auth))
    val auth get() = credentials.value
    fun show(precedence: Int = 0): String {
        val userPass = auth?.let { "Just (${it.first.show()},${it.second.show()})" } ?: "Nothing"
        val result = "ProxySettings {_proxyHost = ${proxy.show()}, _proxyAuth = $userPass}"
        return if (precedence > 10) "($result)" else result
    }
}

object HttpProxyEnvironment {
    fun httpProtocol(secure: Boolean) = if (secure) HttpProxyProtocol.HTTPSProxy else HttpProxyProtocol.HTTPProxy

    /** Snapshot once, including entry order and duplicate names at the effect boundary. */
    fun systemEnvironment(): List<Pair<String, String>> = System.getenv().entries.map { it.key to it.value }

    /** The original non-Windows systemProxyHelper branch. Registry selection is separate. */
    fun environmentProxyHelper(
        overrideName: String?, protocol: HttpProxyProtocol, fallback: HttpEnvHelper,
        environment: () -> List<Pair<String, String>> = ::systemEnvironment,
    ): (HttpRequest) -> HttpRequest {
        val modifier = envHelper(overrideName ?: "${protocol.show()}_proxy", environment)
        return { request ->
            val settings = modifier(request.host)
            if (settings == null) when (fallback) {
                HttpEnvHelper.FromRequest -> request
                HttpEnvHelper.NoProxy -> request.copy(proxy = null)
                is HttpEnvHelper.UseProxy -> request.copy(proxy = fallback.proxy)
            } else {
                val updated = request.copy(proxy = settings.proxy)
                settings.auth?.let { HttpRequests.applyBasicProxyAuth(it.first, it.second, updated) } ?: updated
            }
        }
    }

    fun envHelper(
        name: String, environment: () -> List<Pair<String, String>> = ::systemEnvironment,
    ): (HttpBytes) -> HttpProxySettings? {
        val textName = packText(name)
        val entries = environment().toList()
        val folded by lazy { entries.associate { Text212Lowercase.convert(packText(it.first)) to it.second } }
        fun lookup(key: String): String? = entries.firstOrNull { it.first == key }?.second ?: folded[key]
        val value = lookup(textName)
        if (value == null || value.isEmpty()) return { null }
        fun invalid(): Nothing = throw HttpContentException(HttpFailureContent.InvalidProxyEnvironmentVariable(textName, packText(value)))
        val uri = NetworkURI.parseURI(value)?.takeIf { Chars.build(it.scheme) == "http:" }
            ?: NetworkURI.parseURI("http://$value") ?: invalid()
        if (Chars.build(uri.scheme) != "http:" || Chars.build(uri.path) !in listOf("", "/") ||
            uri.query.points().isNotEmpty() || uri.fragment.points().isNotEmpty()) invalid()
        val authority = uri.authority ?: invalid()
        val portText = Chars.build(authority.port)
        val port = if (portText.isEmpty()) 80L else {
            if (!portText.startsWith(':') || portText.length == 1) invalid()
            var result = 0L
            for (c in portText.substring(1)) {
                if (c !in '0'..'9') invalid()
                result = result * 10 + (c - '0') // Data.Text.Read.decimal Int wraps at machine width.
            }
            result
        }
        val settings = HttpProxySettings(HttpProxy(HttpBytes.pack(authority.regName), port), lazy { HttpRequests.extractBasicAuthInfo(uri) })
        // Keep each suffix deferred independently. Original `any` can stop before
        // a later all-space entry raises ByteString.head on an empty ByteString.
        val suffixes by lazy {
            val raw = lookup("no_proxy") ?: ""
            val lower = raw.codePoints().map(RegexCharacters::toLower).toArray()
            HttpBytes(lower.map { it.toByte() }.toByteArray()).latin1().split(',')
                .filter { it.isNotEmpty() }.map { suffix -> lazy { prefixed(suffix.dropWhile { it == ' ' }) } }
        }
        return { host ->
            val normalizedHost by lazy { prefixed(host.bytes().map { (RegexCharacters.toLower(it.toInt() and 255) and 255).toChar() }.joinToString("")) }
            if (suffixes.any { normalizedHost.endsWith(it.value) }) null else settings
        }
    }

    private fun prefixed(value: String): String {
        if (value.isEmpty()) throw CookieBytesException("head")
        return if (value[0] == '.') value else ".$value"
    }

    /** Data.Text.pack replaces surrogate Chars; environment values remain String until demanded. */
    private fun packText(value: String): String = buildString {
        value.codePoints().forEach { appendCodePoint(if (it in 0xd800..0xdfff) 0xfffd else it) }
    }
}
