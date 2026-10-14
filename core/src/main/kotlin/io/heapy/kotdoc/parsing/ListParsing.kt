// Text.Pandoc.Parsing.Lists and Sources, Pandoc 3.12.1.
// Copyright (C) 2006-2024 John MacFarlane; GPL-2.0-or-later.
package io.heapy.kotdoc.parsing

import io.heapy.kotdoc.ast.ListAttributes
import io.heapy.kotdoc.ast.ListNumberDelim
import io.heapy.kotdoc.ast.ListNumberStyle

/** The two ParserState fields used by Lists, with updates preserving the rest
 * of the caller's state. This is not the complete Pandoc ParserState. */
interface ExampleNumberState<U> {
    fun nextExample(state: U): Long
    fun examples(state: U): Map<String, Long>
    fun update(state: U, nextExample: Long, examples: Map<String, Long>): U
}

data class ExampleNumbers(val nextExample: Long = 1, val examples: Map<String, Long> = emptyMap()) {
    companion object : ExampleNumberState<ExampleNumbers> {
        override fun nextExample(state: ExampleNumbers) = state.nextExample
        override fun examples(state: ExampleNumbers) = state.examples
        override fun update(state: ExampleNumbers, nextExample: Long, examples: Map<String, Long>) =
            state.copy(nextExample = nextExample, examples = examples)
    }
}

/** Pandoc's Sources character primitives for the Text/String stream instance.
 * Unlike Parsec.Char, these have no expectation label and show a Char on failure. */
object PandocTextCharacters {
    fun <U> satisfy(test: (Int) -> Boolean): Parsec<U, Int> = Parsec.primitive { state ->
        val point = state.input.point
        if (point != null && test(point)) {
            val next = state.copy(input = state.input.tail(), position = state.position.advance(point))
            ParseReply(true, point, next, true, ParseError(next.position))
        } else ParseReply(false, null, state, false, ParseError(state.position,
            listOf(ParseMessage(MessageKind.SysUnExpect, point?.let(Parsec.Companion::showChar) ?: ""))))
    }
    fun <U> char(point: Int): Parsec<U, Int> = satisfy { it == point }
    fun <U> anyChar(): Parsec<U, Int> = satisfy { true }
    fun <U> digit(): Parsec<U, Int> = satisfy(ParserCharacters::isDigit)
    fun <U> alphaNum(): Parsec<U, Int> = satisfy(ParserCharacters::isAlphaNum)
}

/** All eight exported list-marker parsers; Int arithmetic follows GHC Int64. */
object ListParsing {
    private fun <U> char(c: Char) = PandocTextCharacters.char<U>(c.code)
    private fun <U> pure(value: Long) = Parsec.pure<U, Long>(value)
    fun <U> romanNumeral(upperCase: Boolean): Parsec<U, Long> {
        fun r(c: Char) = char<U>(if (upperCase) c else c.lowercaseChar())
        fun count(c: Char, scale: Long) = r(c).many().map { scale * it.size.toLong() }
        fun pair(a: Char, b: Char, value: Long) = r(a).then(r(b)).map { value }.attempt().option(0)
        fun single(c: Char, value: Long) = r(c).map { value }.option(0)
        val look = listOf('I','V','X','L','C','D','M').map(::r).reduce { a,b -> a or b }.lookAhead()
        val parts = listOf(count('M',1000),pair('C','M',900),single('D',500),pair('C','D',400),
            count('C',100),pair('X','C',90),single('L',50),pair('X','L',40),count('X',10),
            pair('I','X',9),single('V',5),pair('I','V',4),count('I',1))
        return look.then(parts.fold(pure<U>(0)) { total,part -> total.flatMap { n -> part.map { n + it } } })
            .flatMap { if (it == 0L) Parsec.fail("not a roman numeral") else pure(it) }
    }
    fun <U> upperRoman() = romanNumeral<U>(true).map { ListNumberStyle.UpperRoman to it }
    fun <U> lowerRoman() = romanNumeral<U>(false).map { ListNumberStyle.LowerRoman to it }

