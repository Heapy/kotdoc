// Text.Pandoc.URI, Copyright (C) 2006-2024 John MacFarlane.
// GPL-2.0-or-later; upstream source and notice retained under upstream/ and licenses/.
package io.heapy.kotdoc.uri

import io.heapy.kotdoc.layout.CharacterStringChars as Chars
import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.parsing.Deferred
import io.heapy.kotdoc.parsing.ParserCharacters
import io.heapy.kotdoc.parsing.attoparsec.AttoResult
import io.heapy.kotdoc.parsing.attoparsec.TextParser
import io.heapy.kotdoc.resources.ResourceUri
import java.util.Collections
import java.util.Locale

/** All six exports of the pinned Text.Pandoc.URI module. Text inputs replace
 * unpaired UTF-16 surrogates at the same boundary as Haskell Text.pack. */
object PandocURI {
    val schemes: Set<String> = Collections.unmodifiableSortedSet(
        checkNotNull(javaClass.getResource("/io/heapy/kotdoc/uri-schemes.txt"))
            .readText().lineSequence().filter(String::isNotEmpty).toSortedSet())

    private fun text(s: String) = TextChars.fromPoints(s.codePoints().toArray())
    fun urlEncode(s: String): String = Chars.build(NetworkURI.escapeURIString(
        NetworkURI::isUnreserved, Chars.fromString(text(s))))
    fun escapeURI(s: String): String = Chars.build(NetworkURI.escapeURIString(
        { !ParserCharacters.isSpace(it) && it !in escapedPunctuation }, Chars.fromString(text(s))))
    private val escapedPunctuation = "<>|\"{}[]^`".codePoints().toArray().toSet()

    fun isURI(s: String): Boolean {
        val normalized = text(s)
        // The source discards the pair without demanding its decoder or MIME.
        if (pBase64DataURI.before(TextParser.endOfInput()).parseComplete(normalized) is AttoResult.Done) return true
        val escaped = NetworkURI.escapeURIString({ it < 128 }, Chars.fromString(normalized))
        val uri = NetworkURI.parseURI(escaped) ?: return false
        return Chars.build(NetworkURI.scheme(uri)).filter { it != ':' }.lowercase(Locale.ROOT) in schemes
    }

    /** Explicit platform argument also permits testing the original CPP branch. */
    fun uriPathToPath(s: String, windows: Boolean = System.getProperty("os.name").startsWith("Windows")): String {
        val path = text(s)
        return if (windows && path.startsWith('/')) path.drop(1) else path
    }

    /** Both tuple fields remain independently lazy, like the original pair. */
    class Base64Data internal constructor(val contents: Deferred<ByteArray>, val mime: Deferred<String>)

    private fun alphaNum(c: Int) = c in 65..90 || c in 97..122 || c in 48..57
    private fun hex(c: Int) = c in 48..57 || c in 65..70 || c in 97..102
    private fun digit(c: Int) = when(c) { in 48..57 -> c-48; in 65..70 -> c-55; else -> c-87 }
    private val restrictedName = TextParser.satisfy(::alphaNum).flatMapDeferred { c ->
        TextParser.takeWhile { alphaNum(it) || it in "!#$&^_.+-".codePoints().toArray() }.mapDeferred { rest ->
            c.flatMapValue { first -> rest.mapValue { String(Character.toChars(first)) + it } }
        }
    }
    private val mediaParam = TextParser.char(';').then(TextParser.skipWhile(ParserCharacters::isSpace))
        .then(restrictedName).flatMapDeferred { key ->
            TextParser.char('=').then(TextParser.takeWhile { it != ';'.code }).mapDeferred { value ->
                key.flatMapValue { k -> value.mapValue { ";$k=$it" } }
            }
        }
    private val mime = restrictedName.flatMapDeferred { first ->
        TextParser.char('/').then(restrictedName).flatMapDeferred { second ->
            mediaParam.many().mapDeferred { parameters -> first.flatMapValue { a -> second.flatMapValue { b ->
                parameters.mapValue { ps -> "$a/$b" + ps.joinToString("") }
            } } }
        }
    }
    private val percentOctet = TextParser.char('%').then(TextParser.satisfy(::hex)).flatMap { x ->
        TextParser.satisfy(::hex).map { y -> (digit(x)*16+digit(y)).toChar().toString() }
    }
    private val encoded = (TextParser.takeWhile1 {
        alphaNum(it) || it == '/'.code || it == '+'.code || it == 32 || it == 9 || it == 13 || it == 10
    } or percentOctet).many().map { it.joinToString("") }

    /** Prefix parser; callers requiring a whole URI must append endOfInput. */
    val pBase64DataURI: TextParser<Base64Data> = TextParser.asciiCI("data:").then(mime).flatMapDeferred { type ->
        TextParser.string(";base64,").then(encoded).flatMapDeferred { payload ->
            TextParser.skipWhile { it == '='.code }.mapDeferred {
                Deferred.now(Base64Data(payload.mapValue { ResourceUri.decodeBase64Lenient(it.toByteArray(Charsets.UTF_8)) }, type))
            }
        }
    }
}
