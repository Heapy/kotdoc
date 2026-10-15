// Text.Pandoc.Parsing.State, © 2006-2024 John MacFarlane; GPL-2.0-or-later.
// text 2.1.2 lowercase mapping, (c) 2008-2012 Bryan O'Sullivan, Duncan Coutts,
// Tom Harper; (c) 2021 Andrew Lelechenko. BSD-2-Clause.
// GHC derived Read/String lexer, © University of Glasgow 2002; BSD-3-Clause.
package io.heapy.kotdoc.parsing

import io.heapy.kotdoc.ast.UnicodeTextOrder
import io.heapy.kotdoc.formats.HaskellString
import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars
import io.heapy.kotdoc.layout.TextChars
import kotlinx.serialization.json.*

class Key(text: String) : Comparable<Key> {
    val text = TextChars.fromString(text)
    override fun compareTo(other: Key) = UnicodeTextOrder.compare(text, other.text)
    override fun equals(other: Any?) = other is Key && text == other.text
    override fun hashCode() = text.hashCode()
    fun show(precedence: Long = 0): String {
        val body = "Key " + HaskellString.quote(text)
        return if (precedence > 10) "($body)" else body
    }
    override fun toString() = show()
    companion object {
        fun reads(input: CharacterString, precedence: Long = 0): List<Pair<Key, CharacterString>> =
            KeyRead.reads(input, precedence)
        fun reads(input: String, precedence: Long = 0) = reads(CharacterStringChars.fromString(input), precedence)
        fun readsList(input: CharacterString): List<Pair<List<Key>, CharacterString>> = KeyRead.readsList(input)
        fun readsList(input: String) = readsList(CharacterStringChars.fromString(input))
        fun read(input: CharacterString): Key {
            val results = reads(input).filter { it.second.points().all(ParserCharacters::isSpace) }
            require(results.size == 1) { if (results.isEmpty()) "Prelude.read: no parse" else "Prelude.read: ambiguous parse" }
            return results.single().first
        }
        fun read(input: String) = read(CharacterStringChars.fromString(input))
        fun readList(input: CharacterString): List<Key> {
            val results = readsList(input).filter { it.second.points().all(ParserCharacters::isSpace) }
            require(results.size == 1) { if (results.isEmpty()) "Prelude.read: no parse" else "Prelude.read: ambiguous parse" }
            return results.single().first
        }
        fun readList(input: String) = readList(CharacterStringChars.fromString(input))
    }
}

fun toKey(text: String): Key {
    val source = TextChars.fromString(text)
    val unbracket = if (source.length >= 2 && source.first() == '[' && source.last() == ']') source.substring(1, source.length - 1) else source
    val words = mutableListOf<String>()
    var start = 0
    var at = 0
    while (at < unbracket.length) {
        val cp = unbracket.codePointAt(at)
        if (ParserCharacters.isSpace(cp)) {
            if (start < at) words.add(unbracket.substring(start, at))
            at += Character.charCount(cp); start = at
        } else at += Character.charCount(cp)
    }
    if (start < at) words.add(unbracket.substring(start, at))
    return Key(Text212Lowercase.convert(words.joinToString(" ")))
}

