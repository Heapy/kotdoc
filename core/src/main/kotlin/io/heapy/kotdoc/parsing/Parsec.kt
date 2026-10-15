// Text.Parsec.{Prim,Combinator,Char,Error,Pos} 3.1.18.0.
// (c) Daan Leijen 1999-2001, Paolo Martini 2007; BSD-2-Clause, licenses/Parsec-BSD-2-Clause.txt.
package io.heapy.kotdoc.parsing

import io.heapy.kotdoc.formats.HaskellString
import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars
import io.heapy.kotdoc.layout.TextChars

class SourcePos(val nameRaw: CharacterString, val line: Long = 1, val column: Long = 1) : Comparable<SourcePos> {
    constructor(name: String, line: Long = 1, column: Long = 1) : this(CharacterStringChars.fromString(name), line, column)
    /** UTF-16 facade; source operations use nameRaw to retain individual Haskell Chars. */
    val name: String = buildString { nameRaw.points().forEach(::appendCodePoint) }
    val nameText: String get() = TextChars.fromPoints(nameRaw.points())
    fun copy(name: String = this.name, line: Long = this.line, column: Long = this.column): SourcePos =
        SourcePos(if (name == this.name) nameRaw else CharacterStringChars.fromString(name), line, column)
    fun copyRaw(name: CharacterString = nameRaw, line: Long = this.line, column: Long = this.column) = SourcePos(name, line, column)
    operator fun component1() = name
    operator fun component2() = line
    operator fun component3() = column
    override fun equals(other: Any?) = other is SourcePos && nameRaw == other.nameRaw && line == other.line && column == other.column
    override fun hashCode() = 31 * (31 * name.hashCode() + line.hashCode()) + column.hashCode()
    override fun compareTo(other: SourcePos) = nameRaw.compareTo(other.nameRaw).takeIf { it != 0 }
        ?: line.compareTo(other.line).takeIf { it != 0 } ?: column.compareTo(other.column)
    fun advance(point: Int): SourcePos = when (point) {
        10 -> copy(line = line + 1, column = 1)
        9 -> copy(column = column + 8 - Math.floorMod(column - 1, 8))
        else -> copy(column = column + 1)
    }
    override fun toString() = (if (name.isEmpty()) "" else "\"$name\" ") + "(line $line, column $column)"
    fun showCharacters(): CharacterString {
        val raw = nameRaw.points()
        val end = "(line $line, column $column)".codePoints().toArray()
        return CharacterString(if (raw.isEmpty()) end else intArrayOf(34) + raw + intArrayOf(34,32) + end)
    }
}
enum class MessageKind { SysUnExpect, UnExpect, Expect, Message }
data class ParseMessage(val kind: MessageKind, val text: String) : Comparable<ParseMessage> {
    override fun equals(other: Any?) = other is ParseMessage && kind == other.kind
    override fun hashCode() = kind.hashCode()
    override fun compareTo(other: ParseMessage) = kind.compareTo(other.kind)
}
data class ParseError(val position: SourcePos, val messages: List<ParseMessage> = emptyList()) {
    private fun strings() = messages.sortedBy { it.kind }.map { it.text }
    override fun equals(other: Any?) = other is ParseError && position == other.position && strings() == other.strings()
    override fun hashCode() = 31 * position.hashCode() + strings().hashCode()
    fun merge(other: ParseError): ParseError {
        if (other.messages.isEmpty() && messages.isNotEmpty()) return this
        if (messages.isEmpty() && other.messages.isNotEmpty()) return other
        val order = position.line.compareTo(other.position.line).takeIf { it != 0 }
            ?: position.column.compareTo(other.position.column)
        return if (order > 0) this else if (order < 0) other else copy(messages = messages + other.messages)
    }
    fun set(message: ParseMessage) = copy(messages = listOf(message) + messages.filter { it.kind != message.kind })
    fun labels(labels: List<String>): ParseError {
        val first = labels.firstOrNull() ?: ""
        val base = set(ParseMessage(MessageKind.Expect, first))
        return base.copy(messages = labels.drop(1).map { ParseMessage(MessageKind.Expect, it) } + base.messages)
    }
    private fun messageText(): String {
        if (messages.isEmpty()) return "unknown parse error"
        fun texts(kind: MessageKind) = messages.filter { it.kind == kind }.map { it.text }.filter { it.isNotEmpty() }.distinct()
        fun many(prefix: String, texts: List<String>): String {
            if (texts.isEmpty()) return ""
            val joined = if (texts.size == 1) texts[0] else texts.dropLast(1).joinToString(", ") + " or " + texts.last()
            return if (prefix.isEmpty()) joined else "$prefix $joined"
        }
        val unexpected = texts(MessageKind.UnExpect)
        val system = messages.firstOrNull { it.kind == MessageKind.SysUnExpect }?.text
        val lines = listOf(
            if (messages.any { it.kind == MessageKind.UnExpect } || system == null) "" else "unexpected " + system.ifEmpty { "end of input" },
            many("unexpected", unexpected), many("expecting", texts(MessageKind.Expect)), many("", texts(MessageKind.Message)),
        ).filter { it.isNotEmpty() }.distinct()
        return lines.joinToString("") { "\n$it" }
    }
    /** Raw Show result: a source name retains each original Haskell Char. */
    fun showCharacters(): CharacterString = CharacterStringChars.concat(listOf(
        position.showCharacters(), CharacterStringChars.fromString(":" + messageText())))
    /** Text.pack . show, including replacement of each non-scalar source Char. */
    fun showText(): String = CharacterStringChars.build(showCharacters())
    override fun toString(): String = "$position:" + messageText()
}
data class ParseInput(val text: String = "", val index: Int = 0, val stream: CharacterStream? = null) {
    constructor(stream: CharacterStream) : this(stream = stream, text = "")
    val point: Int? get() = if (stream != null) stream.uncons()?.token?.value()
        else if (index == text.length) null else text.codePointAt(index)
    fun tail(): ParseInput = if (stream != null) ParseInput(checkNotNull(stream.uncons()).tail)
        else copy(index = index + Character.charCount(checkNotNull(point)))
    fun updateSourcePos(position: SourcePos, point: Int): SourcePos =
        stream?.updateSourcePos(position, point) ?: position.advance(point)
}
data class ParseState<U>(val input: ParseInput, override val position: SourcePos, val user: U) : PositionedParserState
typealias ParseReply<U, A> = ParserReply<ParseState<U>, A>

