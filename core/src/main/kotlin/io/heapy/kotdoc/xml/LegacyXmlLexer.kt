// Text.XML.Light.Lexer, xml 1.3.14, (c) 2007 Galois Inc.; BSD-3-Clause.
// Original module and full notice retained in vendor/xml-1.3.14 and licenses/.
package io.heapy.kotdoc.xml

import io.heapy.kotdoc.formats.HaskellNumber
import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars
import io.heapy.kotdoc.parsing.ParserCharacters
import java.math.BigInteger

data class LegacyXmlLChar(val line: BigInteger, val character: Int)
sealed interface LegacyXmlToken {
    class Start(val line: BigInteger, val name: LegacyXmlName, attributes: List<LegacyXmlAttribute>, val empty: Boolean) : LegacyXmlToken {
        val attributes = legacyOwned(attributes)
    }
    class End(val line: BigInteger, val name: LegacyXmlName) : LegacyXmlToken
    class Ref(val reference: CharacterString) : LegacyXmlToken
    class Text(val data: LegacyXmlText) : LegacyXmlToken
}
sealed interface LegacyXmlTextBit {
    class Text(val text: CharacterString) : LegacyXmlTextBit
    class Ref(val reference: CharacterString) : LegacyXmlTextBit
}
class LegacyXmlScan<A>(val value: A, rest: List<LegacyXmlLChar>) { val rest = legacyOwned(rest) }
class LegacyXmlAttributes(attributes: List<LegacyXmlAttribute>, val empty: Boolean, tokens: List<LegacyXmlToken>) {
    val attributes = legacyOwned(attributes); val tokens = legacyOwned(tokens)
}

/** Polymorphic XmlSource/customScanner. Each next observation stays deferred. */
class LegacyXmlScanner<S> internal constructor(private val current: Lazy<Pair<Int, S>?>, private val next: (S) -> Pair<Int, S>?) {
    fun uncons(): Pair<Int, LegacyXmlScanner<S>>? = current.value?.let { (c, rest) ->
        c to LegacyXmlScanner(lazy { next(rest) }, next)
    }
}

