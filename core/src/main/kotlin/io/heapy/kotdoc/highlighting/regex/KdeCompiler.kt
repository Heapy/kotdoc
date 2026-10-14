// Regex.KDE.Compile 0.15, Copyright (c) 2016-2024 John MacFarlane.
// BSD-3-Clause; licenses/Skylighting-core-BSD-3-Clause.txt.
package io.heapy.kotdoc.highlighting.regex

import io.heapy.kotdoc.parsing.*
import java.math.BigInteger
import io.heapy.kotdoc.highlighting.regex.KdeRegex.*

sealed interface CompiledKdeRegex {
    data class Success(val regex: KdeRegex) : CompiledKdeRegex
    data class Failure(val error: String) : CompiledKdeRegex
}
private data class RegexState(val capture: Long = 0, val sensitive: Boolean, val minimal: Boolean)
private typealias RP<A> = Parsec<RegexState, A>
private data class Flags(val on: String, val off: String) {
    fun apply(s: RegexState) = s.copy(
        sensitive = if ('i' in on && 'i' !in off) false else 'i' in off || s.sensitive,
        minimal = if ('U' in on && 'U' !in off) true else 'U' !in off && s.minimal,
    )
}

object KdeCompiler {
    fun compile(caseSensitive: Boolean, minimal: Boolean, bytes: ByteArray): CompiledKdeRegex {
        val text = Utf8Text.decodeLenient(bytes)
        val result = regex().run(ParseState(ParseInput(text), SourcePos(""), RegexState(sensitive = caseSensitive, minimal = minimal)))
        check(result.success)
        val rest = result.state.input
        return if (rest.index == text.length) CompiledKdeRegex.Success(checkNotNull(result.value))
        else CompiledKdeRegex.Failure("Failed reading: parse error at byte position ${bytes.size.toLong() - encodedLength(text, rest.index)}")
    }
    private fun encodedLength(text: String, start: Int): Long {
        var at = start; var bytes = 0L
        while (at < text.length) {
            val cp = text.codePointAt(at)
            bytes += if (cp < 0x80) 1 else if (cp < 0x800) 2 else if (cp < 0x10000) 3 else 4
            at += Character.charCount(cp)
        }
        return bytes
    }
    private fun ascii(point: Int) = if (point in 0..127) point.toChar() else '\uffff'
    private fun readInt(text: String): Long? = if (text.isEmpty()) null else BigInteger(text).toLong()
    private fun <A> pure(a: A): RP<A> = Parsec.pure(a)
    private fun <A> fail(): RP<A> = Parsec.zero()
    private fun ch(c: Char): RP<Int> = Parsec.char(c.code)
    private fun str(s: String): RP<String> = Parsec.string<RegexState>(s).attempt()
    private fun sat(f: (Int) -> Boolean): RP<Int> = Parsec.satisfy(f)
    private fun any(): RP<Int> = sat { true }
    private fun state(): RP<RegexState> = Parsec.state<RegexState>().map { it.user }
    private fun update(f: (RegexState) -> RegexState): RP<Unit> = Parsec.modify<RegexState> { it.copy(user = f(it.user)) }
    private fun points(points: List<Int>) = buildString { points.forEach(::appendCodePoint) }
    private fun digits() = sat(ParserCharacters::isDigit).many().map(::points)
    private fun digits1() = sat(ParserCharacters::isDigit).many1().map(::points)
    private fun concat(rs: List<KdeRegex>): KdeRegex = rs.asReversed().fold<KdeRegex, KdeRegex>(MatchNull) { tail, r -> MatchConcat(r, tail) }
    private fun alt(rs: List<KdeRegex>): KdeRegex = rs.dropLast(1).asReversed().fold(rs.last()) { tail, r -> MatchAlt(r, tail) }
    private fun regex(): RP<KdeRegex> = altPart().attempt().option(MatchNull).flatMap { first ->
        ch('|').then(altPart().attempt().option(MatchNull)).attempt().many().map { alt(listOf(first) + it) }
    }
    private fun altPart(): RP<KdeRegex> = Parsec.defer<RegexState, KdeRegex>(::part).attempt().many1().map(::concat)
    private fun part(): RP<KdeRegex> = (regexChar().attempt() or Parsec.defer(::parenthesized).attempt()).flatMap(::suffix).attempt()
    private fun modifiers(): RP<Flags> = sat { ascii(it) in "imsU" }.many().flatMap { ons ->
        ch('-').then(sat { ascii(it) in "imnsxU" }.many()).attempt().option(emptyList()).map { Flags(points(ons), points(it)) }
    }
    private fun inline(): RP<KdeRegex> = ch('?').then(modifiers()).before(ch(')')).flatMap { f -> update(f::apply).map<KdeRegex> { MatchNull } }.attempt()
    private data class Group(val wrap: (KdeRegex) -> KdeRegex = { it }, val flags: Flags = Flags("", ""))
    private fun groupModifiers(): RP<Group> = modifiers().before(ch(':')).map { Group(flags = it) }.attempt() or
        ch('<').map { Direction.Backward }.option(Direction.Forward).flatMap { dir ->
            ch('=').map { Group(wrap = { AssertPositive(dir, it) }) } or ch('!').map { Group(wrap = { AssertNegative(dir, it) }) }
        }.attempt() or digits1().flatMap { ds -> readInt(ds)?.let { pure(Group(wrap = { _ -> Subroutine(it) })) } ?: fail() }.attempt() or
        ch('R').map { Group(wrap = { Subroutine(0) }) } or ch('>').map { Group(wrap = { Possessive(it) }) }
    private fun parenthesized(): RP<KdeRegex> = ch('(').then(inline() or
        str("?|").map { true }.option(false).flatMap { reset ->
            (if (reset) pure(Group()) else ch('?').then(groupModifiers()).attempt() or
                update { it.copy(capture = it.capture + 1) }.then(state()).map { s -> Group(wrap = { MatchCapture(s.capture, it) }) })
                .flatMap { group -> state().flatMap { old ->
                    update(group.flags::apply).then(altPart().attempt().option(MatchNull)).flatMap { first -> state().flatMap { initial ->
                        ch('|').then(if (reset) update { it.copy(capture = old.capture) } else pure(Unit))
                            .then(altPart().attempt().option(MatchNull)).flatMap { r -> state().map { r to it.capture } }.attempt().many()
                            .flatMap { rest ->
                                (if (reset) update { it.copy(capture = (listOf(initial.capture) + rest.map { it.second }).max()) } else pure(Unit))
                                    .then(ch(')')).then(update { it.copy(sensitive = old.sensitive, minimal = old.minimal) })
                                    .map { group.wrap(alt(listOf(first) + rest.map { it.first })) }
                            }
                    } }
                } }
        })
    private fun literal(point: Int, sensitive: Boolean): KdeRegex = MatchChar { c -> if (sensitive) point == c else RegexCharacters.toLower(point) == RegexCharacters.toLower(c) }
    private fun suffix(r: KdeRegex): RP<KdeRegex> {
        if (r == AssertBeginning || r == AssertEnd || r == AssertWordBoundary) return pure(r)
        fun modified(greedy: KdeRegex, lazy: KdeRegex): RP<KdeRegex> = state().flatMap { s ->
            ch('+').map<KdeRegex> { Possessive(greedy) } or ch('?').map { if (s.minimal) greedy else lazy } or pure(if (s.minimal) lazy else greedy)
        }
        fun atMost(n: Long, minimal: Boolean): KdeRegex {
            if (n <= 0) return MatchNull
            val next = MatchConcat.deferred(r) { atMost(n - 1, minimal) }
            return if (minimal) MatchAlt(MatchNull, next) else MatchAlt(next, MatchNull)
        }
        fun repeated(min: Long?, max: Long?, lazy: Boolean): KdeRegex {
            if (min == null && max != null || min == 0L && max != null) return atMost(checkNotNull(max), lazy)
            val prefix = concat(List(checkNotNull(min).coerceAtLeast(0).toInt()) { r })
            val tail = if (max == null) {
                if (lazy) MatchAlt(MatchNull, Lazy(MatchSome(r))) else MatchAlt(MatchSome(r), MatchNull)
            } else atMost(max - min, lazy)
            return MatchConcat(prefix, tail)
        }
        val quantifier = any().flatMap { point -> when (ascii(point)) {
            '*' -> modified(MatchAlt(MatchSome(r), MatchNull), MatchAlt(MatchNull, Lazy(MatchSome(r))))
            '+' -> modified(MatchSome(r), Lazy(MatchSome(r)))
            '?' -> modified(MatchAlt(r, MatchNull), MatchAlt(MatchNull, r))
            '{' -> digits().map { readInt(it) }.flatMap { min ->
                ch(',').then(digits()).map { readInt(it) }.attempt().option(min).before(ch('}')).flatMap { max ->
                    if (min == null && max == null || min != null && min > 65535 || max != null && max > 65535 || min != null && max != null && min > max) fail()
                    else modified(repeated(min, max, false), repeated(min, max, true))
                }
            }
            else -> fail()
        } }
        return quantifier.attempt().option(r)
    }
    private fun quantifierShape(): RP<Unit> = (digits1().before(ch(',').before(digits()).attempt().optional()).map { Unit } or
        ch(',').then(digits1()).map { Unit }).before(ch('}')).attempt()
    private fun regexChar(): RP<KdeRegex> = any().flatMap { w -> state().flatMap { s -> when (ascii(w)) {
        '.' -> pure(MatchAnyChar)
        '%' -> digits1().flatMap { ds -> readInt(ds)?.let { pure<KdeRegex>(MatchDynamic(it)) } ?: fail() }.attempt().option(literal(37, true))
        '\\' -> escaped(s.sensitive)
        '$' -> pure(AssertEnd)
        '^' -> pure(AssertBeginning)
        '[' -> charClass(s.sensitive)
        '{' -> quantifierShape().lookAhead().map { true }.attempt().option(false).flatMap { if (it) fail() else pure(literal(123, true)) }
        else -> if (ascii(w) in "\\?*+[%()|.$^") fail() else pure(literal(w, s.sensitive))
    } } }
    private fun escaped(sensitive: Boolean): RP<KdeRegex> = any().flatMap { c ->
        val special: RP<KdeRegex> = when (ascii(c)) {
            'b' -> pure(AssertWordBoundary); 'B' -> pure(AssertNegative(Direction.Forward, AssertWordBoundary))
            'G', 'A' -> pure(AssertBeginning)
            '{' -> digits1().before(ch('}')).flatMap { readInt(it)?.let { pure(MatchCaptured(it, sensitive)) } ?: fail() }
            'g' -> (ch('{').then(digits1()).before(ch('}')).attempt() or digits1()).flatMap { readInt(it)?.let { pure(MatchCaptured(it, sensitive)) } ?: fail() }
            'd', 'D', 's', 'S', 'h', 'H', 'w', 'W' -> pure(MatchChar(predicate(ascii(c))))
            'p', 'P' -> unicodeClass().map { p -> MatchChar { if (c == 80) !p(it) else p(it) } }
            else -> if (ParserCharacters.isDigit(c) && c != 48) pure(MatchCaptured((c - 48).toLong(), sensitive)) else fail()
        }
        special.attempt() or escapedPoint(c).map { literal(it, sensitive) }
    }
    private fun predicate(c: Char): (Int) -> Boolean {
        val p: (Int) -> Boolean = when (c.lowercaseChar()) {
            'd' -> ParserCharacters::isDigit; 's' -> ParserCharacters::isSpace; 'h' -> RegexCharacters::isHorizSpace; else -> RegexCharacters::isWordChar
        }
        return if (c.isUpperCase()) { cp -> !p(cp) } else p
    }
    private fun take(count: Int): RP<String> {
        var parser: RP<List<Int>> = pure(emptyList())
        repeat(count) { parser = parser.flatMap { xs -> any().map { xs + it } } }
        return parser.map(::points)
    }
    private fun escapedPoint(c: Int): RP<Int> {
        fun hex(text: String): RP<Int> = if (text.isNotEmpty() && text.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' })
            text.toLongOrNull(16)?.takeIf { it <= 0x10ffff }?.let { pure(it.toInt()) } ?: fail() else fail()
        return when (ascii(c)) {
            '\\' -> pure(c); 'a' -> pure(7); 'b' -> pure(8); 'f' -> pure(12); 'n' -> pure(10); 'r' -> pure(13); 't' -> pure(9); 'v' -> pure(11)
            '0' -> octalTail(2).map { ("0" + it).toInt(8) }
            in '1'..'7' -> octalTail(2).map { (ascii(c).toString() + it).toInt(8) }
            'z' -> take(4).flatMap(::hex)
            'x' -> (ch('{').then(sat { it != 125 }.many().map(::points)).before(ch('}')).attempt() or take(2)).flatMap(::hex)
            else -> if (RegexCharacters.isPunctuation(c) || RegexCharacters.isSymbol(c) || ParserCharacters.isSpace(c)) pure(c) else fail()
        }
    }
    private fun octalTail(max: Int): RP<String> {
        fun loop(n: Int): RP<List<Int>> = if (n == 0) pure(emptyList()) else sat { it in 48..55 }.flatMap { first -> loop(n - 1).map { listOf(first) + it } }.attempt().option(emptyList())
        return loop(max).map(::points)
    }
    private fun unicodeClass(): RP<(Int) -> Boolean> = (ch('{').then(ch('^').map { true }.option(false)).flatMap { neg ->
        sat { it != 125 }.many().map(::points).before(ch('}')).map { neg to it }
    }.attempt() or sat(ParserCharacters::isAlpha).map { false to points(listOf(it)) }).map { (negative, name) ->
        { point: Int ->
            val cat = RegexCharacters.categoryName(point)
            val matched = if (name.length == 1 && name in "LMNPSZC") cat.startsWith(name) else name in categories && cat == name
            if (negative) !matched else matched
        }
    }
    private val categories = setOf("Lu","Ll","Lt","Lm","Lo","Mn","Mc","Me","Nd","Nl","No","Pc","Pd","Ps","Pe","Pi","Pf","Po","Sm","Sc","Sk","So","Zs","Zl","Zp","Cc","Cf","Cs","Co","Cn")
    private fun charClass(sensitive: Boolean): RP<KdeRegex> {
        val getC: RP<Int> = ch('\\').then(any()).flatMap(::escapedPoint).attempt() or sat { it != 92 && it != 93 }
        val range: RP<(Int) -> Boolean> = getC.flatMap { low -> ch('-').then(getC).map<(Int) -> Boolean> { high -> { it in low..high } }.attempt().option { it == low } }
        val quoted: RP<(Int) -> Boolean> = str("\\Q").then(
            (str("\\E").lookAhead().map { true }.option(false).flatMap { if (it) fail<Int>() else any() }).many()
                .before(str("\\E"))).map { xs -> { it in xs } }
        val escapedClass: RP<(Int) -> Boolean> = ch('\\').then(sat { ascii(it) in "dDsShHwW" }).map { predicate(ascii(it)) }
        val posix: RP<(Int) -> Boolean> = str("[:").then(ch('^').map { true }.option(false)).flatMap { neg ->
            sat { it != 58 }.many1().map(::points).before(str(":]")).flatMap { name ->
                val p: ((Int) -> Boolean)? = when (name) {
                    "alnum" -> ParserCharacters::isAlphaNum; "alpha" -> ParserCharacters::isAlpha; "ascii" -> { cp: Int -> cp < 128 }
                    "blank" -> { cp: Int -> ParserCharacters.isSpace(cp) && cp !in 10..13 }; "cntrl" -> RegexCharacters::isControl
                    "digit" -> ParserCharacters::isDigit; "graph" -> { cp: Int -> RegexCharacters.isPrint(cp) && !ParserCharacters.isSpace(cp) }
                    "lower" -> RegexCharacters::isLower; "upper" -> RegexCharacters::isUpper; "print" -> RegexCharacters::isPrint
                    "punct" -> RegexCharacters::isPunctuation; "space" -> ParserCharacters::isSpace; "word" -> RegexCharacters::isWordChar
                    "xdigit" -> { cp: Int -> cp in 48..57 || cp in 65..70 || cp in 97..102 }; else -> null
                }
                if (p == null) fail() else pure<(Int) -> Boolean> { if (neg) !p(it) else p(it) }
            }
        }
        val members = quoted.attempt() or escapedClass.attempt() or posix.attempt() or range.attempt() or
            str("\\p").then(unicodeClass()).attempt() or str("\\P").then(unicodeClass()).map<(Int) -> Boolean> { p -> { !p(it) } }.attempt()
        return ch('^').map { true }.option(false).flatMap { neg ->
            ch(']').then(ch('-').then(getC).map<(Int) -> Boolean> { high -> { it in 93..high } }.attempt().option { it == 93 })
                .map { listOf(it) }.attempt().option(emptyList()).flatMap { first -> members.many().before(ch(']')).map { rest ->
                    val ps = first + rest
                    MatchChar { point ->
                        val match = ps.any { it(point) } || !sensitive && (ps.any { it(RegexCharacters.toLower(point)) } || ps.any { it(RegexCharacters.toUpper(point)) })
                        if (neg) !match else match
                    }
                } }
        }
    }
}
