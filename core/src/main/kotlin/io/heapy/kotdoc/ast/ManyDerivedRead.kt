// GHC ReadP/ReadPrec: Copyright (c) The University of Glasgow 2002.
// ReadP was originally written by Koen Claessen. GHC Read: Glasgow 1994–2002.
// Exact headers: licenses/GHC-Read-Show-Attribution.txt; full base/ghc-prim BSD licenses retained.
// pandoc-types: Copyright (c) 2010–2023 John MacFarlane. Seq: Ross Paterson 2005,
// Louis Wasserman 2009; Bertram Felgenhauer, David Feuer, Ross Paterson,
// Milan Straka 2014. Exact upstream sources and BSD notices retained.
package io.heapy.kotdoc.ast

import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars
import io.heapy.kotdoc.parsing.ParserCharacters
import io.heapy.kotdoc.parsing.RegexCharacters

/** Shared untouched Haskell-Char input, including partial/infinite spines.
 * CharacterString/String constructors are finite JVM bridges. Custom ReadS may
 * return any remainder; it need not be a suffix of the supplied input. */
class ManyReadInput private constructor(value:()->GenericList<Int>,val offset:Long):GenericRepRoot {
    private val source=GenericMemo(value)
    constructor(value:CharacterString):this({GenericList.view(value.points().asList())},0)
    constructor(value:String):this(CharacterStringChars.fromString(value))
    constructor(value:()->GenericList<Int>):this(value,0)
    internal fun view():GenericList.Step<Int> = source.get().step()
    override fun forceGenericRepRoot(){view()}
    fun point(index:Int=0):Int? = point(index.toLong())
    fun point(index:Long):Int? {
        require(index>=0);var rest=source.get();var n=index
        while(true)when(val node=rest.step()) {
            GenericList.Step.Nil->return null
            is GenericList.Step.Cons->{if(n--==0L)return node.head().also{require(it in 0..0x10ffff)};rest=node.tail()}
        }
    }
    fun drop(count:Int):ManyReadInput {
        require(count>=0)
        return ManyReadInput({GenericList.defer {
            var rest=source.get();repeat(count){when(val node=rest.step()){GenericList.Step.Nil->return@defer GenericList.empty();is GenericList.Step.Cons->rest=node.tail()}};rest
        }},offset+count)
    }
    fun sourceCharacters():GenericList<Int> = GenericList.defer {source.get()}
    fun characters()=CharacterString(source.get().toIntArray())
    fun isSpaces():Boolean {var rest=source.get();while(true)when(val node=rest.step()){GenericList.Step.Nil->return true;is GenericList.Step.Cons->{if(!ParserCharacters.isSpace(node.head()))return false;rest=node.tail()}}}
}
class ManyReadAlternative<A>(value:()->A,remainder:()->ManyReadInput) {
    val valueSource:ManyDerivedThunk<A> = manyDerivedThunk(value)
    val remainderSource:ManyDerivedThunk<ManyReadInput> = manyDerivedThunk(remainder)
    fun value():A=valueSource()
    fun remainder():ManyReadInput=remainderSource()
}
private sealed interface ManyReadNode<A> {
    class Get<A>(val next:(()->Int)->ManyReadP<A>):ManyReadNode<A>
    class Look<A>(val next:(ManyReadInput)->ManyReadP<A>):ManyReadNode<A>
    class Fail<A>:ManyReadNode<A>
    class Result<A>(val value:()->A,val rest:ManyReadP<A>):ManyReadNode<A>
    class Final<A>(val values:ManyReadStream<A>):ManyReadNode<A>
}
private sealed interface ManyReadStep<A> {
    class Nil<A>:ManyReadStep<A>
    class Cons<A> private constructor(val first:GenericMemo<ManyReadAlternative<A>>,tail:()->ManyReadStream<A>):ManyReadStep<A> {
        constructor(head:()->ManyReadAlternative<A>,tail:()->ManyReadStream<A>):this(GenericMemo(head),tail)
        private val rest=GenericMemo(tail)
        fun withTail(tail:()->ManyReadStream<A>)=Cons(first,tail)
        fun head()=first.get();fun tail()=rest.get()
    }
}
private sealed interface ManyPrecOp {
    class Pure(val value:()->Any?):ManyPrecOp
    data object Fail:ManyPrecOp
    data object Get:ManyPrecOp
    data object Look:ManyPrecOp
    class Bind(val source:ManyPrecOp,val next:(()->Any?)->ManyPrecOp):ManyPrecOp
    class Choice(val left:ManyPrecOp,val right:ManyPrecOp):ManyPrecOp
    class Context(val source:ManyPrecOp,val change:(()->Long)->(()->Long)?):ManyPrecOp
    class Delay(val source:GenericMemo<ManyPrecOp>):ManyPrecOp
    class ReadS(val source:(()->Long,ManyReadInput)->GenericList<ManyReadAlternative<Any?>>):ManyPrecOp
}
private class ManyReadContinuation(val next:(()->Any?)->ManyPrecOp,val precedence:()->Long,val rest:ManyReadContinuation?)
private sealed interface ManyReadProgram {
    class Cps(val source:ManyPrecOp,val precedence:()->Long,val continuation:ManyReadContinuation?):ManyReadProgram
    class Choice(val left:ManyReadP<Any?>,val right:ManyReadP<Any?>):ManyReadProgram
    class Final(val values:ManyReadStream<Any?>):ManyReadProgram
    class Deferred(val source:GenericMemo<ManyReadStream<Any?>>):ManyReadProgram
    class Input(val source:GenericList<ManyReadAlternative<Any?>>):ManyReadProgram
    class Append(val left:ManyReadStream<Any?>,val right:GenericMemo<ManyReadStream<Any?>>):ManyReadProgram
    class Flat(val source:ManyReadStream<Any?>,val next:(ManyReadAlternative<Any?>)->ManyReadStream<Any?>):ManyReadProgram
    class Run(val parser:ManyReadP<Any?>,val input:ManyReadInput):ManyReadProgram
}
private class ManyReadCell(val program:ManyReadProgram) {
    val lock=java.util.concurrent.locks.ReentrantLock()
    @Volatile var result:Result<Any>?=null
}
/** Explicit heap frames evaluate both ReadP nodes and Final streams. This keeps
 * CPS parser depth and ReadS backtracking off the JVM call stack. */
