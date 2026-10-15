// Text.Parsec.Prim 3.1.18.0; (c) Daan Leijen 1999-2001, Paolo Martini 2007.
// BSD-2-Clause; licenses/Parsec-BSD-2-Clause.txt.
package io.heapy.kotdoc.parsing

import java.util.ArrayDeque

/** State position used when lookAhead replaces the successful parser error. */
interface PositionedParserState { val position: SourcePos }

data class ParserReply<S, out A>(val success: Boolean, val value: A?, val state: S, val consumed: Boolean, val error: ParseError)

/** Parsec's consumed/empty continuations, interpreted with an explicit work stack. */
internal class ParserMachine<S : PositionedParserState, A> private constructor(private val node: Node<S>) {
    private sealed interface Node<S> {
        data class Primitive<S>(val run: (S) -> ParserReply<S, Any?>) : Node<S>
        data class Bind<S>(val first: Node<S>, val next: (Any?) -> Node<S>) : Node<S>
        data class Map<S>(val child: Node<S>, val f: (Any?) -> Any?) : Node<S>
        data class Choice<S>(val first: Node<S>, val second: Node<S>) : Node<S>
        data class Try<S>(val child: Node<S>) : Node<S>
        data class Look<S>(val child: Node<S>) : Node<S>
        data class Label<S>(val child: Node<S>, val labels: List<String>) : Node<S>
        data class Many<S>(val child: Node<S>, val collect: Boolean) : Node<S>
        data class Deferred<S>(val child: () -> Node<S>) : Node<S>
    }
    private sealed interface Frame<S> {
        data class BindFirst<S>(val next: (Any?) -> Node<S>) : Frame<S>
        data class BindSecond<S>(val first: ParserReply<S, Any?>) : Frame<S>
        data class Map<S>(val f: (Any?) -> Any?) : Frame<S>
        data class ChoiceFirst<S>(val second: Node<S>, val state: S) : Frame<S>
        data class ChoiceSecond<S>(val error: ParseError) : Frame<S>
        class Try<S> : Frame<S>
        data class Look<S>(val state: S) : Frame<S>
        data class Label<S>(val labels: List<String>) : Frame<S>
        data class Many<S>(val node: Node.Many<S>, var state: S, val values: MutableList<Any?>, var consumed: Boolean = false) : Frame<S>
    }
    @Suppress("UNCHECKED_CAST")
    fun run(initial: S): ParserReply<S, A> {
        val stack = ArrayDeque<Frame<S>>()
        var current: Node<S>? = node; var state = initial; var result: ParserReply<S, Any?>? = null
        while (true) {
            when (val n = current) {
                is Node.Primitive -> { result = n.run(state); current = null }
                is Node.Bind -> { stack.push(Frame.BindFirst(n.next)); current = n.first }
                is Node.Map -> { stack.push(Frame.Map(n.f)); current = n.child }
                is Node.Choice -> { stack.push(Frame.ChoiceFirst(n.second, state)); current = n.first }
                is Node.Try -> { stack.push(Frame.Try()); current = n.child }
                is Node.Look -> { stack.push(Frame.Look(state)); current = n.child }
                is Node.Label -> { stack.push(Frame.Label(n.labels)); current = n.child }
                is Node.Many -> { stack.push(Frame.Many(n, state, mutableListOf())); current = n.child }
                is Node.Deferred -> current = n.child()
                null -> {
                    val r = checkNotNull(result)
                    if (stack.isEmpty()) return r as ParserReply<S, A>
                    when (val frame = stack.pop()) {
                        is Frame.BindFirst -> if (r.success) {
                            if (r.consumed || r.error.messages.isNotEmpty()) stack.push(Frame.BindSecond(r))
                            state = r.state; current = frame.next(r.value)
                        }
                        is Frame.BindSecond -> if (!r.consumed) result = r.copy(
                            consumed = frame.first.consumed,
                            error = if (frame.first.error.messages.isEmpty()) r.error else frame.first.error.merge(r.error))
                        is Frame.Map -> if (r.success) result = r.copy(value = frame.f(r.value))
                        is Frame.ChoiceFirst -> if (!r.success && !r.consumed) {
                            stack.push(Frame.ChoiceSecond(r.error)); state = frame.state; current = frame.second
                        }
                        is Frame.ChoiceSecond -> if (!r.consumed) result = r.copy(error = frame.error.merge(r.error))
                        is Frame.Try -> if (!r.success) result = r.copy(consumed = false)
                        is Frame.Look -> if (r.success) result = r.copy(state = frame.state, consumed = false, error = ParseError(frame.state.position))
                        is Frame.Label -> if (!r.consumed && (!r.success || r.error.messages.isNotEmpty())) result = r.copy(error = r.error.labels(frame.labels))
                        is Frame.Many -> if (r.success) {
                            check(r.consumed) { "Text.ParserCombinators.Parsec.Prim.many: combinator 'many' is applied to a parser that accepts an empty string." }
                            if (frame.node.collect) frame.values.add(r.value)
                            frame.state = r.state; frame.consumed = true
                            stack.push(frame); state = r.state; current = frame.node.child
                        } else if (!r.consumed) result = ParserReply(true,
                            if (frame.node.collect) frame.values.toList() else Unit, frame.state, frame.consumed, r.error)
                    }
                }
            }
        }
    }
    @Suppress("UNCHECKED_CAST")
    fun <B> flatMap(next: (A) -> ParserMachine<S, B>): ParserMachine<S, B> = ParserMachine(Node.Bind(node) { next(it as A).node })
    @Suppress("UNCHECKED_CAST")
    fun <B> map(f: (A) -> B): ParserMachine<S, B> = ParserMachine(Node.Map(node) { f(it as A) })
    infix fun or(other: ParserMachine<S, A>): ParserMachine<S, A> = ParserMachine(Node.Choice(node, other.node))
    fun attempt(): ParserMachine<S, A> = ParserMachine(Node.Try(node))
    fun lookAhead(): ParserMachine<S, A> = ParserMachine(Node.Look(node))
    fun label(text: String): ParserMachine<S, A> = ParserMachine(Node.Label(node, listOf(text)))
    fun many(): ParserMachine<S, List<A>> = ParserMachine(Node.Many(node, true))
    fun skipMany(): ParserMachine<S, Unit> = ParserMachine(Node.Many(node, false))
    companion object {
        fun <S : PositionedParserState, A> primitive(f: (S) -> ParserReply<S, A>): ParserMachine<S, A> = ParserMachine(Node.Primitive(f))
        fun <S : PositionedParserState, A> defer(f: () -> ParserMachine<S, A>): ParserMachine<S, A> = ParserMachine(Node.Deferred { f().node })
    }
}
