// Attoparsec 0.14.4 Text/Internal/Combinator operational semantics.
// Copyright Bryan O'Sullivan and contributors; BSD-3-Clause, licenses/.
package io.heapy.kotdoc.parsing.attoparsec

import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars as Chars
import io.heapy.kotdoc.parsing.Deferred
import io.heapy.kotdoc.parsing.Parsec

/** Incremental Text primitives/combinators used by Pandoc URI parsing.
 * Choice always backtracks; a Partial continuation is immutable and branchable.
 * Result maps and list elements preserve call-by-need through Deferred values. */
class TextParser<A> private constructor(private val node: Node) {
    private sealed interface Node {
        class Pure(val value: Deferred<Any?>): Node
        class Bind(val first: Node,val next: (Deferred<Any?>)->Node): Node
        class Map(val first: Node,val f: (Deferred<Any?>)->Deferred<Any?>): Node
        class Choice(val first: Node,val second: Node): Node
        class Label(val first: Node,val name: CharacterString): Node
        class Many(val first: Node,val collect: Boolean): Node
        class Look(val first: Node): Node
        class Match(val first: Node): Node
        class Suspend(val body: ()->Node): Node
        class Satisfy(val predicate: (Int)->Boolean): Node
        class Literal(val points: IntArray,val read: Int=0): Node
        class Take(val predicate: (Int)->Boolean,val minimum: Int,val start: Int = -1): Node
        class Failure(val message: CharacterString): Node
        data object End: Node
    }
    private class Chain<T>(val head: T,val tail: Chain<T>?)
    private sealed interface Frame {
        class Bind(val next: (Deferred<Any?>)->Node): Frame
        class Map(val f: (Deferred<Any?>)->Deferred<Any?>): Frame
        class Choice(val second: Node,val position: Int): Frame
        class Label(val name: CharacterString): Frame
        class Many(val first: Node,val start: Int,val items: Chain<Deferred<Any?>>?,val collect: Boolean): Frame
        class Look(val start: Int): Frame
        class Match(val start: Int): Frame
    }
    private class Error(val contexts: List<CharacterString>,val message: CharacterString)
    private class Snapshot(val input: TextRope,val position: Int,val node: Node,val frames: Chain<Frame>?)

    fun parse(input: String): AttoResult<A> = run(TextRope.from(input),false)
    /** Complete input, with the same residual input as parseOnly's underlying run. */
    fun parseComplete(input: String): AttoResult<A> = run(TextRope.from(input),true)
    @Suppress("UNCHECKED_CAST")
    private fun run(input: TextRope,complete: Boolean): AttoResult<A> = Machine(input,0,node,null,complete).run() as AttoResult<A>
    fun <B> flatMapDeferred(next: (Deferred<A>)->TextParser<B>): TextParser<B> = TextParser(Node.Bind(node) { @Suppress("UNCHECKED_CAST") next(it as Deferred<A>).node })
    fun <B> flatMap(next: (A)->TextParser<B>): TextParser<B> = flatMapDeferred { next(it.value()) }
    fun <B> mapDeferred(f: (Deferred<A>)->Deferred<B>): TextParser<B> = TextParser(Node.Map(node) { @Suppress("UNCHECKED_CAST") f(it as Deferred<A>) })
    fun <B> map(f: (A)->B): TextParser<B> = mapDeferred { it.mapValue(f) }
    infix fun or(other: TextParser<A>): TextParser<A> = TextParser(Node.Choice(node,other.node))
    fun label(name: CharacterString): TextParser<A> = TextParser(Node.Label(node,name))
    fun label(name: String)=label(Chars.fromString(name))
    fun <B> then(other: TextParser<B>): TextParser<B> = flatMapDeferred { other }
    fun <B> before(other: TextParser<B>): TextParser<A> = flatMapDeferred { a->other.mapDeferred { a } }
    fun attempt(): TextParser<A> = this
    fun lookAhead(): TextParser<A> = TextParser(Node.Look(node))
    fun match(): TextParser<AttoMatch<A>> = TextParser(Node.Match(node))
    fun many(): TextParser<List<A>> = TextParser(Node.Many(node,true))
    fun many1(): TextParser<List<A>> = flatMapDeferred { head->many().mapDeferred { tail->tail.mapValue { xs->
        object : AbstractList<A>() { override val size=xs.size+1;override fun get(index: Int): A { if(index !in indices)throw IndexOutOfBoundsException("index: $index, size: $size");return if(index==0)head.value() else xs[index-1] } }
    } } }
    fun skipMany(): TextParser<Unit> = TextParser(Node.Many(node,false))
    fun option(default: A): TextParser<A> = this or pure(default)