private object ManyReadMachine {
    private class Frame(val cell:ManyReadCell) {
        var program=cell.program;var locked=false;var stage=0
        var child:ManyReadCell?=null;var first:Any?=null
    }
    @Suppress("UNCHECKED_CAST")
    fun resolve(cell:ManyReadCell):Any {
        cell.result?.let{return it.getOrThrow()}
        val work=ArrayDeque<Frame>();work.addLast(Frame(cell))
        try {
            while(work.isNotEmpty()) {
                val frame=work.last();val target=frame.cell
                if(!frame.locked){target.lock.lock();frame.locked=true;if(target.result!=null){work.removeLast();target.lock.unlock();continue}}
                fun ask(child:ManyReadCell,stage:Int){frame.child=child;frame.stage=stage;if(child.result==null)work.addLast(Frame(child))}
                fun answer():Any=frame.child!!.result!!.getOrThrow()
                var result:Any?=null
                when(val p=frame.program) {
                    is ManyReadProgram.Cps->{
                        var op=p.source;var prec=p.precedence;var continuation=p.continuation
                        while(true)when(val current=op) {
                            is ManyPrecOp.Pure->{val k=continuation;if(k==null){result=ManyReadNode.Result(current.value,ManyReadP.fail());break};op=k.next(current.value);prec=k.precedence;continuation=k.rest}
                            ManyPrecOp.Fail->{result=ManyReadNode.Fail<Any?>();break}
                            ManyPrecOp.Get->{val n=prec;val k=continuation;result=ManyReadNode.Get<Any?>{c->ManyReadP.cps(ManyPrecOp.Pure(manyDerivedThunk(c)),n,k)};break}
                            ManyPrecOp.Look->{val n=prec;val k=continuation;result=ManyReadNode.Look<Any?>{s->ManyReadP.cps(ManyPrecOp.Pure(manyDerivedThunk{s}),n,k)};break}
                            is ManyPrecOp.Bind->{continuation=ManyReadContinuation(current.next,prec,continuation);op=current.source}
                            is ManyPrecOp.Choice->{val choice=ManyReadP.cps<Any?>(current.left,prec,continuation).plus(ManyReadP.cps(current.right,prec,continuation));frame.program=choice.cell.program;frame.stage=0;break}
                            is ManyPrecOp.Context->{val next=current.change(prec);if(next==null){result=ManyReadNode.Fail<Any?>();break};prec=next;op=current.source}
                            is ManyPrecOp.Delay->op=current.source.get()
                            is ManyPrecOp.ReadS->{val n=prec;val k=continuation;result=ManyReadNode.Look<Any?>{s->ManyReadP.final(ManyReadStream.flat(ManyReadStream.input(current.source(n,s))){r->ManyReadStream.run(ManyReadP.cps(ManyPrecOp.Pure(r.valueSource),n,k),ManyReadInput{r.remainderSource().sourceCharacters()})})};break}
                        }
                    }
                    is ManyReadProgram.Choice->when(frame.stage) {
                        0->ask(p.left.cell,1)
                        1->{val left=answer() as ManyReadNode<Any?>;if(left is ManyReadNode.Result)result=ManyReadNode.Result(left.value,left.rest.plus(p.right))else{frame.first=left;ask(p.right.cell,2)}}
                        else->{val a=frame.first as ManyReadNode<Any?>;val b=answer() as ManyReadNode<Any?>
                            result=when {
                                a is ManyReadNode.Get && b is ManyReadNode.Get->ManyReadNode.Get<Any?>{a.next(it).plus(b.next(it))}
                                b is ManyReadNode.Result->ManyReadNode.Result(b.value,p.left.plus(b.rest))
                                a is ManyReadNode.Fail->b
                                b is ManyReadNode.Fail->a
                                a is ManyReadNode.Final && b is ManyReadNode.Final->ManyReadNode.Final(ManyReadStream.append(a.values){b.values})
                                a is ManyReadNode.Final->ManyReadNode.Look<Any?>{s->ManyReadP.final(ManyReadStream.append(a.values){ManyReadStream.run(p.right,s)})}
                                b is ManyReadNode.Final->ManyReadNode.Look<Any?>{s->ManyReadP.final(ManyReadStream.append(ManyReadStream.run(p.left,s)){b.values})}
                                a is ManyReadNode.Look && b is ManyReadNode.Look->ManyReadNode.Look<Any?>{a.next(it).plus(b.next(it))}
                                a is ManyReadNode.Look->ManyReadNode.Look<Any?>{a.next(it).plus(p.right)}
                                b is ManyReadNode.Look->ManyReadNode.Look<Any?>{p.left.plus(b.next(it))}
                                else->error("Unreachable ReadP choice")
                            }
                        }
                    }
                    is ManyReadProgram.Final->if(frame.stage==0)ask(p.values.cell,1)else result=when(answer()){is ManyReadStep.Nil<*>->ManyReadNode.Fail<Any?>();else->ManyReadNode.Final(p.values)}
                    is ManyReadProgram.Deferred->if(frame.stage==0)ask(p.source.get().cell,1)else result=answer()
                    is ManyReadProgram.Input->result=when(val s=p.source.step()){GenericList.Step.Nil->ManyReadStep.Nil<Any?>();is GenericList.Step.Cons->ManyReadStep.Cons(s::head){ManyReadStream.input(s.tail())}}
                    is ManyReadProgram.Append->when(frame.stage) {
                        0->ask(p.left.cell,1)
                        1->when(val s=answer() as ManyReadStep<Any?>){is ManyReadStep.Nil->ask(p.right.get().cell,2);is ManyReadStep.Cons->result=s.withTail{ManyReadStream.append(ManyReadStream.defer(s::tail),p.right::get)}}
                        else->result=answer()
                    }
                    is ManyReadProgram.Flat->when(frame.stage) {
                        0->ask(p.source.cell,1)
                        1->when(val s=answer() as ManyReadStep<Any?>){is ManyReadStep.Nil->result=ManyReadStep.Nil<Any?>();is ManyReadStep.Cons->{frame.first=s;ask(p.next(s.head()).cell,2)}}
                        2->{val source=frame.first as ManyReadStep.Cons<Any?>;when(val mapped=answer() as ManyReadStep<Any?>){is ManyReadStep.Nil->ask(ManyReadStream.flat(ManyReadStream.defer(source::tail),p.next).cell,3);is ManyReadStep.Cons->result=mapped.withTail{ManyReadStream.append(ManyReadStream.defer(mapped::tail)){ManyReadStream.flat(ManyReadStream.defer(source::tail),p.next)}}}}
                        else->result=answer()
                    }
                    is ManyReadProgram.Run->when(frame.stage) {
                        0->ask(p.parser.cell,1)
                        1->when(val node=answer() as ManyReadNode<Any?>) {
                            is ManyReadNode.Fail->result=ManyReadStep.Nil<Any?>()
                            is ManyReadNode.Get->when(val input=p.input.view()) {
                                GenericList.Step.Nil->result=ManyReadStep.Nil<Any?>()
                                is GenericList.Step.Cons->{frame.program=ManyReadProgram.Run(node.next(manyDerivedThunk(input::head)),p.input.drop(1));frame.stage=0}
                            }
                            is ManyReadNode.Look->{frame.program=ManyReadProgram.Run(node.next(p.input),p.input);frame.stage=0}
                            is ManyReadNode.Result->result=ManyReadStep.Cons({ManyReadAlternative(node.value){p.input}}){ManyReadStream.run(node.rest,p.input)}
                            is ManyReadNode.Final->ask(node.values.cell,2)
                        }
                        else->result=answer()
                    }
                }
                if(result!=null){target.result=Result.success(result);work.removeLast();target.lock.unlock()}
            }
        }catch(error:Throwable){
            while(work.isNotEmpty()){val f=work.removeLast();if(f.locked){f.cell.result=Result.failure(error);f.cell.lock.unlock()}}
            throw error
        }
        return cell.result!!.getOrThrow()
    }
}
private class ManyReadP<A>(val cell:ManyReadCell) {
    @Suppress("UNCHECKED_CAST") fun plus(other:ManyReadP<A>)=ManyReadP<A>(ManyReadCell(ManyReadProgram.Choice(this as ManyReadP<Any?>,other as ManyReadP<Any?>)))
    companion object {
        fun <A> cps(op:ManyPrecOp,p:()->Long,k:ManyReadContinuation?)=ManyReadP<A>(ManyReadCell(ManyReadProgram.Cps(op,p,k)))
        fun <A> fail()=cps<A>(ManyPrecOp.Fail,{0},null)
        @Suppress("UNCHECKED_CAST") fun <A> final(values:ManyReadStream<A>)=ManyReadP<A>(ManyReadCell(ManyReadProgram.Final(values as ManyReadStream<Any?>)))
    }
}
private class ManyReadStream<A>(val cell:ManyReadCell) {
    @Suppress("UNCHECKED_CAST") fun asList():GenericList<ManyReadAlternative<A>> = GenericList.defer {
        when(val s=ManyReadMachine.resolve(cell) as ManyReadStep<A>){is ManyReadStep.Nil->GenericList.empty();is ManyReadStep.Cons->GenericList.cons(s::head){s.tail().asList()}}
    }
    companion object {
        @Suppress("UNCHECKED_CAST") fun <A> defer(source:()->ManyReadStream<A>)=ManyReadStream<A>(ManyReadCell(ManyReadProgram.Deferred(GenericMemo{source() as ManyReadStream<Any?>})))
        @Suppress("UNCHECKED_CAST") fun <A> input(source:GenericList<ManyReadAlternative<A>>)=ManyReadStream<A>(ManyReadCell(ManyReadProgram.Input(source as GenericList<ManyReadAlternative<Any?>>)))
        @Suppress("UNCHECKED_CAST") fun <A> append(left:ManyReadStream<A>,right:()->ManyReadStream<A>)=ManyReadStream<A>(ManyReadCell(ManyReadProgram.Append(left as ManyReadStream<Any?>,GenericMemo{right() as ManyReadStream<Any?>})))
        @Suppress("UNCHECKED_CAST") fun <A,B> flat(source:ManyReadStream<A>,next:(ManyReadAlternative<A>)->ManyReadStream<B>)=ManyReadStream<B>(ManyReadCell(ManyReadProgram.Flat(source as ManyReadStream<Any?>){next(it as ManyReadAlternative<A>) as ManyReadStream<Any?>}))
        @Suppress("UNCHECKED_CAST") fun <A> run(parser:ManyReadP<A>,input:ManyReadInput)=ManyReadStream<A>(ManyReadCell(ManyReadProgram.Run(parser as ManyReadP<Any?>,input)))
    }
}
/** Source ReadPrec operations retain CPS continuation, symmetric-choice and
 * ReadS Final ordering, with explicit frames rather than JVM recursion. */
