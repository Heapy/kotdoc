// Text.Pandoc.Parsing.Math and Shared.trimMath, © 2006-2024 John MacFarlane.
// GPL-2.0-or-later; complete unchanged source retained in upstream/pandoc.
package io.heapy.kotdoc.parsing

import io.heapy.kotdoc.formats.HaskellString
import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.options.Extension
import io.heapy.kotdoc.options.ExtensionName

/** Delimiter parsers, preserving the caller's state and stream position rules. */
object MathParsing {
    fun mathInline() = mathInline(ReaderOptionsAccess.options)
    fun mathDisplay() = mathDisplay(ReaderOptionsAccess.options)
    fun <U> mathInline(access: ReaderOptionsAccess<U>): Parsec<U, String> =
        enabled(access, Extension.Builtin(ExtensionName.TexMathDollars), "$", "$") or
            enabled(access, Extension.Builtin(ExtensionName.TexMathSingleBackslash), "\\(", "\\)") or
            enabled(access, Extension.Builtin(ExtensionName.TexMathDoubleBackslash), "\\\\(", "\\\\)")
    fun <U> mathDisplay(access: ReaderOptionsAccess<U>): Parsec<U, String> =
        enabled(access, Extension.Builtin(ExtensionName.TexMathDollars), "$$", "$$") or
            enabled(access, Extension.Builtin(ExtensionName.TexMathSingleBackslash), "\\[", "\\]") or
            enabled(access, Extension.Builtin(ExtensionName.TexMathDoubleBackslash), "\\\\[", "\\\\]")
    private fun <U> enabled(access: ReaderOptionsAccess<U>, extension: Extension, open: String, close: String) =
        ReaderOptionGuards.guardEnabled(access, extension).then(mathWith<U>(open, close))

    private fun <U> char(c: Char) = SourceParsers.char<U>(c.code)
    private fun <U> text(s: String) = SourceParsers.string<U>(s)
    private fun <U> spaceChar() = SourceParsers.satisfy<U> { it == 32 || it == 9 }
    private fun <U> blankline() = spaceChar<U>().skipMany().then(SourceParsers.newline()).attempt()
    private fun isSpaceChar(c: Int) = c == 32 || c == 9 || c == 10 || c == 13

    // General.notFollowedBy' differs from Parsec.notFollowedBy when p accepts
    // empty input: choose a parser first, then execute it outside the choice.
    private fun <U, A> notFollowedBy(p: Parsec<U, A>, show: (A) -> String): Parsec<U, Unit> =
        (p.attempt().map { Parsec.unexpected<U, Unit>(show(it)) } or
            Parsec.pure(Parsec.pure<U, Unit>(Unit))).flatMap { it }.attempt()
    private class Link<A>(val value: A, val next: Link<A>?)
    private fun <A> values(chain: Link<A>?): List<A> {
        val out = mutableListOf<A>(); var node = chain
        while (node != null) { out.add(node.value); node = node.next }; return out
    }
    private fun <U, A, B> manyTill(p: Parsec<U, A>, end: Parsec<U, B>): Parsec<U, Link<A>?> {
        fun loop(): Parsec<U, Link<A>?> = end.map<Link<A>?> { null } or
            p.flatMap { first -> Parsec.defer { loop() }.map<Link<A>?> { rest -> Link(first, rest) } }
        return Parsec.defer { loop() }
    }
    private fun <U> mathWith(open: String, close: String): Parsec<U, String> {
        val end = text<U>(close).attempt()
        fun afterSpace() = Parsec.guard<U>(open != "$") or char<U>('$').notFollowedBy(Parsec.Companion::showChar)
        val part = comment<U>() or group<U>() or
            SourceParsers.satisfy<U> { !isSpaceChar(it) && it != 92 && it != 123 }.map { TextChars.fromPoints(intArrayOf(it)) } or
            spaceChar<U>().many1().before(afterSpace()).map { TextChars.fromPoints(it.toIntArray()) } or
            SourceParsers.newline<U>().before(notFollowedBy(blankline<U>(), Parsec.Companion::showChar))
                .before(afterSpace()).map { "\n" } or
            char<U>('\\').then(SourceParsers.anyChar<U>()).map { TextChars.fromPoints(intArrayOf(92, it)) }
        val body = notFollowedBy(end, HaskellString::quote).then(part).flatMap { first ->
            manyTill(part, end).map { rest -> trimMath(buildString { append(first); values(rest).forEach(::append) }) }
        }
        val noLeadingSpace = if (open == "$") SourceParsers.space<U>().notFollowedBy(Parsec.Companion::showChar) else Parsec.pure(Unit)
        val noDigit = if (close == "$") SourceParsers.digit<U>().notFollowedBy(Parsec.Companion::showChar) else Parsec.pure(Unit)
        return text<U>(open).then(noLeadingSpace).then(body).before(noDigit).attempt()
    }
    private fun <U> comment(): Parsec<U, String> = char<U>('%').then(
        manyTill(SourceParsers.anyChar<U>(), SourceParsers.newline<U>())).before(
        notFollowedBy(blankline<U>(), Parsec.Companion::showChar)).map {
        TextChars.fromPoints(intArrayOf(37) + values(it).toIntArray() + intArrayOf(10))
    }
    private fun <U> mathChar(): Parsec<U, Int> = SourceParsers.satisfy<U> { it != 10 } or
        SourceParsers.newline<U>().before(notFollowedBy(blankline<U>(), Parsec.Companion::showChar))
    private fun <U> group(): Parsec<U, String> {
        fun finish(chain: Link<String>): String = buildString { values(chain).asReversed().forEach(::append) }
        fun go(depth: Int, escaped: Boolean, acc: Link<String>): Parsec<U, String> {
            if (depth == 0) return Parsec.pure(finish(acc))
            fun next(c: Int): Parsec<U, String> = Parsec.defer {
                val newDepth = if (escaped) depth else when (c) { 123 -> depth + 1; 125 -> depth - 1; else -> depth }
                go(newDepth, !escaped && c == 92, Link(TextChars.fromPoints(intArrayOf(c)), acc))
            }
            return if (escaped) mathChar<U>().flatMap(::next) else
                comment<U>().flatMap { s -> Parsec.defer { go(depth, false, Link(s, acc)) } } or mathChar<U>().flatMap(::next)
        }
        return char<U>('{').then(Parsec.defer { go(1, false, Link("{", null)) })
    }
    /** Shared.trimMath: an escaped trailing whitespace run retains one character. */
    fun trimMath(input: String): String {
        val points = TextChars.fromString(input).codePoints().toArray(); var first = 0; var end = points.size
        while (first < end && isSpaceChar(points[first])) first++
        while (end > first && isSpaceChar(points[end - 1])) end--
        if (end < points.size && end > first && points[end - 1] == 92) end++
        return TextChars.fromPoints(points.copyOfRange(first, end))
    }
}
