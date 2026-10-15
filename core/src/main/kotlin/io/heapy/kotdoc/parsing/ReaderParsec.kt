// ReaderT instances from transformers 0.6.1.2 / mtl 2.3.1; BSD-3-Clause.
// Copyright (C) Andy Gill 2001, Oregon Graduate Institute of Science and Technology 2001,
// Jeff Newbern 2003-2007, Andriy Palamarchuk 2007. Notices in licenses/.
// ParsecT local lifting from parsec 3.1.18.0; (c) Daan Leijen 1999-2001,
// Paolo Martini 2007; BSD-2-Clause, licenses/Parsec-BSD-2-Clause.txt.
package io.heapy.kotdoc.parsing

/** Reader environment outside parser user state. Delayed construction shares the
 * existing consumed/empty interpreter, including deep local/bind chains. */
class ReaderParsec<E, U, A>(private val reader: (Deferred<E>) -> Parsec<U, A>) {
    fun runReader(environment: Deferred<E>): Parsec<U, A> = Parsec.defer { reader(environment) }
    fun run(initial: ParseState<U>, environment: E): ParserReply<ParseState<U>, A> = runReader(Deferred.now(environment)).run(initial)
    fun <B> map(function: (A) -> B): ReaderParsec<E, U, B> = ReaderParsec { env -> runReader(env).map(function) }
    fun <B> flatMap(next: (A) -> ReaderParsec<E, U, B>): ReaderParsec<E, U, B> = ReaderParsec { env -> runReader(env).flatMap { next(it).runReader(env) } }
    infix fun or(other: ReaderParsec<E, U, A>): ReaderParsec<E, U, A> = ReaderParsec { env -> runReader(env) or other.runReader(env) }
    fun <B> then(other: ReaderParsec<E, U, B>): ReaderParsec<E, U, B> = flatMap { other }
    fun <B> before(other: ReaderParsec<E, U, B>): ReaderParsec<E, U, A> = flatMap { value -> other.map { value } }
    fun attempt(): ReaderParsec<E, U, A> = ReaderParsec { runReader(it).attempt() }
    fun lookAhead(): ReaderParsec<E, U, A> = ReaderParsec { runReader(it).lookAhead() }
    fun label(text: String): ReaderParsec<E, U, A> = ReaderParsec { runReader(it).label(text) }
    fun many(): ReaderParsec<E, U, List<A>> = ReaderParsec { runReader(it).many() }
    fun many1(): ReaderParsec<E, U, List<A>> = ReaderParsec { runReader(it).many1() }
    fun skipMany(): ReaderParsec<E, U, Unit> = ReaderParsec { runReader(it).skipMany() }
    fun optional(): ReaderParsec<E, U, Unit> = ReaderParsec { runReader(it).optional() }
    fun option(default: A): ReaderParsec<E, U, A> = ReaderParsec { runReader(it).option(default) }
    fun notFollowedBy(show: (A) -> String): ReaderParsec<E, U, Unit> = ReaderParsec { runReader(it).notFollowedBy(show) }
    /** The transformed environment is shared and is not forced by actions which ignore it. */
    fun local(update: (Deferred<E>) -> Deferred<E>): ReaderParsec<E, U, A> =
        ReaderParsec { env -> runReader(Deferred.defer { update(env) }) }
    fun localValue(update: (E) -> E): ReaderParsec<E, U, A> = local { it.mapValue(update) }
    companion object {
        fun <E, U, A> lift(parser: Parsec<U, A>): ReaderParsec<E, U, A> = ReaderParsec { parser }
        fun <E, U, A> pure(value: A): ReaderParsec<E, U, A> = lift(Parsec.pure(value))
        fun <E, U, A> defer(parser: () -> ReaderParsec<E, U, A>): ReaderParsec<E, U, A> = ReaderParsec { parser().runReader(it) }
        fun <E, U, A> zero(): ReaderParsec<E, U, A> = lift(Parsec.zero())
        fun <E, U, A> fail(message: String): ReaderParsec<E, U, A> = lift(Parsec.fail(message))
        fun <E, U, A> asks(function: (Deferred<E>) -> A): ReaderParsec<E, U, A> = ReaderParsec { env -> Parsec.lift { function(env) } }
        fun <E, U, A> asksValue(function: (E) -> A): ReaderParsec<E, U, A> = asks { function(it.value()) }
        /** Lazy environment adapter for callbacks which may ignore their input. */
        fun <E, U> askDeferred(): ReaderParsec<E, U, Deferred<E>> = asks { it }
        fun <E, U> ask(): ReaderParsec<E, U, E> = asks { it.value() }
    }
}
