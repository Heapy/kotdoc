// Data.ASN1.Parse, crypton-asn1-parse 0.10.0; Vincent Hanquez 2010-2013.
// Full notice: licenses/crypton-asn1-parse-BSD-3-Clause.txt.
package io.heapy.kotdoc.asn1

import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars

sealed interface Asn1ParseResult<out A> {
    class Success<out A> private constructor(private val stored: Lazy<A>) : Asn1ParseResult<A> {
        constructor(value: A) : this(lazyOf(value))
        val value: A get() = stored.value
        companion object { fun <A> deferred(value: () -> A) = Success(lazy(value)) }
    }
    class Failure private constructor(private val stored: Lazy<CharacterString>) : Asn1ParseResult<Nothing> {
        constructor(message: CharacterString): this(lazyOf(message))
        constructor(message: String): this(CharacterStringChars.fromString(message))
        val message get() = stored.value
        operator fun component1() = message
        fun copy(message: CharacterString = this.message) = Failure(message)
        override fun equals(other: Any?) = other is Failure && message == other.message
        override fun hashCode() = message.hashCode()
        override fun toString() = "Failure(message=$message)"
        companion object { fun deferred(message: () -> CharacterString) = Failure(lazy(message)) }
    }
}
class Asn1Parsed<out A> private constructor(internal val deferredValue: Lazy<A>, val remaining: List<Asn1Value>) {
    constructor(value: A, remaining: List<Asn1Value>) : this(lazyOf(value), remaining)
    val value: A get() = deferredValue.value
    companion object { fun <A> deferred(value: () -> A, remaining: List<Asn1Value>) = Asn1Parsed(lazy(value), remaining) }
}
sealed interface Asn1Maybe<out A> {
    data object None : Asn1Maybe<Nothing>
    class Some<out A> private constructor(private val stored: Lazy<A>) : Asn1Maybe<A> {
        constructor(value: A) : this(lazyOf(value))
        val value: A get() = stored.value
        override fun equals(other: Any?) = other is Some<*> && value == other.value
        override fun hashCode() = value?.hashCode() ?: 0
        companion object { fun <A> deferred(value: () -> A) = Some(lazy(value)) }
    }
}

class Asn1Parser<A> private constructor(private val parse: (List<Asn1Value>) -> Asn1ParseResult<Asn1Parsed<A>>) {
    fun runState(values: List<Asn1Value>) = parse(values.toList())
    fun run(values: List<Asn1Value>): Asn1ParseResult<A> = when (val result = runState(values)) {
        is Asn1ParseResult.Failure -> result
        is Asn1ParseResult.Success -> if (result.value.remaining.isEmpty()) Asn1ParseResult.Success.deferred { result.value.value } else Asn1ParseResult.Failure.deferred { CharacterStringChars.fromString("runParseASN1: remaining state ${asn1ShowList(result.value.remaining)}") }
    }
    fun <B> map(transform: (A) -> B): Asn1Parser<B> = mapLazy { transform(it.value) }
    fun <B> mapLazy(transform: (Lazy<A>) -> B): Asn1Parser<B> = Asn1Parser { values ->
        when (val result = parse(values)) {
            is Asn1ParseResult.Failure -> result
            is Asn1ParseResult.Success -> Asn1ParseResult.Success(Asn1Parsed.deferred({ transform(result.value.deferredValue) }, result.value.remaining))
        }
    }
    fun <B> flatMap(next: (A) -> Asn1Parser<B>): Asn1Parser<B> = flatMapLazy { next(it.value) }
    fun <B> flatMapLazy(next: (Lazy<A>) -> Asn1Parser<B>): Asn1Parser<B> = Asn1Parser { values ->
        when (val result = parse(values)) {
            is Asn1ParseResult.Failure -> result
            is Asn1ParseResult.Success -> next(result.value.deferredValue).parse(result.value.remaining)
        }
    }
    fun <B> then(next: Asn1Parser<B>): Asn1Parser<B> = flatMapLazy { next }
    fun orElse(other: Asn1Parser<A>): Asn1Parser<A> = Asn1Parser { values ->
        when (val result = parse(values)) { is Asn1ParseResult.Failure -> other.parse(values); is Asn1ParseResult.Success -> result }
    }

