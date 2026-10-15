// Text.Parsec.Prim/Combinator 3.1.18.0; (c) Daan Leijen 1999-2001, Paolo Martini 2007.
// BSD-2-Clause; licenses/Parsec-BSD-2-Clause.txt.
package io.heapy.kotdoc.parsing

/** Typed token streams share the character parser's consumed/empty interpreter. */
interface TokenStream<T> { fun uncons(): TokenStep<T>? }
data class TokenStep<T>(val token: T, val tail: TokenStream<T>)
class ListTokenStream<T> private constructor(private val values: List<T>, private val index: Int) : TokenStream<T> {
    constructor(values: List<T>) : this(values.toList(), 0)
    override fun uncons(): TokenStep<T>? = if (index == values.size) null else TokenStep(values[index], ListTokenStream(values, index + 1))
    fun remaining(): List<T> = values.subList(index, values.size).toList()
}
data class TokenParseState<T, U>(val input: TokenStream<T>, override val position: SourcePos, val user: U) : PositionedParserState
sealed interface TokenMatch<out A> {
    data class Accept<A>(val value: A) : TokenMatch<A>
    data object Reject : TokenMatch<Nothing>
}

class TokenParsec<T, U, A> private constructor(private val machine: ParserMachine<TokenParseState<T, U>, A>) {
    fun run(initial: TokenParseState<T, U>): ParserReply<TokenParseState<T, U>, A> = machine.run(initial)
    fun <B> map(f: (A) -> B): TokenParsec<T, U, B> = TokenParsec(machine.map(f))
    fun <B> flatMap(next: (A) -> TokenParsec<T, U, B>): TokenParsec<T, U, B> = TokenParsec(machine.flatMap { next(it).machine })
    infix fun or(other: TokenParsec<T, U, A>): TokenParsec<T, U, A> = TokenParsec(machine or other.machine)
    fun <B> then(next: TokenParsec<T, U, B>): TokenParsec<T, U, B> = flatMap { next }
    fun <B> before(next: TokenParsec<T, U, B>): TokenParsec<T, U, A> = flatMap { value -> next.map { value } }
    fun attempt(): TokenParsec<T, U, A> = TokenParsec(machine.attempt())
    fun lookAhead(): TokenParsec<T, U, A> = TokenParsec(machine.lookAhead())
    fun label(text: String): TokenParsec<T, U, A> = TokenParsec(machine.label(text))
    fun many(): TokenParsec<T, U, List<A>> = TokenParsec(machine.many())
    fun skipMany(): TokenParsec<T, U, Unit> = TokenParsec(machine.skipMany())
    fun many1(): TokenParsec<T, U, List<A>> = flatMap { first -> many().map { listOf(first) + it } }
    fun option(default: A): TokenParsec<T, U, A> = this or pure(default)
    fun optional(): TokenParsec<T, U, Unit> = map { Unit }.option(Unit)
    fun notFollowedBy(show: (A) -> String): TokenParsec<T, U, Unit> =
        (attempt().flatMap { unexpected<T, U, Unit>(show(it)) } or pure(Unit)).attempt()

    companion object {
        fun <T, U, A> primitive(f: (TokenParseState<T, U>) -> ParserReply<TokenParseState<T, U>, A>): TokenParsec<T, U, A> = TokenParsec(ParserMachine.primitive(f))
        fun <T, U, A> pure(value: A): TokenParsec<T, U, A> = primitive { ParserReply(true, value, it, false, ParseError(it.position)) }
        fun <T, U, A> defer(f: () -> TokenParsec<T, U, A>): TokenParsec<T, U, A> = TokenParsec(ParserMachine.defer { f().machine })
        fun <T, U, A> zero(): TokenParsec<T, U, A> = primitive { ParserReply(false, null, it, false, ParseError(it.position)) }
        fun <T, U, A> fail(message: String): TokenParsec<T, U, A> = failure(ParseMessage(MessageKind.Message, message))
        fun <T, U, A> unexpected(message: String): TokenParsec<T, U, A> = failure(ParseMessage(MessageKind.UnExpect, message))
        private fun <T, U, A> failure(message: ParseMessage): TokenParsec<T, U, A> = primitive { ParserReply(false, null, it, false, ParseError(it.position, listOf(message))) }
        fun <T, U> state(): TokenParsec<T, U, TokenParseState<T, U>> = primitive { ParserReply(true, it, it, false, ParseError(it.position)) }
        fun <T, U> modify(f: (TokenParseState<T, U>) -> TokenParseState<T, U>): TokenParsec<T, U, Unit> = primitive {
            val next = f(it); ParserReply(true, Unit, next, false, ParseError(next.position))
        }
        fun <T, U, A> lift(f: () -> A): TokenParsec<T, U, A> = primitive { ParserReply(true, f(), it, false, ParseError(it.position)) }
        fun <T, U, A> tokenPrim(show: (T) -> String, nextPosition: (SourcePos, T, TokenStream<T>) -> SourcePos, test: (T) -> TokenMatch<A>): TokenParsec<T, U, A> =
            tokenPrimEx(show, nextPosition, null, test)
        fun <T, U, A> tokenPrimEx(show: (T) -> String, nextPosition: (SourcePos, T, TokenStream<T>) -> SourcePos,
                                 nextUser: ((SourcePos, T, TokenStream<T>, U) -> U)?, test: (T) -> TokenMatch<A>): TokenParsec<T, U, A> = primitive { state ->
            val step = state.input.uncons()
            if (step == null) ParserReply(false, null, state, false, ParseError(state.position, listOf(ParseMessage(MessageKind.SysUnExpect, ""))))
            else when (val match = test(step.token)) {
                TokenMatch.Reject -> ParserReply(false, null, state, false, ParseError(state.position, listOf(ParseMessage(MessageKind.SysUnExpect, show(step.token)))))
                is TokenMatch.Accept -> {
                    val next = state.copy(input = step.tail, position = nextPosition(state.position, step.token, step.tail),
                        user = if (nextUser == null) state.user else nextUser(state.position, step.token, step.tail, state.user))
                    ParserReply(true, match.value, next, true, ParseError(next.position))
                }
            }
        }
        fun <T, U> anyToken(show: (T) -> String): TokenParsec<T, U, T> = tokenPrim(show, { p, _, _ -> p }, { TokenMatch.Accept(it) })
        fun <T, U> eof(show: (T) -> String): TokenParsec<T, U, Unit> = anyToken<T, U>(show).notFollowedBy(show).label("end of input")
        fun <T, U, A, B> manyTill(p: TokenParsec<T, U, A>, end: TokenParsec<T, U, B>): TokenParsec<T, U, List<A>> {
            class Chain<A>(val value: A, val tail: Chain<A>?)
            fun loop(): TokenParsec<T, U, Chain<A>?> = end.map<Chain<A>?> { null } or
                p.flatMap { first -> defer { loop() }.map<Chain<A>?> { tail -> Chain(first, tail) } }
            return defer { loop() }.map { chain ->
                val out = mutableListOf<A>(); var node = chain
                while (node != null) { out.add(node.value); node = node.tail }; out
            }
        }
    }
}