/** Cut-down Read lexer: whitespace is skipped, Haskell source comments are not. */
private object KeyRead {
    private class NoRead : RuntimeException(null, null, false, false)
    fun reads(input: CharacterString, precedence: Long): List<Pair<Key, CharacterString>> = try {
        val cursor = Cursor(input.points()); listOf(cursor.key(precedence) to cursor.remaining())
    } catch (_: NoRead) { emptyList() }
    fun readsList(input: CharacterString): List<Pair<List<Key>, CharacterString>> = try {
        val cursor = Cursor(input.points()); listOf(cursor.parens(0) { cursor.list { cursor.key(0) } } to cursor.remaining())
    } catch (_: NoRead) { emptyList() }
    private class Cursor(val points: IntArray) {
        var at = 0
        fun remaining() = CharacterString(points.copyOfRange(at, points.size))
        fun fail(): Nothing = throw NoRead()
        fun peek() = points.getOrNull(at)
        fun take() = points.getOrNull(at++) ?: fail()
        fun spaces() { while (peek()?.let(ParserCharacters::isSpace) == true) at++ }
        fun symbol(cp: Int): Boolean { spaces(); if (peek() != cp) return false; at++; return true }
        fun expect(cp: Int) { if (!symbol(cp)) fail() }
        fun <A> parens(precedence: Long, body: (Long) -> A): A {
            var count = 0
            while (symbol(40)) count++
            val value = body(if (count > 0) 0 else precedence)
            repeat(count) { expect(41) }
            return value
        }
        fun key(precedence: Long): Key = parens(precedence) { p ->
            if (p > 10) fail()
            spaces(); val start = at
            if (peek()?.let { ParserCharacters.isAlpha(it) || it == 95 } != true) fail()
            at++
            while (peek()?.let { ParserCharacters.isAlphaNum(it) || it == 95 || it == 39 } == true) at++
            if (String(points, start, at - start) != "Key") fail()
            Key(string())
        }
        fun <A> list(element: () -> A): List<A> {
            expect(91); val result = mutableListOf<A>()
            if (symbol(93)) return result
            while (true) {
                result.add(element())
                if (symbol(93)) return result
                expect(44)
            }
        }
        fun string(): String = parens(0) {
            spaces()
            val chars = if (peek() == 91) list { character() } else {
                if (take() != 34) fail()
                val result = mutableListOf<Int>()
                while (true) {
                    if (peek() == 34) { at++; break }
                    if (peek() == 92) {
                        val next = points.getOrNull(at + 1)
                        if (next == 38) { at += 2; continue }
                        if (next?.let(ParserCharacters::isSpace) == true) { at += 2; spaces(); if (take() != 92) fail(); continue }
                    }
                    result.add(literal())
                }
                result
            }
            TextChars.fromPoints(chars.toIntArray())
        }
        fun character(): Int = parens(0) {
            expect(39); if (peek() == 39) fail()
            val cp = literal(); if (take() != 39) fail(); cp
        }
        fun literal(): Int {
            val cp = take(); if (cp != 92) return cp
            val escaped = take()
            when (escaped) {
                97 -> return 7; 98 -> return 8; 102 -> return 12; 110 -> return 10
                114 -> return 13; 116 -> return 9; 118 -> return 11
                92, 34, 39 -> return escaped
                94 -> { val control = take(); if (control !in 64..95) fail(); return control - 64 }
            }
            if (escaped in 48..57 || escaped in listOf(111, 79, 120, 88)) {
                val radix = when (escaped) { 111, 79 -> 8; 120, 88 -> 16; else -> 10 }
                if (radix == 10) at--
                val start = at; var value = 0L
                while (true) {
                    val digit = when (val c = peek()) { in 48..57 -> c!! - 48; in 65..70 -> c!! - 55; in 97..102 -> c!! - 87; else -> -1 }
                    if (digit !in 0..<radix) break
                    at++; value = (value * radix + digit).coerceAtMost(0x110000)
                }
                if (at == start || value > 0x10ffff) fail()
                return value.toInt()
            }
            at--
            val names = HaskellString.controlNames + listOf("SP", "DEL")
            val name = names.sortedByDescending(String::length).firstOrNull { n -> n.indices.all { points.getOrNull(at + it) == n[it].code } } ?: fail()
            at += name.length
            return when (name) { "SP" -> 32; "DEL" -> 127; else -> names.indexOf(name) }
        }
    }
}

/** The complete original build uses text 2.1.2. Its lowercase table differs
 * from the text 2.1.3 table retained for earlier independent dependency ports. */
internal object Text212Lowercase {
    private val mapping = Json.parseToJsonElement(checkNotNull(javaClass.getResource("/io/heapy/kotdoc/text-2.1.2-lowercase.json")).readText())
        .jsonObject.getValue("lower").jsonObject.entries.associate { it.key.toInt(16) to it.value.jsonPrimitive.content }
    fun convert(text: String): String = buildString {
        text.codePoints().forEach { cp ->
            val replacement = mapping[cp]
            if (replacement == null) appendCodePoint(cp) else append(replacement)
        }
    }
}