    companion object {
        fun <A> pure(value: A): Asn1Parser<A> = Asn1Parser { Asn1ParseResult.Success(Asn1Parsed(value, it)) }
        fun <A> pureLazy(value: () -> A): Asn1Parser<A> = Asn1Parser { Asn1ParseResult.Success(Asn1Parsed.deferred(value, it)) }
        fun <A> fail(message: String): Asn1Parser<A> = fail(CharacterStringChars.fromString(message))
        fun <A> fail(message: CharacterString): Asn1Parser<A> = Asn1Parser { Asn1ParseResult.Failure(message) }
        fun <A> failDeferred(message: () -> CharacterString): Asn1Parser<A> = Asn1Parser { Asn1ParseResult.Failure.deferred(message) }
        fun <A> failLazy(message: () -> String): Asn1Parser<A> = failDeferred { CharacterStringChars.fromString(message()) }
        fun <A> empty(): Asn1Parser<A> = fail("empty Alternative")
        fun <A, B> apply(function: Asn1Parser<(Lazy<A>) -> B>, argument: Asn1Parser<A>): Asn1Parser<B> = function.flatMapLazy { f -> argument.mapLazy { a -> f.value(a) } }
        val hasNext: Asn1Parser<Boolean> = Asn1Parser { Asn1ParseResult.Success(Asn1Parsed(it.isNotEmpty(), it)) }
        val getNext: Asn1Parser<Asn1Value> = Asn1Parser { if (it.isEmpty()) Asn1ParseResult.Failure("empty") else Asn1ParseResult.Success(Asn1Parsed(it.first(), it.subList(1, it.size))) }

        fun <A> getNextMaybe(select: (Asn1Value) -> Asn1Maybe<A>): Asn1Parser<Asn1Maybe<A>> = Asn1Parser { values ->
            val result = values.firstOrNull()?.let(select) ?: Asn1Maybe.None
            Asn1ParseResult.Success(Asn1Parsed(result, if (result is Asn1Maybe.Some) values.subList(1, values.size) else values))
        }
        fun getNextContainer(kind: Asn1ConstructionType): Asn1Parser<List<Asn1Value>> = Asn1Parser { values ->
            if (values.isEmpty()) Asn1ParseResult.Failure("empty")
            else if (values.first() != Asn1Value.Start(kind)) Asn1ParseResult.Failure("not an expected container")
            else {
                val (inside, rest) = Asn1Stream.getConstructedEnd(0, values.subList(1, values.size))
                Asn1ParseResult.Success(Asn1Parsed(inside, rest))
            }
        }
        fun getNextContainerMaybe(kind: Asn1ConstructionType): Asn1Parser<Asn1Maybe<List<Asn1Value>>> = Asn1Parser { values ->
            if (values.firstOrNull() != Asn1Value.Start(kind)) Asn1ParseResult.Success(Asn1Parsed(Asn1Maybe.None, values))
            else {
                val (inside, rest) = Asn1Stream.getConstructedEnd(0, values.subList(1, values.size))
                Asn1ParseResult.Success(Asn1Parsed(Asn1Maybe.Some(inside), rest))
            }
        }
        fun <A> onNextContainer(kind: Asn1ConstructionType, parser: Asn1Parser<A>): Asn1Parser<A> = getNextContainer(kind).flatMap { inside ->
            when (val result = parser.run(inside)) { is Asn1ParseResult.Failure -> failDeferred { result.message }; is Asn1ParseResult.Success -> pureLazy { result.value } }
        }
        fun <A> onNextContainerMaybe(kind: Asn1ConstructionType, parser: Asn1Parser<A>): Asn1Parser<Asn1Maybe<A>> = getNextContainerMaybe(kind).flatMap { maybe ->
            when (maybe) {
                Asn1Maybe.None -> pure(Asn1Maybe.None)
                is Asn1Maybe.Some -> when (val result = parser.run(maybe.value)) { is Asn1ParseResult.Failure -> failDeferred { result.message }; is Asn1ParseResult.Success -> pureLazy { Asn1Maybe.Some.deferred { result.value } } }
            }
        }
        fun <A> getObject(codec: Asn1Object<A>): Asn1Parser<A> = Asn1Parser { codec.fromAsn1(it) }
        fun <A> getMany(parser: Asn1Parser<A>): Asn1Parser<List<A>> = Asn1Parser { input ->
            val output = mutableListOf<Lazy<A>>(); var rest = input
            var failure: Asn1ParseResult.Failure? = null
            while (rest.isNotEmpty()) when (val result = parser.parse(rest)) {
                is Asn1ParseResult.Failure -> { failure = result; break }
                is Asn1ParseResult.Success -> { output.add(result.value.deferredValue); rest = result.value.remaining }
            }
            failure ?: Asn1ParseResult.Success(Asn1Parsed.deferred({ output.map { it.value } }, rest))
        }
    }
}