/** Character-stream facade over the shared consumed/empty parser interpreter. */
class Parsec<U, A> private constructor(private val machine: ParserMachine<ParseState<U>, A>) {
    fun run(initial: ParseState<U>): ParseReply<U, A> = machine.run(initial)
    fun <B> flatMap(next: (A) -> Parsec<U, B>): Parsec<U, B> = Parsec(machine.flatMap { next(it).machine })
    fun <B> map(f: (A) -> B): Parsec<U, B> = Parsec(machine.map(f))
    infix fun or(other: Parsec<U, A>): Parsec<U, A> = Parsec(machine or other.machine)
    fun <B> then(next: Parsec<U, B>): Parsec<U, B> = flatMap { next }
    fun <B> before(next: Parsec<U, B>): Parsec<U, A> = flatMap { value -> next.map { value } }
    fun combine(other: Parsec<U, A>, append: (A, A) -> A): Parsec<U, A> = flatMap { a -> other.map { b -> append(a, b) } }
    fun attempt(): Parsec<U, A> = Parsec(machine.attempt())
    fun lookAhead(): Parsec<U, A> = Parsec(machine.lookAhead())
    fun label(text: String): Parsec<U, A> = Parsec(machine.label(text))
    fun many(): Parsec<U, List<A>> = Parsec(machine.many())
    /** Control.Applicative's default many retains errors differently from Parsec.many. */
    fun manyAlternative(): Parsec<U, List<A>> {
        class Chain<A>(val value: A, val next: Chain<A>?)
        fun loop(): Parsec<U, Chain<A>?> = flatMap { first -> defer { loop() }.map { tail -> Chain(first, tail) as Chain<A>? } } or pure(null)
        return defer { loop() }.map { chain ->
            val result = mutableListOf<A>(); var current = chain
            while (current != null) { result.add(current.value); current = current.next }
            result
        }
    }
    fun skipMany(): Parsec<U, Unit> = Parsec(machine.skipMany())
    fun many1(): Parsec<U, List<A>> = flatMap { first -> many().map { listOf(first) + it } }
    fun option(default: A): Parsec<U, A> = this or pure(default)
    fun optional(): Parsec<U, Unit> = map { Unit }.option(Unit)
    fun notFollowedBy(show: (A) -> String): Parsec<U, Unit> =
        (attempt().flatMap { unexpected<U, Unit>(show(it)) } or pure(Unit)).attempt()

