// Network.HTTP.Proxy 0.7.19 Windows branch, full original authors/notice in
// licenses/http-proxy-BSD-3-Clause.txt. Registry binding reference: Win32 2.14.2.1,
// licenses/Win32-BSD-3-Clause.txt. Safe.readDef: safe 0.3.21, licenses/safe-BSD-3-Clause.txt.
package io.heapy.kotdoc.http

import io.heapy.kotdoc.formats.HaskellNumber
import io.heapy.kotdoc.formats.HaskellString
import io.heapy.kotdoc.uri.NetworkURI
import io.heapy.kotdoc.uri.URI
import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars as Chars
import java.io.IOException

/** Original Win32 effect boundary: queryValue reads a subkey's default value,
 * NOT a named value on the open key. queryDword uses a four-byte named value. */
fun interface HttpProxyRegistry {
    fun openCurrentUser(path: String): HttpProxyRegistryKey
}
interface HttpProxyRegistryKey {
    fun queryDword(name: String): UInt?
    fun queryValue(subkey: String?): String
    fun close()
}

object HttpWindowsProxy {
    const val registryPath = "Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings"

    fun registryProxyString(registry: HttpProxyRegistry = HttpNativeWindowsRegistry): Pair<String, String>? = try {
        val key = registry.openCurrentUser(registryPath)
        try {
            if ((key.queryDword("ProxyEnable") ?: 0u) == 0u) null else {
                val server = key.queryValue("ProxyServer")
                val exceptions = try { key.queryValue("ProxyOverride") } catch (_: IOException) { "" }
                server to exceptions
            }
        } finally { key.close() }
    } catch (_: IOException) { null }

    fun parseWindowsProxy(protocol: HttpProxyProtocol, text: String): String? {
        val prefix = "${protocol.show()}://"
        for (part in text.split(';')) {
            val equal = part.indexOf('=')
            val value = if (equal < 0) part else part.substring(0, equal) + "://" + part.substring(equal + 1)
            if (value.startsWith(prefix)) return value
        }
        return null
    }

    fun parseProxy(protocol: HttpProxyProtocol, text: String): HttpProxySettings? {
        fun parse(value: String): URI? = NetworkURI.parseAbsoluteURI(value)?.takeIf { it.authority != null }
        val uri = parse(text) ?: parse("${protocol.show()}://$text") ?: return null
        if (Chars.build(uri.scheme) != "${protocol.show()}:") return null
        val authority = checkNotNull(uri.authority)
        val portText = Chars.build(authority.port)
        val default = if (protocol == HttpProxyProtocol.HTTPProxy) 80L else 443L
        val port = if (portText.startsWith(':')) HaskellNumber.readInteger(portText.substring(1))?.toLong() ?: default else default
        val userInfo = authority.userInfo.points().let { p ->
            var last = p.size
            while (last > 0 && p[last - 1] == 64) last--
            p.copyOf(last)
        }
        val auth = lazy {
            if (userInfo.isEmpty()) null else {
                val colon = userInfo.indexOf(58)
                val user = if (colon < 0) userInfo else userInfo.copyOf(colon)
                val pass = if (colon < 0) intArrayOf() else userInfo.copyOfRange(colon + 1, userInfo.size)
                HttpBytes.pack(NetworkURI.unEscapeString(CharacterString(user))) to
                    HttpBytes.pack(NetworkURI.unEscapeString(CharacterString(pass)))
            }
        }
        return HttpProxySettings(HttpProxy(HttpBytes.pack(authority.regName), port), auth)
    }

    fun systemProxy(
        protocol: HttpProxyProtocol, registry: HttpProxyRegistry = HttpNativeWindowsRegistry,
    ): (HttpBytes) -> HttpProxySettings? {
        val raw = registryProxyString(registry)
        val selected = raw?.let { parseWindowsProxy(protocol, it.first) }
        if (selected == null) return { null }
        val settings = parseProxy(protocol, selected) ?: throw HttpContentException(
            HttpFailureContent.InvalidProxySettings(
                "Invalid http proxy uri: ${HaskellString.quoteCodePoints(selected.codePoints().toArray())}\n" +
                    "proxy uri must be http with a hostname\n" +
                    "ignoring http proxy, trying a direct connection\n",
            ),
        )
        val exceptions = checkNotNull(raw).second
        val exceptionBytes = HttpBytes.pack(exceptions).latin1()
        return { host ->
            val name = host.latin1()
            if ((name == "127.0.0.1" || name == "localhost") && exceptions.contains("<local>") || exceptionBytes.contains(name)) null
            else settings
        }
    }
}
