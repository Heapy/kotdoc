// Text.Pandoc.Translations.Types, © 2017–2024 John MacFarlane, GPL-2.0-or-later.
// GHC derived Read/Enum; complete original notices are distributed in licenses/.
package io.heapy.kotdoc.translations

import io.heapy.kotdoc.formats.HaskellString
import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars
import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.parsing.ParserCharacters
import kotlinx.serialization.json.*

enum class Term {
    Abstract, Appendix, Bibliography, Cc, Chapter, Contents, Encl, Figure,
    Glossary, Index, Listing, ListOfFigures, ListOfTables, Page, Part, Preface,
    Proof, References, See, SeeAlso, Table, To;

    fun show(precedence: Long = 0): String {
        @Suppress("UNUSED_VARIABLE") val sourcePrecedence = precedence
        return name
    }
    fun successor(): Term {
        require(this != maximum) { "Enum.succ{Term}: tried to take `succ' of last tag in enumeration" }
        return entries[ordinal + 1]
    }
    fun predecessor(): Term {
        require(this != minimum) { "Enum.pred{Term}: tried to take `pred' of first tag in enumeration" }
        return entries[ordinal - 1]
    }
    /** A zero step repeats forever when the source's range includes this value. */
    fun enumerate(then: Term? = null, to: Term? = null): Sequence<Term> = sequence {
        val step = then?.let { it.ordinal - ordinal } ?: 1
        val limit = to?.ordinal ?: if (step < 0) 0 else maximum.ordinal
        var at = ordinal
        while (if (step < 0) at >= limit else at <= limit) {
            yield(entries[at]); at += step
        }
    }

    companion object {
        val minimum get() = Abstract
        val maximum get() = To
        private val names = entries.associateBy(Term::name)

        fun fromOrdinal(tag: Long): Term {
            require(tag in 0L..maximum.ordinal.toLong()) {
                "Enum.toEnum{Term}: tag ($tag) is outside of enumeration's range (0,21)"
            }
            return entries[tag.toInt()]
        }

        internal fun named(text: String): Term? = names[text]

        fun fromJson(value: JsonElement): Term {
            if (value !is JsonPrimitive || !value.isString) throw typeMismatch("Term", value)
            val text = TextChars.fromString(value.content)
            return named(text) ?: throw invalidName(text)
        }

        /** GHC's Read lexer skips Unicode whitespace, but does not skip comments. */
        fun reads(input: CharacterString, precedence: Long = 0): List<Pair<Term, CharacterString>> {
            @Suppress("UNUSED_VARIABLE") val sourcePrecedence = precedence
            val lexer = TermLexer(input.points())
            val value = lexer.term() ?: return emptyList()
            return listOf(value to lexer.remaining())
        }
        fun reads(input: String, precedence: Long = 0) = reads(CharacterStringChars.fromString(input), precedence)
        fun read(input: CharacterString): Term = finishRead(reads(input))
        fun read(input: String): Term = read(CharacterStringChars.fromString(input))

        fun readsList(input: CharacterString): List<Pair<List<Term>, CharacterString>> {
            val lexer = TermLexer(input.points())
            val value = lexer.list() ?: return emptyList()
            return listOf(value to lexer.remaining())
        }
        fun readsList(input: String) = readsList(CharacterStringChars.fromString(input))
        fun readList(input: CharacterString): List<Term> = finishRead(readsList(input))
        fun readList(input: String): List<Term> = readList(CharacterStringChars.fromString(input))

        private fun <A> finishRead(results: List<Pair<A, CharacterString>>): A {
            val complete = results.filter { (_, rest) -> rest.points().all(ParserCharacters::isSpace) }
            require(complete.size == 1) {
                if (complete.isEmpty()) "Prelude.read: no parse" else "Prelude.read: ambiguous parse"
            }
            return complete.single().first
        }
    }
}

private class TermLexer(private val points: IntArray) {
    private var at = 0
    private fun spaces() { while (at < points.size && ParserCharacters.isSpace(points[at])) at++ }
    private fun opening(): Int {
        var count = 0
        spaces()
        while (at < points.size && points[at] == 40) { count++; at++; spaces() }
        return count
    }
    private fun closing(count: Int): Boolean {
        repeat(count) { if (!symbol(41)) return false }
        return true
    }
    private fun symbol(cp: Int): Boolean {
        spaces()
        if (at >= points.size || points[at] != cp) return false
        at++; return true
    }
    fun remaining() = CharacterString(points.copyOfRange(at, points.size))
    fun term(): Term? {
        val parentheses = opening()
        val start = at
        if (at >= points.size || !(ParserCharacters.isAlpha(points[at]) || points[at] == 95)) return null
        at++
        while (at < points.size && (ParserCharacters.isAlphaNum(points[at]) || points[at] == 95 || points[at] == 39)) at++
        val term = Term.named(String(points, start, at - start)) ?: return null
        return if (closing(parentheses)) term else null
    }
    fun list(): List<Term>? {
        val parentheses = opening()
        if (!symbol(91)) return null
        val values = mutableListOf<Term>()
        spaces()
        if (at < points.size && points[at] == 93) at++ else {
            while (true) {
                values.add(term() ?: return null)
                spaces()
                if (at < points.size && points[at] == 93) { at++; break }
                if (!symbol(44)) return null
            }
        }
        return if (closing(parentheses)) values.toList() else null
    }
}

internal fun invalidName(text: String) = IllegalArgumentException("Invalid Term name ${HaskellString.quote(text)}")
internal fun typeMismatch(expected: String, value: JsonElement): IllegalArgumentException {
    val actual = when (value) {
        JsonNull -> "Null"
        is JsonObject -> "Object"
        is JsonArray -> "Array"
        is JsonPrimitive -> if (value.isString) "String" else if (value.booleanOrNull != null) "Boolean" else "Number"
    }
    return IllegalArgumentException("expected $expected, but encountered $actual")
}