    companion object {
        fun <U, A> primitive(f: (ParseState<U>) -> ParseReply<U, A>): Parsec<U, A> = Parsec(ParserMachine.primitive(f))
        fun <U, A> pure(value: A): Parsec<U, A> = primitive { ParseReply(true, value, it, false, ParseError(it.position)) }
        fun <U, A> defer(parser: () -> Parsec<U, A>): Parsec<U, A> = Parsec(ParserMachine.defer { parser().machine })
        fun <U, A> zero(): Parsec<U, A> = primitive { ParseReply(false, null, it, false, ParseError(it.position)) }
        fun <U> guard(condition: Boolean): Parsec<U, Unit> = if (condition) pure(Unit) else zero()
        fun <U, A> fail(message: String): Parsec<U, A> = failure(ParseMessage(MessageKind.Message, message))
        fun <U, A> unexpected(message: String): Parsec<U, A> = failure(ParseMessage(MessageKind.UnExpect, message))
        private fun <U, A> failure(message: ParseMessage): Parsec<U, A> = primitive { ParseReply(false, null, it, false, ParseError(it.position, listOf(message))) }
        fun <U> state(): Parsec<U, ParseState<U>> = primitive { ParseReply(true, it, it, false, ParseError(it.position)) }
        fun <U> modify(f: (ParseState<U>) -> ParseState<U>): Parsec<U, Unit> = primitive {
            val next = f(it); ParseReply(true, Unit, next, false, ParseError(next.position))
        }
        fun <U, A> lift(effect: () -> A): Parsec<U, A> = primitive { ParseReply(true, effect(), it, false, ParseError(it.position)) }
        fun <U> satisfy(test: (Int) -> Boolean): Parsec<U, Int> = primitive { state ->
            val cp = state.input.point
            if (cp != null && test(cp)) {
                val next = state.copy(input = state.input.tail(), position = state.position.advance(cp))
                ParseReply(true, cp, next, true, ParseError(next.position))
            } else ParseReply(false, null, state, false, ParseError(state.position,
                listOf(ParseMessage(MessageKind.SysUnExpect, cp?.let { HaskellString.quote(String(Character.toChars(it))) } ?: ""))))
        }
        fun <U> char(point: Int): Parsec<U, Int> = satisfy<U> { it == point }.label(HaskellString.quote(String(Character.toChars(point))))
        fun <U> string(text: String): Parsec<U, String> = primitive { state ->
            var input = state.input; var pos = state.position; var consumed = false; var error: ParseError? = null
            val cps = text.codePoints().toArray()
            for (cp in cps) {
                if (input.point != cp) {
                    error = ParseError(state.position, listOf(ParseMessage(MessageKind.Expect, HaskellString.quote(text)),
                        ParseMessage(MessageKind.SysUnExpect, input.point?.let { HaskellString.quote(String(Character.toChars(it))) } ?: "")))
                    break
                }
                consumed = true; input = input.tail(); pos = pos.advance(cp)
            }
            if (error != null) ParseReply(false, null, state, consumed, error)
            else { val next = state.copy(input = input, position = pos); ParseReply(true, text, next, consumed, ParseError(pos)) }
        }
        fun <U> stringAtomic(text: String): Parsec<U, String> = string<U>(text).attempt()
        fun <U> anyToken(): Parsec<U, Int> = primitive { s ->
            val cp = s.input.point
            if (cp == null) ParseReply(false, null, s, false, ParseError(s.position, listOf(ParseMessage(MessageKind.SysUnExpect, ""))))
            else { val next = s.copy(input = s.input.tail()); ParseReply(true, cp, next, true, ParseError(s.position)) }
        }
        fun <U> eof(): Parsec<U, Unit> = anyToken<U>().notFollowedBy(::showChar).label("end of input")
        fun showChar(point: Int): String = "'" + when (point) {
            39 -> "\\'"; 34 -> "\""; else -> HaskellString.quote(String(Character.toChars(point))).drop(1).dropLast(1)
        } + "'"
    }
}