    // Only digit tokens can reach this read. Read Int wraps arbitrary-size
    // decimal literals modulo 2^64; an empty literal has no Read result.
    private fun readDigits(points: List<Int>): Long? = if (points.isEmpty()) null else
        points.fold(0L) { n,point -> n * 10 + point - '0'.code }
    fun <U> decimal(): Parsec<U, Pair<ListNumberStyle, Long>> =
        PandocTextCharacters.digit<U>().many1().map { ListNumberStyle.Decimal to (readDigits(it) ?: 1) }
    fun <U> lowerAlpha(): Parsec<U, Pair<ListNumberStyle, Long>> =
        PandocTextCharacters.satisfy<U> { it in 'a'.code..'z'.code }.map { ListNumberStyle.LowerAlpha to (it - 'a'.code + 1L) }
    fun <U> upperAlpha(): Parsec<U, Pair<ListNumberStyle, Long>> =
        PandocTextCharacters.satisfy<U> { it in 'A'.code..'Z'.code }.map { ListNumberStyle.UpperAlpha to (it - 'A'.code + 1L) }
    private fun <U> defaultNum() = char<U>('#').map { ListNumberStyle.DefaultStyle to 1L }
    private fun <U> romanOne() = char<U>('i').map { ListNumberStyle.LowerRoman to 1L } or
        char<U>('I').map { ListNumberStyle.UpperRoman to 1L }
    private fun <U> exampleNum(access: ExampleNumberState<U>): Parsec<U, Pair<ListNumberStyle, Long>> {
        val digits = PandocTextCharacters.digit<U>().many()
        val alpha = PandocTextCharacters.alphaNum<U>().many1()
        val labelPart = alpha or (char<U>('_') or char<U>('-')).flatMap { c -> alpha.map { listOf(c) + it } }.attempt()
        return digits.flatMap { prefix -> char<U>('@').then(labelPart.many()).flatMap { parts ->
            val label = buildString { parts.forEach { xs -> xs.forEach { appendCodePoint(it) } } }
            val number = readDigits(prefix)
            Parsec.modify<U> { state -> if (number == null) state else state.copy(user =
                access.update(state.user,number,access.examples(state.user))) }.then(Parsec.state()).flatMap { state ->
                val examples = access.examples(state.user)
                val existing = examples[label]
                if (existing != null) Parsec.pure(ListNumberStyle.Example to existing)
                else {
                    val next = access.nextExample(state.user)
                    val updated = if (label.isEmpty()) examples else examples + (label to next)
                    Parsec.modify<U> { it.copy(user = access.update(it.user,next + 1,updated)) }
                        .then(Parsec.pure(ListNumberStyle.Example to next))
                }
            }
        } }
    }
    fun <U> anyOrderedListMarker(access: ExampleNumberState<U>): Parsec<U, ListAttributes> =
        char<U>('(').map { true }.option(false).flatMap { openParen ->
            PandocTextCharacters.anyChar<U>().lookAhead().flatMap { c ->
                fun withDelim(num: Parsec<U, Pair<ListNumberStyle, Long>>) = num.flatMap { (style,start) ->
                    (char<U>('.').map { if (style == ListNumberStyle.DefaultStyle) ListNumberDelim.DefaultDelim else ListNumberDelim.Period } or
                        char<U>(')').map { if (openParen) ListNumberDelim.TwoParens else ListNumberDelim.OneParen })
                        .map { ListAttributes(start,style,it) }
                }.attempt()
                when {
                    c == '@'.code -> withDelim(exampleNum(access))
                    c == '#'.code -> withDelim(defaultNum())
                    ParserCharacters.isDigit(c) -> withDelim(exampleNum(access)) or withDelim(decimal())
                    c in 'a'.code..'z'.code -> withDelim(romanOne()) or withDelim(lowerAlpha()) or withDelim(lowerRoman())
                    c in 'A'.code..'Z'.code -> withDelim(romanOne()) or withDelim(upperAlpha()) or withDelim(upperRoman())
                    else -> Parsec.zero()
                }
            }
        }.attempt()
    fun <U> orderedListMarker(style: ListNumberStyle, delimiter: ListNumberDelim, access: ExampleNumberState<U>): Parsec<U, Long> {
        val num = defaultNum<U>() or when (style) {
            ListNumberStyle.DefaultStyle, ListNumberStyle.Decimal -> decimal()
            ListNumberStyle.Example -> exampleNum(access)
            ListNumberStyle.UpperRoman -> upperRoman()
            ListNumberStyle.LowerRoman -> lowerRoman()
            ListNumberStyle.UpperAlpha -> upperAlpha()
            ListNumberStyle.LowerAlpha -> lowerAlpha()
        }
        val context = when (delimiter) {
            ListNumberDelim.DefaultDelim, ListNumberDelim.Period -> num.before(char<U>('.'))
            ListNumberDelim.OneParen -> num.before(char<U>(')'))
            ListNumberDelim.TwoParens -> char<U>('(').then(num).before(char<U>(')'))
        }
        return context.attempt().map { it.second }
    }
}
