package io.heapy.kotdoc.parsing

import java.util.ArrayDeque
import java.util.concurrent.locks.ReentrantLock

/** A shared call-by-need value. Composition is interpreted with an explicit
 * stack; callbacks return Deferred values instead of recursively forcing them. */
class Deferred<out A> private constructor(private val cell: Cell) {
    private sealed interface Outcome {
        data class Value(val value: Any?) : Outcome
        data class Failed(val error: Throwable) : Outcome
    }
    private sealed interface Node {
        data class Later(val body: () -> Any?) : Node
        data class Suspend(val body: () -> Cell) : Node
        data class Map(val child: Cell,val function: (Any?) -> Any?) : Node
        data class Bind(val child: Cell,val function: (Any?) -> Cell) : Node
    }
    private class Cell(var node: Node?,@Volatile var outcome: Outcome? = null) {
        val lock = ReentrantLock()
    }
    private sealed interface Frame {
        val parent: Cell
        data class Return(override val parent: Cell) : Frame
        data class Map(override val parent: Cell,val function: (Any?) -> Any?) : Frame
        data class Bind(override val parent: Cell,val function: (Any?) -> Cell) : Frame
    }
    @Suppress("UNCHECKED_CAST")
    fun value(): A {
        val frames = ArrayDeque<Frame>()
        val held = ArrayDeque<Cell>()
        var current = cell
        var result: Outcome? = null
        fun complete(parent: Cell,outcome: Outcome) {
            parent.outcome = outcome
            parent.node = null // Release the evaluated closure and its environment.
            check(held.pop() === parent)
            parent.lock.unlock()
        }
        try {
            while (true) {
                if (result == null) {
                    result = current.outcome
                    if (result == null) {
                        if (current.lock.isHeldByCurrentThread) error("<<loop>>")
                        current.lock.lock();held.push(current)
                        val cached = current.outcome
                        if (cached != null) {
                            held.pop();current.lock.unlock();result=cached
                        } else when (val node = checkNotNull(current.node)) {
                            is Node.Later -> {
                                result=Outcome.Value(node.body());complete(current,checkNotNull(result))
                            }
                            is Node.Suspend -> {
                                val child=node.body();frames.push(Frame.Return(current));current=child
                            }
                            is Node.Map -> {
                                frames.push(Frame.Map(current,node.function));current=node.child
                            }
                            is Node.Bind -> {
                                frames.push(Frame.Bind(current,node.function));current=node.child
                            }
                        }
                    }
                }
                val ready = result ?: continue
                if (ready is Outcome.Failed) throw ready.error
                val value = (ready as Outcome.Value).value
                if (frames.isEmpty()) return value as A
                when (val frame=frames.pop()) {
                    is Frame.Return -> complete(frame.parent,ready)
                    is Frame.Map -> {
                        result=Outcome.Value(frame.function(value));complete(frame.parent,checkNotNull(result))
                    }
                    is Frame.Bind -> {
                        val child=frame.function(value);frames.push(Frame.Return(frame.parent));current=child;result=null
                    }
                }
            }
        } catch (error: Throwable) {
            val failed=Outcome.Failed(error)
            while (held.isNotEmpty()) complete(held.peek(),failed)
            throw error
        }
    }
    /** The callback chooses whether to demand this value. */
    fun <B> map(function: (Deferred<A>) -> Deferred<B>): Deferred<B> = defer { function(this) }
    @Suppress("UNCHECKED_CAST")
    fun <B> mapValue(function: (A) -> B): Deferred<B> = Deferred(Cell(Node.Map(cell) { function(it as A) }))
    @Suppress("UNCHECKED_CAST")
    fun <B> flatMapValue(function: (A) -> Deferred<B>): Deferred<B> = Deferred(Cell(Node.Bind(cell) { function(it as A).cell }))
    companion object {
        fun <A> now(value: A): Deferred<A> = Deferred(Cell(null,Outcome.Value(value)))
        fun <A> later(body: () -> A): Deferred<A> = Deferred(Cell(Node.Later(body)))
        fun <A> defer(body: () -> Deferred<A>): Deferred<A> = Deferred(Cell(Node.Suspend { body().cell }))
    }
}
