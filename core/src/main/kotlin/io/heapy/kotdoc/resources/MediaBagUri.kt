// network-uri 2.6.4.2 Network.URI absolute URI grammar used by Text.Pandoc.MediaBag.
// Original BSD-3-Clause source/license retained under vendor/ and licenses/.
package io.heapy.kotdoc.resources

import io.heapy.kotdoc.uri.NetworkURI
import io.heapy.kotdoc.layout.CharacterStringChars as Chars

/** MediaBag projection of the complete pinned Network.URI parser. */
internal object MediaBagUri {
    data class Parsed(val scheme: String, val path: String)
    fun parse(s: String): Parsed? = NetworkURI.parseURI(s)?.let {
        Parsed(Chars.build(NetworkURI.scheme(it)), Chars.build(it.path))
    }
    fun parseReference(s: String): Parsed? = NetworkURI.parseURIReference(s)?.let {
        Parsed(Chars.build(NetworkURI.scheme(it)), Chars.build(it.path))
    }
    fun isKnown(s: String): Boolean = io.heapy.kotdoc.uri.PandocURI.isURI(s)
}
