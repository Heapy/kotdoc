// Network.URI 2.6.4.2, Copyright (c) 2004 Graham Klyne.
// BSD-3-Clause; licenses/Network-uri-Graham-Klyne-BSD-3-Clause.txt.
package io.heapy.kotdoc.uri

import io.heapy.kotdoc.formats.HaskellString
import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars as Chars

/** A source URI value may contain arbitrary components, independently of parsing.
 * Haskell String fields retain raw surrogate Chars and original Char ordering. */
data class URIAuth(val userInfo: CharacterString, val regName: CharacterString, val port: CharacterString) : Comparable<URIAuth> {
    constructor(userInfo: String = "", regName: String = "", port: String = "") : this(Chars.fromString(userInfo), Chars.fromString(regName), Chars.fromString(port))
    override fun compareTo(other: URIAuth): Int = userInfo.compareTo(other.userInfo).takeIf { it != 0 }
        ?: regName.compareTo(other.regName).takeIf { it != 0 } ?: port.compareTo(other.port)
    fun show(precedence: Long = 0): String {
        fun q(s: CharacterString) = HaskellString.quoteCodePoints(s.points())
        val body = "URIAuth {uriUserInfo = ${q(userInfo)}, uriRegName = ${q(regName)}, uriPort = ${q(port)}}"
        return if (precedence > 10) "($body)" else body
    }
    override fun toString() = show()
    fun rectify(): URIAuth = copy(userInfo = userInfo.unlessEmpty { it.ensureSuffix("@") }, port = port.unlessEmpty { it.ensurePrefix(":") })
    companion object { val empty = URIAuth() }
}

data class URI(val scheme: CharacterString, val authority: URIAuth?, val path: CharacterString, val query: CharacterString, val fragment: CharacterString) : Comparable<URI> {
    constructor(scheme: String = "", authority: URIAuth? = null, path: String = "", query: String = "", fragment: String = "") :
        this(Chars.fromString(scheme), authority, Chars.fromString(path), Chars.fromString(query), Chars.fromString(fragment))
    override fun compareTo(other: URI): Int = scheme.compareTo(other.scheme).takeIf { it != 0 }
        ?: (when { authority == null -> if (other.authority == null) 0 else -1; other.authority == null -> 1; else -> authority.compareTo(other.authority) }).takeIf { it != 0 }
        ?: path.compareTo(other.path).takeIf { it != 0 } ?: query.compareTo(other.query).takeIf { it != 0 } ?: fragment.compareTo(other.fragment)
    fun isAbsolute() = scheme.points().isNotEmpty()
    fun isRelative() = !isAbsolute()
    fun rectify(): URI = copy(scheme = scheme.ensureSuffix(":"), authority = authority?.rectify(),
        query = query.unlessEmpty { it.ensurePrefix("?") }, fragment = fragment.unlessEmpty { it.ensurePrefix("#") })
    fun uriToString(userInfoMap: (CharacterString) -> CharacterString, suffix: CharacterString = Chars.empty): CharacterString =
        Chars.concat(listOf(scheme, uriAuthToString(userInfoMap, authority), path, query, fragment, suffix))
    /** Show ignores precedence and masks the password, as in the original. */
    fun showCharacters(precedence: Long = 0): CharacterString {
        @Suppress("UNUSED_VARIABLE") val ignored = precedence
        return uriToString(userInfoMap = { raw ->
            val points = raw.points(); val colon = points.indexOf(58)
            if (colon < 0) raw else {
                val pass = points.copyOfRange(colon, points.size)
                if (pass.contentEquals(intArrayOf(64)) || pass.contentEquals(intArrayOf(58, 64))) raw
                else CharacterString(points.copyOfRange(0, colon) + ":...@".codePoints().toArray())
            }
        })
    }
    override fun toString() = Chars.build(showCharacters())
    companion object { val empty = URI() }
}

fun uriAuthToString(userInfoMap: (CharacterString) -> CharacterString, authority: URIAuth?, suffix: CharacterString = Chars.empty): CharacterString {
    if (authority == null) return suffix
    val user = if (authority.userInfo.points().isEmpty()) Chars.empty else userInfoMap(authority.userInfo)
    return Chars.concat(listOf(Chars.fromString("//"), user, authority.regName, authority.port, suffix))
}
private fun CharacterString.unlessEmpty(update: (CharacterString) -> CharacterString) = if (points().isEmpty()) this else update(this)
private fun CharacterString.ensurePrefix(prefix: String): CharacterString {
    val p = prefix.codePoints().toArray(); val s = points()
    return if (s.size >= p.size && p.indices.all { s[it] == p[it] }) this else CharacterString(p + s)
}
private fun CharacterString.ensureSuffix(suffix: String): CharacterString {
    val p = suffix.codePoints().toArray(); val s = points()
    return if (s.size >= p.size && p.indices.all { s[s.size - p.size + it] == p[it] }) this else CharacterString(s + p)
}