    private class Machine(var input: TextRope,var position: Int,var node: Node?,var frames: Chain<Frame>?,val complete: Boolean) {
        private var value: Deferred<Any?>?=null
        private var error: Error?=null
        private fun push(f: Frame) {frames=Chain(f,frames)}
        private fun fail(message: String) {error=Error(emptyList(),Chars.fromString(message));node=null;value=null}
        private fun success(result: Deferred<Any?>) {value=result;node=null;error=null}
        private fun partial(waiting: Node): AttoResult<Any?> {
            val snapshot=Snapshot(input,position,waiting,frames)
            return AttoResult.Partial { chunk->Machine(snapshot.input.append(chunk),snapshot.position,snapshot.node,snapshot.frames,chunk.isEmpty()).run() }
        }
        fun run(): AttoResult<Any?> {
            while(true) {
                when(val p=node) {
                    is Node.Pure->success(p.value)
                    is Node.Bind->{push(Frame.Bind(p.next));node=p.first;continue}
                    is Node.Map->{push(Frame.Map(p.f));node=p.first;continue}
                    is Node.Choice->{push(Frame.Choice(p.second,position));node=p.first;continue}
                    is Node.Label->{push(Frame.Label(p.name));node=p.first;continue}
                    is Node.Many->{push(Frame.Many(p.first,position,null,p.collect));node=p.first;continue}
                    is Node.Look->{push(Frame.Look(position));node=p.first;continue}
                    is Node.Match->{push(Frame.Match(position));node=p.first;continue}
                    is Node.Suspend->{node=p.body();continue}
                    is Node.Failure->{error=Error(emptyList(),p.message);value=null;node=null}
                    Node.End->if(position<input.size)fail("endOfInput") else if(complete)success(Deferred.now(Unit)) else return partial(p)
                    is Node.Satisfy->if(position==input.size) {
                        if(!complete)return partial(p)
                        fail("not enough input")
                    } else if(p.predicate(input[position]))success(Deferred.now(input[position++])) else fail("Failed reading: satisfy")
                    is Node.Literal->{
                        var read=p.read
                        while(read<p.points.size && position+read<input.size && input[position+read]==p.points[read])read++
                        when {
                            read==p.points.size->{val start=position;position+=read;val captured=input;val end=position;success(Deferred.later {captured.slice(start,end)})}
                            position+read<input.size->fail("string")
                            complete->fail("not enough input")
                            else->return partial(Node.Literal(p.points,read))
                        }
                    }
                    is Node.Take->{
                        val start=if(p.start<0)position else p.start
                        while(position<input.size && p.predicate(input[position]))position++
                        when {
                            position==input.size && !complete->return partial(Node.Take(p.predicate,p.minimum,start))
                            position-start<p.minimum->fail(if(position==input.size)"not enough input" else "Failed reading: takeWhile1")
                            else->{val captured=input;val end=position;success(Deferred.later {captured.slice(start,end)})}
                        }
                    }
                    null->Unit
                }
                val frame=frames
                if(frame==null) {
                    val e=error
                    return if(e!=null)AttoResult.Fail(input,position,e.contexts,e.message) else AttoResult.Done(input,position,checkNotNull(value))
                }
                frames=frame.tail
                val e=error
                if(e==null) {
                    val v=checkNotNull(value)
                    when(val f=frame.head) {
                        is Frame.Bind->{value=null;node=f.next(v)}
                        is Frame.Map->value=f.f(v)
                        is Frame.Choice,is Frame.Label->Unit
                        is Frame.Look->position=f.start
                        is Frame.Match->{val captured=input;val end=position;value=Deferred.now(AttoMatch(Deferred.later {captured.slice(f.start,end)},v))}
                        is Frame.Many->{push(Frame.Many(f.first,position,if(f.collect)Chain(v,f.items) else null,f.collect));value=null;node=f.first}
                    }
                } else when(val f=frame.head) {
                    is Frame.Choice->{position=f.position;error=null;node=f.second}
                    is Frame.Label->error=Error(listOf(f.name)+e.contexts,e.message)
                    is Frame.Many->{
                        position=f.start;error=null
                        value=if(!f.collect)Deferred.now(Unit) else Deferred.later {
                            val cells=ArrayList<Deferred<Any?>>();var items=f.items
                            while(items!=null) {cells.add(items.head);items=items.tail};cells.reverse()
                            object : AbstractList<Any?>() {override val size=cells.size;override fun get(index: Int): Any?=cells[index].value()}
                        }
                    }
                    else->Unit
                }
            }
        }
    }
    companion object {
        fun <A> pure(value: A): TextParser<A> = pureDeferred(Deferred.now(value))
        fun <A> pureDeferred(value: Deferred<A>): TextParser<A> = TextParser(Node.Pure(value))
        fun <A> defer(body: ()->TextParser<A>): TextParser<A> = TextParser(Node.Suspend {body().node})
        fun <A> fail(message: CharacterString): TextParser<A> = TextParser(Node.Failure(Chars.append(Chars.fromString("Failed reading: "),message)))
        fun <A> fail(message: String): TextParser<A> = fail(Chars.fromString(message))
        fun satisfy(predicate: (Int)->Boolean): TextParser<Int> = TextParser(Node.Satisfy(predicate))
        fun char(c: Int): TextParser<Int> = satisfy {it==c}.label(Parsec.showChar(c))
        fun char(c: Char)=char(c.code)
        fun string(text: String): TextParser<String> = TextParser(Node.Literal(TextRope.from(text).slice(0).codePoints().toArray()))
        fun asciiCI(text: String): TextParser<String> {
            var p=pure(Unit)
            for(c in text.codePoints().toArray()) {
                val one=when(c) {in 65..90->char(c) or char(c+32);in 97..122->char(c) or char(c-32);else->char(c)}
                p=p.then(one).map {Unit}
            }
            return p.match().mapDeferred {it.flatMapValue {m->m.text}}
        }
        fun takeWhile(predicate: (Int)->Boolean): TextParser<String> = TextParser(Node.Take(predicate,0))
        fun takeWhile1(predicate: (Int)->Boolean): TextParser<String> = TextParser(Node.Take(predicate,1))
        fun skipWhile(predicate: (Int)->Boolean): TextParser<Unit> = takeWhile(predicate).mapDeferred {Deferred.now(Unit)}
        fun endOfInput(): TextParser<Unit> = TextParser(Node.End)
    }
}
