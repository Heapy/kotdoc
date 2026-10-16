// Data.ASN1.BinaryEncoding.{Parse,Writer}, crypton-asn1-encoding 0.10.0.
// Vincent Hanquez 2010-2013; complete licenses/crypton-asn1-encoding-BSD-3-Clause.txt.
package io.heapy.kotdoc.asn1

import java.io.ByteArrayOutputStream

internal sealed interface Asn1Expectation {
    data class Header(val continuation: Asn1GetResult.Partial<Asn1Header>? = null) : Asn1Expectation
    data class Primitive(val length: ULong, val continuation: Asn1GetResult.Partial<ByteArray>? = null) : Asn1Expectation
}
class Asn1ParseState internal constructor(internal val ends: List<ULong?>, internal val expectation: Asn1Expectation, internal val position: ULong)
data class Asn1ParseCursor(val events: List<Asn1Event>, val state: Asn1ParseState)
object Asn1Raw {
    fun newParseState() = Asn1ParseState(emptyList(), Asn1Expectation.Header(), 0u)
    fun isParseDone(state: Asn1ParseState): Boolean = state.ends.isEmpty() && state.expectation is Asn1Expectation.Header && state.expectation.continuation == null
    fun putHeader(header: Asn1Header): ByteArray = Asn1Serialize.putHeader(header)
    fun runParseState(initial: Asn1ParseState, bytes: ByteArray): Asn1Result<Asn1ParseCursor> {
        var state = initial; var incoming = bytes.copyOf(); val events = mutableListOf<Asn1Event>()
        while (true) {
            if (incoming.isNotEmpty()) {
                when (val expected = state.expectation) {
                    is Asn1Expectation.Header -> when (val result = expected.continuation?.feed?.invoke(incoming) ?: Asn1Serialize.header.start(incoming, state.position)) {
                        is Asn1GetResult.Failed -> return Asn1Result.Failure(Asn1Error.ParsingHeaderFail(result.description()))
                        is Asn1GetResult.Partial -> { state = Asn1ParseState(state.ends, Asn1Expectation.Header(result), state.position); incoming = byteArrayOf() }
                        is Asn1GetResult.Done -> {
                            val header = result.value; incoming = result.state.input
                            when {
                                header.kind == Asn1Class.Universal && header.tag == 0L -> {
                                    if (state.ends.isEmpty()) state = Asn1ParseState(emptyList(), Asn1Expectation.Header(), result.state.position)
                                    else if (state.ends.first() != null) return Asn1Result.Failure(Asn1Error.StreamUnexpectedEOC)
                                    else { events.add(Asn1Event.ConstructionEnd); state = Asn1ParseState(state.ends.drop(1), Asn1Expectation.Header(), result.state.position) }
                                }
                                header.constructed -> {
                                    val end = when (val length = header.length) {
                                        is Asn1Length.Short -> result.state.position + length.value.toULong()
                                        is Asn1Length.LongForm -> result.state.position + length.value.toULong()
                                        Asn1Length.Indefinite -> null
                                    }
                                    events.add(Asn1Event.Header(header)); events.add(Asn1Event.ConstructionBegin)
                                    state = Asn1ParseState(listOf(end) + state.ends, Asn1Expectation.Header(), result.state.position)
                                }
                                header.length == Asn1Length.Indefinite -> return Asn1Result.Failure(Asn1Error.StreamInfinitePrimitive)
                                else -> {
                                    val length = when (val l = header.length) { is Asn1Length.Short -> l.value.toULong(); is Asn1Length.LongForm -> l.value.toULong() }
                                    events.add(Asn1Event.Header(header))
                                    state = if (length == 0uL) {
                                        events.add(Asn1Event.Primitive(byteArrayOf())); Asn1ParseState(state.ends, Asn1Expectation.Header(), result.state.position)
                                    } else Asn1ParseState(state.ends, Asn1Expectation.Primitive(length), result.state.position)
                                }
                            }
                        }
                    }
                    is Asn1Expectation.Primitive -> when (val result = expected.continuation?.feed?.invoke(incoming) ?: Asn1Get.bytes(expected.length.toLong()).start(incoming, state.position)) {
                        is Asn1GetResult.Failed -> return Asn1Result.Failure(Asn1Error.ParsingPartial)
                        is Asn1GetResult.Partial -> { state = Asn1ParseState(state.ends, Asn1Expectation.Primitive(expected.length, result), state.position); incoming = byteArrayOf() }
                        is Asn1GetResult.Done -> {
                            events.add(Asn1Event.Primitive(result.value)); incoming = result.state.input
                            state = Asn1ParseState(state.ends, Asn1Expectation.Header(), result.state.position)
                        }
                    }
                }
            }
            // terminateAugment runs after every step, including an empty feed.
            while (state.ends.isNotEmpty() && state.ends.first() != null) {
                val end = checkNotNull(state.ends.first())
                if (state.position > end) return Asn1Result.Failure(Asn1Error.StreamConstructionWrongSize)
                if (state.position != end) break
                events.add(Asn1Event.ConstructionEnd); state = Asn1ParseState(state.ends.drop(1), state.expectation, state.position)
            }
            if (incoming.isEmpty()) return Asn1Result.Success(Asn1ParseCursor(events, state))
        }
    }
    fun parse(bytes: ByteArray): Asn1Result<List<Asn1Event>> = when (val result = runParseState(newParseState(), bytes)) {
        is Asn1Result.Failure -> result
        is Asn1Result.Success -> if (isParseDone(result.value.state)) Asn1Result.Success(result.value.events) else Asn1Result.Failure(Asn1Error.ParsingPartial)
    }
    fun parseChunks(chunks: List<ByteArray>): Asn1Result<List<Asn1Event>> = parseChunks(chunks.asSequence())
    fun parseChunks(chunks: Sequence<ByteArray>): Asn1Result<List<Asn1Event>> {
        var state = newParseState(); val events = mutableListOf<Asn1Event>()
        // Lazy ByteString drops empty chunks before parseLBS's fold.
        for (chunk in chunks) if (chunk.isNotEmpty()) when (val result = runParseState(state, chunk)) {
            is Asn1Result.Failure -> return result
            is Asn1Result.Success -> { state = result.value.state; events.addAll(result.value.events) }
        }
        return if (isParseDone(state)) Asn1Result.Success(events) else Asn1Result.Failure(Asn1Error.ParsingPartial)
    }
    fun write(events: List<Asn1Event>): ByteArray = ByteArrayOutputStream().also { out -> writeChunks(events).forEach(out::write) }.toByteArray()
    fun writeChunks(events: List<Asn1Event>): List<ByteArray> = buildList {
        val stack = mutableListOf<Boolean>()
        for (event in events) when (event) {
            is Asn1Event.Header -> { add(putHeader(event.header)); if (event.header.constructed) stack.add(event.header.length == Asn1Length.Indefinite) }
            is Asn1Event.Primitive -> if (event.bytes.isNotEmpty()) add(event.bytes)
            Asn1Event.ConstructionBegin -> Unit
            Asn1Event.ConstructionEnd -> {
                check(stack.isNotEmpty()) { "malformed stream: end before construction" }; stack.removeAt(stack.lastIndex)
                // Original toBs ConstructionEnd is empty, also for indefinite lengths.
            }
        }
    }
}