class ManyReadPrec<A> private constructor(private val operation:ManyPrecOp) {
    fun results(precedence:Long,input:ManyReadInput):GenericList<ManyReadAlternative<A>> =
        resultsLazy({precedence},input)
    fun resultsLazy(precedence:()->Long,input:ManyReadInput):GenericList<ManyReadAlternative<A>> = ManyReadStream.run(ManyReadP.cps<A>(operation,manyDerivedThunk(precedence),null),input).asList()
    @Suppress("UNCHECKED_CAST") fun <B> flatMap(f:(()->A)->ManyReadPrec<B>):ManyReadPrec<B> = ManyReadPrec(ManyPrecOp.Bind(operation){f(it as ()->A).operation})
    fun <B> map(f:(()->A)->B):ManyReadPrec<B> = flatMap{value->pure{f(value)}}
    fun or(other:ManyReadPrec<A>):ManyReadPrec<A> = ManyReadPrec(ManyPrecOp.Choice(operation,other.operation))
    fun step():ManyReadPrec<A> = ManyReadPrec(ManyPrecOp.Context(operation){p->manyDerivedThunk{p()+1}})
    fun reset():ManyReadPrec<A> = ManyReadPrec(ManyPrecOp.Context(operation){{0}})
    fun atPrecedence(limit:Long):ManyReadPrec<A> = ManyReadPrec(ManyPrecOp.Context(operation){if(it()<=limit)({limit}) else null})
    companion object {
        fun <A> pure(value:()->A):ManyReadPrec<A> = ManyReadPrec(ManyPrecOp.Pure(manyDerivedThunk(value)))
        fun <A> fail():ManyReadPrec<A> = ManyReadPrec(ManyPrecOp.Fail)
        fun get():ManyReadPrec<Int> = ManyReadPrec(ManyPrecOp.Get)
        fun look():ManyReadPrec<ManyReadInput> = ManyReadPrec(ManyPrecOp.Look)
        fun <A> defer(value:()->ManyReadPrec<A>):ManyReadPrec<A> = ManyReadPrec(ManyPrecOp.Delay(GenericMemo{value().operation}))
        @Suppress("UNCHECKED_CAST") fun <A> fromReadS(reader:(Long,ManyReadInput)->GenericList<ManyReadAlternative<A>>):ManyReadPrec<A> = fromReadSLazy{p,s->reader(p(),s)}
        @Suppress("UNCHECKED_CAST") fun <A> fromReadSLazy(reader:(()->Long,ManyReadInput)->GenericList<ManyReadAlternative<A>>):ManyReadPrec<A> = ManyReadPrec(ManyPrecOp.ReadS{p,s->reader(p,s) as GenericList<ManyReadAlternative<Any?>>})
        internal fun consume(count:Long):ManyReadPrec<Unit> = if(count==0L)pure{Unit} else get().flatMap{defer{consume(count-1)}}
        internal fun <A> skipSpaces(next:()->ManyReadPrec<A>):ManyReadPrec<A> = look().flatMap {source->
            val s=source();var count=0L;while(s.point(count)?.let(ParserCharacters::isSpace)==true)count++
            consume(count).flatMap{defer(next)}
        }
        fun <A> parens(value:ManyReadPrec<A>):ManyReadPrec<A> {
            lateinit var mandatory:()->ManyReadPrec<A>
            fun optional():ManyReadPrec<A> = skipSpaces {value.or(defer{mandatory()})}
            fun close(x:()->A):ManyReadPrec<A> = skipSpaces {get().flatMap{if(it()==41)pure(x) else fail()}}
            mandatory = {get().flatMap{if(it()!=40)fail() else defer{optional()}.reset().flatMap(::close)}}
            return defer{optional()}
        }
        fun token(expected:String):ManyReadPrec<Unit> = look().flatMap {input->
            val count=ManyReadLex.match(input(),expected)
            if(count==null)fail() else consume(count)
        }
        fun <A> list(value:ManyReadPrec<A>):ManyReadPrec<GenericList<A>> {
            lateinit var rest:(Boolean)->ManyReadPrec<GenericList<A>>
            fun next():ManyReadPrec<GenericList<A>> = value.reset().flatMap {head->defer{rest(true)}.map{tail->GenericList.cons(head){tail()}}}
            rest = {started->token("]").map{GenericList.empty<A>()}.let{end->if(started)end.or(token(",").flatMap{defer{next()}})else end}}
            return parens(token("[").flatMap{defer{rest(false)}.or(defer{next()})})
        }
    }
}
interface ManyElementRead<A> {
    /** Same MINIMAL choice as GHC: override readsPrec or readPrec. */
    fun readsPrec(precedence:()->Long,input:ManyReadInput):GenericList<ManyReadAlternative<A>> = readPrec().resultsLazy(precedence,input)
    fun readPrec():ManyReadPrec<A> = ManyReadPrec.fromReadSLazy{p,s->readsPrec(p,s)}
    fun readList(input:ManyReadInput):GenericList<ManyReadAlternative<GenericList<A>>> = ManyReadPrec.list(ManyReadPrec.defer{readPrec()}).results(0,input)
    fun readListPrec():ManyReadPrec<GenericList<A>> = ManyReadPrec.fromReadSLazy{_,s->readList(s)}
}
class ManyReadDictionary<A>(private val element:ManyElementRead<A>) {
    fun readPrec():ManyReadPrec<Many<A>> = ManyReadPrec.parens(
        ManyReadPrec.token("Many").flatMap{ManyReadPrec.token("{")}.flatMap{ManyReadPrec.token("unMany")}.flatMap{ManyReadPrec.token("=")}.flatMap{
            ManyReadPrec.parens(ManyReadPrec.token("fromList").flatMap{ManyReadPrec.defer{element.readListPrec()}}.atPrecedence(10)).reset()
        }.flatMap{items->ManyReadPrec.token("}").map{Many.fromList(items())}}.atPrecedence(11)
    )
    fun readsPrec(precedence:Long,input:ManyReadInput)=readPrec().results(precedence,input)
    fun readsPrecLazy(precedence:()->Long,input:()->ManyReadInput):GenericList<ManyReadAlternative<Many<A>>> {
        val shared=manyDerivedThunk(input)
        return readPrec().resultsLazy(precedence,ManyReadInput{shared().sourceCharacters()})
    }
    fun readPrec(precedence:Long,input:ManyReadInput)=readsPrec(precedence,input)
    fun readPrecLazy(precedence:()->Long,input:()->ManyReadInput)=readsPrecLazy(precedence,input)
    fun readListPrec():ManyReadPrec<GenericList<Many<A>>> = ManyReadPrec.list(ManyReadPrec.defer{readPrec()})
    fun readListPrec(precedence:Long,input:ManyReadInput)=readListPrec().results(precedence,input)
    fun readList(input:ManyReadInput)=readListPrec(0,input)
    fun readListPrecLazy(precedence:()->Long,input:()->ManyReadInput):GenericList<ManyReadAlternative<GenericList<Many<A>>>> {
        val shared=manyDerivedThunk(input)
        return readListPrec().resultsLazy(precedence,ManyReadInput{shared().sourceCharacters()})
    }
    fun readListLazy(input:()->ManyReadInput)=readListPrecLazy({0},input)
    fun readLazy(input:()->ManyReadInput):ManyDerivedThunk<Many<A>> = manyDerivedThunk{read(input())}
    fun read(input:ManyReadInput):Many<A> {
        val results=readsPrec(0,input);var rest=results;var found:(()->Many<A>)?=null
        while(true)when(val node=rest.step()) {
            GenericList.Step.Nil->return found?.invoke()?:throw IllegalArgumentException("Prelude.read: no parse")
            is GenericList.Step.Cons->{val candidate=node.head();if(candidate.remainder().isSpaces()){
                if(found!=null)throw IllegalArgumentException("Prelude.read: ambiguous parse")
                found=candidate::value
            };rest=node.tail()}
        }
    }
}
