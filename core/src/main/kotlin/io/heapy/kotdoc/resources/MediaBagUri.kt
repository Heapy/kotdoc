// network-uri 2.6.4.2 Network.URI absolute URI grammar used by Text.Pandoc.MediaBag.
// Original BSD-3-Clause source/license retained under vendor/ and licenses/.
package io.heapy.kotdoc.resources

import io.heapy.kotdoc.uri.NetworkURI
import io.heapy.kotdoc.layout.CharacterStringChars as Chars

/** MediaBag projection of the complete pinned Network.URI parser. */
internal object MediaBagUri {
    data class Parsed(val scheme: String, val path: String)
    private val schemes = checkNotNull(javaClass.getResource("/io/heapy/kotdoc/uri-schemes.txt")).readText().lines().toSet()
    fun parse(s: String): Parsed? = NetworkURI.parseURI(s)?.let {
        Parsed(Chars.build(NetworkURI.scheme(it)), Chars.build(it.path))
    }
    fun parseReference(s: String): Parsed? = NetworkURI.parseURIReference(s)?.let {
        Parsed(Chars.build(NetworkURI.scheme(it)), Chars.build(it.path))
    }
    fun isKnown(s: String): Boolean {
        val escaped=buildString {
            s.codePoints().forEach { cp ->
                if(cp<128) appendCodePoint(cp) else for(b in String(Character.toChars(cp)).toByteArray(Charsets.UTF_8))
                    append('%').append((b.toInt() and 255).toString(16).uppercase().padStart(2,'0'))
            }
        }
        return parse(escaped)?.scheme?.lowercase() in schemes
    }
}
