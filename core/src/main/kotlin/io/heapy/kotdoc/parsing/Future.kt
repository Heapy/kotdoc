// Text.Pandoc.Parsing.Future, Pandoc 3.12.1.
// Copyright (C) 2006-2024 John MacFarlane; GPL-2.0-or-later.
// Reader instances: transformers 0.6.1.2 and mtl 2.3.1.
// Copyright (C) Andy Gill 2001, Oregon Graduate Institute of Science and Technology 2001,
// Jeff Newbern 2003-2007, Andriy Palamarchuk 2007; BSD-3-Clause,
// licenses/Transformers-BSD-3-Clause.txt and licenses/Mtl-BSD-3-Clause.txt.
package io.heapy.kotdoc.parsing

/** Reader of the final parser state. Deferred arguments/results preserve
 * Haskell's demand semantics, including callbacks which ignore their input.
 * Value adapters are conveniences for callbacks which do demand that input. */
class Future<S,A>(private val reader: (Deferred<S>) -> Deferred<A>) {
    fun runDelayed(state: Deferred<S>): Deferred<A> = Deferred.defer { reader(state) }
    fun runF(state: S): A = runDelayed(Deferred.now(state)).value()
    fun runFLazy(state: Deferred<S>): A = runDelayed(state).value()
    fun <B> map(function: (Deferred<A>) -> Deferred<B>): Future<S,B> =
        Future { state -> Deferred.defer { function(runDelayed(state)) } }
    fun <B> mapValue(function: (A) -> B): Future<S,B> =
        Future { state -> runDelayed(state).mapValue(function) }
    fun <B> flatMap(function: (Deferred<A>) -> Future<S,B>): Future<S,B> =
        Future { state -> Deferred.defer { function(runDelayed(state)).runDelayed(state) } }
    fun <B> flatMapValue(function: (A) -> Future<S,B>): Future<S,B> =
        Future { state -> runDelayed(state).flatMapValue { function(it).runDelayed(state) } }
    fun <B,C> liftA2(other: Future<S,B>,function: (Deferred<A>,Deferred<B>) -> Deferred<C>): Future<S,C> =
        Future { state -> Deferred.defer { function(runDelayed(state),other.runDelayed(state)) } }
    fun append(other: Future<S,A>,combine: (Deferred<A>,Deferred<A>) -> Deferred<A>): Future<S,A> = liftA2(other,combine)
    fun <B> replace(value: Deferred<B>): Future<S,B> = map { value }
    fun <B> then(other: Future<S,B>): Future<S,B> = Future { other.runDelayed(it) }
    fun <B> before(other: Future<S,B>): Future<S,A> = Future { runDelayed(it) }
    companion object {
        fun <S,A> pure(value: A): Future<S,A> = pureDeferred(Deferred.now(value))
        fun <S,A> pureDeferred(value: Deferred<A>): Future<S,A> = Future { value }
        fun <S> askF(): Future<S,S> = Future { it }
        fun <S,A> asksF(function: (Deferred<S>) -> Deferred<A>): Future<S,A> = Future(function)
        fun <S,A> asksValue(function: (S) -> A): Future<S,A> = Future { it.mapValue(function) }
        fun <S,A,B> apply(function: Future<S,(Deferred<A>) -> Deferred<B>>,argument: Future<S,A>): Future<S,B> =
            Future { state -> function.runDelayed(state).flatMapValue { it(argument.runDelayed(state)) } }
        fun <S,A> mempty(empty: Deferred<A>): Future<S,A> = pureDeferred(empty)
        /** The outer Monad uses only return; its dictionary controls demand. */
        fun <S,A,M> returnF(value: Deferred<A>,pure: (Deferred<Future<S,A>>) -> M): M =
            pure(Deferred.later { pureDeferred(value) })
    }
}
