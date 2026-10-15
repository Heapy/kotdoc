// ReaderT instances from transformers 0.6.1.2 / mtl 2.3.1; BSD-3-Clause.
// Copyright (C) Andy Gill 2001, Oregon Graduate Institute of Science and Technology 2001,
// Jeff Newbern 2003-2007, Andriy Palamarchuk 2007. Notices in licenses/.
// ParsecT local lifting from parsec 3.1.18.0; (c) Daan Leijen 1999-2001,
// Paolo Martini 2007; BSD-2-Clause, licenses/Parsec-BSD-2-Clause.txt.
package io.heapy.kotdoc.parsing

/** Reader environment outside parser user state. Delayed construction shares the
 * existing consumed/empty interpreter, including deep local/bind chains. */
class ReaderTokenParsec<E, T, U, A>(private val reader: (Deferred<E>) -> TokenParsec<T, U, A>) {
    fun runReader(environment: Deferred<E>): TokenParsec<T, U, A> = TokenParsec.defer { reader(environment) }
    fun run(initial: TokenParseState<T, U>, environment: E): ParserReply<TokenParseState<T, U>, A> = runReader(Deferred.now(environment)).run(initial)
    fun <B> map(function: (A) -> B): ReaderTokenParsec<E, T, U, B> = ReaderTokenParsec { env -> runReader(env).map(function) }
    fun <B> flatMap(next: (A) -> ReaderTokenParsec<E, T, U, B>): ReaderTokenParsec<E, T, U, B> = ReaderTokenParsec { env -> runReader(env).flatMap { next(it).runReader(env) } }
    infix fun or(other: ReaderTokenParsec<E, T, U, A>): ReaderTokenParsec<E, T, U, A> = ReaderTokenParsec { env -> runReader(env) or other.runReader(env) }
    fun <B> then(other: ReaderTokenParsec<E, T, U, B>): ReaderTokenParsec<E, T, U, B> = flatMap { other }
    fun <B> before(other: ReaderTokenParsec<E, T, U, B>): ReaderTokenParsec<E, T, U, A> = flatMap { value -> other.map { value } }
    fun attempt(): ReaderTokenParsec<E, T, U, A> = ReaderTokenParsec { runReader(it).attempt() }
    fun lookAhead(): ReaderTokenParsec<E, T, U, A> = ReaderTokenParsec { runReader(it).lookAhead() }
    fun label(text: String): ReaderTokenParsec<E, T, U, A> = ReaderTokenParsec { runReader(it).label(text) }
    fun many(): ReaderTokenParsec<E, T, U, List<A>> = ReaderTokenParsec { runReader(it).many() }
    fun many1(): ReaderTokenParsec<E, T, U, List<A>> = ReaderTokenParsec { runReader(it).many1() }
    fun skipMany(): ReaderTokenParsec<E, T, U, Unit> = ReaderTokenParsec { runReader(it).skipMany() }
    fun optional(): ReaderTokenParsec<E, T, U, Unit> = ReaderTokenParsec { runReader(it).optional() }
    fun option(default: A): ReaderTokenParsec<E, T, U, A> = ReaderTokenParsec { runReader(it).option(default) }
    fun notFollowedBy(show: (A) -> String): ReaderTokenParsec<E, T, U, Unit> = ReaderTokenParsec { runReader(it).notFollowedBy(show) }
    /** The transformed environment is shared and is not forced by actions which ignore it. */
    fun local(update: (Deferred<E>) -> Deferred<E>): ReaderTokenParsec<E, T, U, A> =
        ReaderTokenParsec { env -> runReader(Deferred.defer { update(env) }) }
    fun localValue(update: (E) -> E): ReaderTokenParsec<E, T, U, A> = local { it.mapValue(update) }
    companion object {
        fun <E, T, U, A> lift(parser: TokenParsec<T, U, A>): ReaderTokenParsec<E, T, U, A> = ReaderTokenParsec { parser }
        fun <E, T, U, A> pure(value: A): ReaderTokenParsec<E, T, U, A> = lift(TokenParsec.pure(value))
        fun <E, T, U, A> defer(parser: () -> ReaderTokenParsec<E, T, U, A>): ReaderTokenParsec<E, T, U, A> = ReaderTokenParsec { parser().runReader(it) }
        fun <E, T, U, A> zero(): ReaderTokenParsec<E, T, U, A> = lift(TokenParsec.zero())
        fun <E, T, U, A> fail(message: String): ReaderTokenParsec<E, T, U, A> = lift(TokenParsec.fail(message))
        fun <E, T, U, A> asks(function: (Deferred<E>) -> A): ReaderTokenParsec<E, T, U, A> = ReaderTokenParsec { env -> TokenParsec.lift { function(env) } }
        fun <E, T, U, A> asksValue(function: (E) -> A): ReaderTokenParsec<E, T, U, A> = asks { function(it.value()) }
        /** Lazy environment adapter for callbacks which may ignore their input. */
        fun <E, T, U> askDeferred(): ReaderTokenParsec<E, T, U, Deferred<E>> = asks { it }
        fun <E, T, U> ask(): ReaderTokenParsec<E, T, U, E> = asks { it.value() }
    }
}