object LegacyXmlLexer {
    fun <S> customScanner(next: (S) -> Pair<Int, S>?, source: S) = LegacyXmlScanner(lazy { next(source) }, next)
    fun <S> lineNumber(first: BigInteger, source: S, next: (S) -> Pair<Int, S>?): List<LegacyXmlLChar> {
        val out = mutableListOf<LegacyXmlLChar>(); var line = first; var state = source; var pending = next(state)
        while (pending != null) {
            val (c, rest) = pending; state = rest
            if (c == 13) {
                pending = next(state)
                if (pending?.first == 10) { state = pending.second; pending = next(state) }
            } else pending = next(state)
            out.add(LegacyXmlLChar(line, if (c == 13) 10 else c))
            if (c == 13 || c == 10) line += BigInteger.ONE
        }
        return legacyOwned(out)
    }
    fun lineNumber(first: BigInteger, source: CharacterString): List<LegacyXmlLChar> {
        val points = source.points()
        return lineNumber(first, 0) { i -> if (i < points.size) points[i] to (i + 1) else null }
    }
    fun tokens(source: CharacterString) = tokensFromLines(lineNumber(BigInteger.ONE, source))
    fun <S> tokens(source: S, next: (S) -> Pair<Int, S>?) = tokensFromLines(lineNumber(BigInteger.ONE, source, next))
    fun tokens(source: String) = tokens(legacyChars(source))
    /** ByteString XmlSource maps octets to Char; it does not decode UTF-8. */
    fun tokens(source: ByteArray) = tokens(CharacterStringChars.fromPoints(source.map { it.toInt() and 255 }.toIntArray()))
    fun tokensFromLines(source: List<LegacyXmlLChar>): List<LegacyXmlToken> = Cursor(source).tokens()
    fun qualName(source: List<LegacyXmlLChar>): LegacyXmlScan<LegacyXmlName> = Cursor(source).let { LegacyXmlScan(it.name(), it.rest()) }
    fun attrib(source: List<LegacyXmlLChar>): LegacyXmlScan<LegacyXmlAttribute> = Cursor(source).let { LegacyXmlScan(it.attribute(), it.rest()) }
    fun attrValue(source: List<LegacyXmlLChar>): LegacyXmlScan<CharacterString> = Cursor(source).let { LegacyXmlScan(it.attrValue(), it.rest()) }
    fun string(source: List<LegacyXmlLChar>): LegacyXmlScan<CharacterString> = Cursor(source).let { LegacyXmlScan(it.string(), it.rest()) }
    fun attribs(source: List<LegacyXmlLChar>): LegacyXmlAttributes = Cursor(source).let { c ->
        val (attrs, empty) = c.attributes(); LegacyXmlAttributes(attrs, empty, c.tokens())
    }
    fun tag(source: List<LegacyXmlLChar>): List<LegacyXmlToken> = Cursor(source).let { c -> c.tag() + c.tokens() }
    fun special(bang: LegacyXmlLChar, source: List<LegacyXmlLChar>): List<LegacyXmlToken> = Cursor(source).let { c -> c.special(bang) + c.tokens() }
    fun dropSpace(source: List<LegacyXmlLChar>) = legacyOwned(source.dropWhile { ParserCharacters.isSpace(it.character) })
    fun breakn(predicate: (Int) -> Boolean, source: List<LegacyXmlLChar>): LegacyXmlScan<CharacterString> = Cursor(source).let { LegacyXmlScan(it.takeWhile { c -> !predicate(c) }, it.rest()) }
    fun breakAfter(predicate: (Int) -> Boolean, source: List<LegacyXmlLChar>): LegacyXmlScan<CharacterString> = Cursor(source).let { c ->
        val s = c.takeWhile { !predicate(it) }; if (!c.done) c.at++; LegacyXmlScan(s, c.rest())
    }
    fun decodeText(source: CharacterString): List<LegacyXmlTextBit> {
        val p = source.points(); var at = 0; val out = mutableListOf<LegacyXmlTextBit>()
        fun part(a: Int, b: Int) = CharacterStringChars.fromPoints(p.copyOfRange(a, b))
        while (at < p.size) {
            val start = at
            if (p[at] == 38) {
                var end = at + 1; while (end < p.size && p[end] != 59) end++
                if (end == p.size) { out.add(LegacyXmlTextBit.Text(part(at, end))); break }
                out.add(LegacyXmlTextBit.Ref(part(at + 1, end))); at = end + 1
            } else {
                while (at < p.size && p[at] != 38) at++
                out.add(LegacyXmlTextBit.Text(part(start, at)))
            }
        }
        return legacyOwned(out)
    }
    fun decodeAttribute(source: CharacterString): CharacterString = CharacterStringChars.concat(decodeText(source).map { bit -> when (bit) {
        is LegacyXmlTextBit.Text -> bit.text
        is LegacyXmlTextBit.Ref -> referenceToChar(bit.reference)?.let { CharacterStringChars.fromPoints(intArrayOf(it)) }
            ?: CharacterStringChars.concat(listOf(legacyChars("&"), bit.reference, legacyChars(";")))
    } })
    fun referenceToChar(source: CharacterString): Int? {
        val p = source.points()
        if (p.firstOrNull() == 35) return numericEscape(CharacterStringChars.fromPoints(p.drop(1).toIntArray()))
        return when (source) { legacyChars("lt") -> 60; legacyChars("gt") -> 62; legacyChars("amp") -> 38; legacyChars("apos") -> 39; legacyChars("quot") -> 34; else -> null }
    }
    fun numericEscape(source: CharacterString): Int? {
        val p = source.points()
        val n = if (p.firstOrNull() == 120) {
            val digits = p.drop(1)
            if (digits.isEmpty() || digits.any { it !in 48..57 && it !in 65..70 && it !in 97..102 }) return null
            BigInteger(digits.map { it.toChar() }.joinToString(""), 16).toLong()
        } else {
            // reads leaves trailing whitespace in the remainder; the original
            // accepts only [(n, "")], unlike readMaybe's trailing-space rule.
            if (p.lastOrNull()?.let(ParserCharacters::isSpace) == true) return null
            HaskellNumber.readInteger(CharacterStringChars.build(source))?.toLong() ?: return null
        }
        return charFromInt(n)
    }
    fun charFromInt(value: Long): Int? = if (value in 0..0x10ffff) value.toInt() else null

