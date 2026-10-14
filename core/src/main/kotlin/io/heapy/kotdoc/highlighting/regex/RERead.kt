// Skylighting.Regex 0.15 and GHC.Internal.Read/Text.Read.Lex 9.12.2.
// Copyright (c) 2016-2024 John MacFarlane; (c) University of Glasgow.
// BSD-3-Clause; licenses/Skylighting-core-BSD-3-Clause.txt and GHC-base-BSD-3-Clause.txt.
package io.heapy.kotdoc.highlighting.regex

import io.heapy.kotdoc.formats.HaskellString
import io.heapy.kotdoc.parsing.ParserCharacters
import io.heapy.kotdoc.parsing.RegexCharacters

/** The hand-written original Read RE requires exactly this record field order. */
internal object RERead {
    private class NoRead : RuntimeException(null, null, false, false)
    fun read(text: String): RE? = try { Cursor(text).read() } catch (_: NoRead) { null }
    fun readCharacterLiteral(text: String): Int? = try {
        val cursor = Cursor(text)
        cursor.character().also { cursor.spaces(); if (cursor.position != cursor.input.size) cursor.fail() }
    } catch (_: NoRead) { null }
    private class Cursor(text: String) {
        val input = text.codePoints().toArray()
        var position = 0
        fun fail(): Nothing = throw NoRead()
        fun peek() = input.getOrNull(position)
        fun take(): Int = input.getOrNull(position++) ?: fail()
        fun spaces() { while (peek()?.let(ParserCharacters::isSpace) == true) position++ }
        fun consume(char: Char): Boolean { spaces(); return if (peek() == char.code) { position++; true } else false }
        fun punctuation(char: Char) { if (!consume(char)) fail() }
        fun identifier(): String {
            spaces()
            val start = position
            val first = peek() ?: fail()
            if (!ParserCharacters.isAlpha(first) && first != '_'.code) fail()
            position++
            while (peek()?.let { ParserCharacters.isAlphaNum(it) || it == '_'.code || it == '\''.code } == true) position++
            return buildString { for (i in start..<position) appendCodePoint(input[i]) }
        }
        fun name(name: String) { if (identifier() != name) fail() }
        fun symbol(point: Int) = point !in ",;()[]{}`'\"_".map { it.code } &&
            (RegexCharacters.isSymbol(point) || RegexCharacters.category(point) in listOf(12, 17, 11))
        fun equalsSign() {
            spaces(); if (take() != '='.code || peek()?.let(::symbol) == true) fail()
        }
        fun <A> parens(body: () -> A): A {
            var opened = 0
            while (consume('(')) opened++
            val value = body()
            repeat(opened) { punctuation(')') }
            return value
        }
        fun bool(): Boolean = parens { when (identifier()) { "True" -> true; "False" -> false; else -> fail() } }
        fun read(): RE = parens {
            name("RE"); punctuation('{'); name("reString"); equalsSign(); val bytes = string()
            punctuation(','); name("reCaseSensitive"); equalsSign(); val sensitive = bool()
            punctuation(','); name("reMinimal"); equalsSign(); val minimal = bool(); punctuation('}')
            RE(bytes.map { it.toByte() }.toByteArray(), sensitive, minimal)
        }.also { spaces(); if (position != input.size) fail() }

        fun string(): List<Int> = parens {
            spaces()
            if (peek() == '['.code) {
                position++; val chars = mutableListOf<Int>()
                if (!consume(']')) {
                    do { chars.add(character()) } while (consume(','))
                    punctuation(']')
                }
                chars
            } else {
                if (take() != '"'.code) fail()
                val chars = mutableListOf<Int>()
                while (true) {
                    if (peek() == '"'.code) { position++; break }
                    if (peek() == '\\'.code) {
                        val next = input.getOrNull(position + 1)
                        if (next == '&'.code) { position += 2; continue }
                        if (next?.let(ParserCharacters::isSpace) == true) {
                            position += 2; spaces(); if (take() != '\\'.code) fail(); continue
                        }
                    }
                    chars.add(literal())
                }
                chars
            }
        }
        fun character(): Int = parens {
            punctuation('\'')
            if (peek() == '\''.code) fail()
            val value = literal()
            if (take() != '\''.code) fail()
            value
        }
        fun literal(): Int {
            val c = take(); if (c != '\\'.code) return c
            val escaped = take()
            if (escaped > 127) fail()
            when (escaped.toChar()) {
                'a' -> return 7; 'b' -> return 8; 'f' -> return 12; 'n' -> return 10
                'r' -> return 13; 't' -> return 9; 'v' -> return 11
                '\\', '"', '\'' -> return escaped
                '^' -> { val control = take(); if (control !in 64..95) fail(); return control - 64 }
            }
            if (escaped in 48..57 || escaped in listOf('o'.code, 'O'.code, 'x'.code, 'X'.code)) {
                val radix = when (escaped.toChar()) { 'o', 'O' -> 8; 'x', 'X' -> 16; else -> 10 }
                if (radix == 10) position--
                val start = position; var number = 0
                while (true) {
                    val digit = when (val point = peek()) {
                        in 48..57 -> point!! - 48; in 65..70 -> point!! - 65 + 10; in 97..102 -> point!! - 97 + 10
                        else -> -1
                    }
                    if (digit !in 0..<radix) break
                    position++; number = (number.toLong() * radix + digit).coerceAtMost(0x110000).toInt()
                }
                if (position == start || number > 0x10ffff) fail()
                return number
            }
            position--
            val names = HaskellString.controlNames + listOf("SP", "DEL")
            val name = names.sortedByDescending { it.length }.firstOrNull { name ->
                name.indices.all { input.getOrNull(position + it) == name[it].code }
            } ?: fail()
            position += name.length
            return when (name) { "SP" -> 32; "DEL" -> 127; else -> names.indexOf(name) }
        }
    }
}
