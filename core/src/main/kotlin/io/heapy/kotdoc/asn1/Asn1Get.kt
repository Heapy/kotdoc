// Data.ASN1.Get, crypton-asn1-encoding 0.10.0.
// Vincent Hanquez 2010-2013; Lennart Kolmodin, Galois Inc. 2009.
// Full notice: licenses/crypton-asn1-encoding-BSD-3-Clause.txt.
package io.heapy.kotdoc.asn1

internal data class Asn1GetState(val input: ByteArray, val position: ULong, val buffer: ByteArray? = null)
internal sealed interface Asn1GetResult<out A> {
    data class Done<A>(val value: A, val state: Asn1GetState) : Asn1GetResult<A>
    data class Failed(val message: String, val state: Asn1GetState) : Asn1GetResult<Nothing> {
        fun description() = "${state.position}:$message"
    }
    class Partial<A>(val feed: (ByteArray) -> Asn1GetResult<A>) : Asn1GetResult<A>
}
/** Result continuations retain input, recovery buffer and the Word64 position.
 * Choice intentionally keeps the failed branch's position, as upstream does. */
internal class Asn1Get<A>(internal val run: (Asn1GetState) -> Asn1GetResult<A>) {
    fun <B> flatMap(next: (A) -> Asn1Get<B>): Asn1Get<B> = Asn1Get { initial ->
        fun bind(result: Asn1GetResult<A>): Asn1GetResult<B> = when (result) {
            is Asn1GetResult.Done -> next(result.value).run(result.state)
            is Asn1GetResult.Failed -> result
            is Asn1GetResult.Partial -> Asn1GetResult.Partial { bind(result.feed(it)) }
        }
        bind(run(initial))
    }
    fun <B> map(f: (A) -> B): Asn1Get<B> = flatMap { pure(f(it)) }
    fun orElse(other: Asn1Get<A>): Asn1Get<A> = Asn1Get { initial ->
        fun choose(result: Asn1GetResult<A>): Asn1GetResult<A> = when (result) {
            is Asn1GetResult.Done -> result
            is Asn1GetResult.Partial -> Asn1GetResult.Partial { choose(result.feed(it)) }
            is Asn1GetResult.Failed -> other.run(result.state.copy(input = initial.input + (result.state.buffer ?: byteArrayOf()),
                buffer = if (initial.buffer != null && result.state.buffer != null) initial.buffer + result.state.buffer else null))
        }
        choose(run(initial.copy(buffer = byteArrayOf())))
    }
    fun start(bytes: ByteArray, position: ULong = 0u) = run(Asn1GetState(bytes.copyOf(), position))
    companion object {
        fun <A> pure(value: A): Asn1Get<A> = Asn1Get { Asn1GetResult.Done(value, it) }
        fun <A> fail(message: String): Asn1Get<A> = Asn1Get { Asn1GetResult.Failed("Failed reading: $message", it) }
        fun bytes(count: Long): Asn1Get<ByteArray> = Asn1Get { initial ->
            fun ensure(state: Asn1GetState): Asn1GetResult<ByteArray> = if (count <= 0) Asn1GetResult.Done(byteArrayOf(), state)
                else if (state.input.size.toLong() >= count) Asn1GetResult.Done(state.input.copyOfRange(0, count.toInt()), state.copy(input = state.input.copyOfRange(count.toInt(), state.input.size), position = state.position + count.toULong()))
                else Asn1GetResult.Partial { more -> if (more.isEmpty()) Asn1GetResult.Failed("too few bytes", state)
                    else ensure(state.copy(input = state.input + more, buffer = state.buffer?.plus(more))) }
            ensure(initial)
        }
        val word8: Asn1Get<Int> = bytes(1).map { it[0].toInt() and 255 }
    }
}