    private class Cursor(val source: List<LegacyXmlLChar>, var at: Int = 0) {
        val done get() = at == source.size
        fun rest() = source.subList(at, source.size)
        fun point() = source.getOrNull(at)?.character
        fun starts(s: String) = s.indices.all { source.getOrNull(at + it)?.character == s[it].code }
        fun dropSpace() { while (!done && ParserCharacters.isSpace(source[at].character)) at++ }
        fun takeWhile(predicate: (Int) -> Boolean): CharacterString {
            val start = at; while (!done && predicate(source[at].character)) at++
            return CharacterStringChars.fromPoints(IntArray(at - start) { source[start + it].character })
        }
        fun name(): LegacyXmlName {
            val s = takeWhile { !ParserCharacters.isSpace(it) && it !in listOf(61, 62, 47) }.points()
            val colon = s.indexOf(58)
            return if (colon < 0) LegacyXmlName(CharacterStringChars.fromPoints(s)) else LegacyXmlName(
                CharacterStringChars.fromPoints(s.copyOfRange(colon + 1, s.size)), prefix = CharacterStringChars.fromPoints(s.copyOfRange(0, colon)))
        }
        fun string(): CharacterString {
            val quote = point()
            if (quote == 34 || quote == 39) { at++; val text = takeWhile { it != quote }; if (!done) at++; return text }
            return takeWhile { !ParserCharacters.isSpace(it) && it != 62 && it != 47 }
        }
        fun attrValue(): CharacterString = if (point() == 61) { at++; dropSpace(); string() } else CharacterStringChars.empty
        fun attribute(): LegacyXmlAttribute {
            val key = name(); dropSpace(); val value = attrValue(); dropSpace()
            return LegacyXmlAttribute(key, decodeAttribute(value))
        }
        fun attributes(): Pair<List<LegacyXmlAttribute>, Boolean> {
            val attrs = mutableListOf<LegacyXmlAttribute>()
            while (!done) when {
                point() == 62 -> { at++; return attrs to false }
                point() == 47 -> { at++; if (point() == 62) at++; return attrs to true }
                starts("?>") -> { at += 2; return attrs to true }
                else -> attrs.add(attribute())
            }
            return attrs to false
        }
        fun tag(): List<LegacyXmlToken> {
            if (done) return emptyList()
            if (point() == 47) {
                val line = source[at++].line; dropSpace(); val name = name(); val saved = at; dropSpace()
                if (point() == 62) at++ else at = saved
                return listOf(LegacyXmlToken.End(line, name))
            }
            val line = source[at].line; val name = name(); dropSpace(); val (attrs, empty) = attributes()
            return listOf(LegacyXmlToken.Start(line, name, attrs, empty))
        }
        fun special(bang: LegacyXmlLChar): List<LegacyXmlToken> {
            if (starts("--")) {
                at += 2; while (!done && !starts("-->")) at++
                if (!done) at += 3
                return emptyList()
            }
            if (starts("[CDATA[")) {
                at += 7; val data = takeWhile { !starts("]]>" ) }
                if (!done) at += 3
                return listOf(LegacyXmlToken.Text(LegacyXmlText(data, LegacyXmlTextKind.CDataVerbatim, bang.line)))
            }
            val points = mutableListOf(60, 33); var nesting = 0
            while (!done) {
                val c = source[at++].character; points.add(c)
                if (c == 62) { if (nesting == 0) break else nesting-- } else if (c == 60) nesting++
            }
            return listOf(LegacyXmlToken.Text(LegacyXmlText(CharacterStringChars.fromPoints(points.toIntArray()), LegacyXmlTextKind.CDataRaw, bang.line)))
        }
        fun tokens(): List<LegacyXmlToken> {
            val out = mutableListOf<LegacyXmlToken>()
            while (!done) {
                if (point() == 60) {
                    at++
                    if (point() == 33) { val bang = source[at++]; out.addAll(special(bang)) }
                    else { dropSpace(); out.addAll(tag()) }
                } else {
                    val line = source[at].line; val raw = takeWhile { it != 60 }
                    for (bit in decodeText(raw)) out.add(when (bit) {
                        is LegacyXmlTextBit.Text -> LegacyXmlToken.Text(LegacyXmlText(bit.text, line = line))
                        is LegacyXmlTextBit.Ref -> referenceToChar(bit.reference)?.let { LegacyXmlToken.Text(LegacyXmlText(CharacterStringChars.fromPoints(intArrayOf(it)), line = line)) } ?: LegacyXmlToken.Ref(bit.reference)
                    })
                }
            }
            return legacyOwned(out)
        }
    }
}
