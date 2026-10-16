// Port of Text.Pandoc.Generic and syb 0.7.4 Schemes.everywhere/everywhere'/
// everywhereM/everything. Copyright (c) 2006–2023 John MacFarlane.
// syb Data.Generics.Schemes:
// Copyright   :  (c) The University of Glasgow, CWI 2001--2003
// pandoc-types BSD-3-Clause; syb BSD-style. Full original syb notice/license
// are packaged as META-INF/LICENSES/syb-0.7.4-{NOTICE,LICENSE}.txt.
package io.heapy.kotdoc.ast

/** Lazy monoid operands preserve source mappend demand. The default fold is the
 * original per-node foldl; lawful built-ins may provide stack-safe reductions. */
interface GenericMonoid<C> {
    fun empty(): C
    fun append(left: () -> C, right: () -> C): C
    fun fold(node: GenericQuery<C>): C {
        val children=node.children() // gmapQ spine is demanded even for First.
        var result:()->C=node::head
        for(child in children) {val previous=result;val next=GenericMemo {append(previous){fold(child)}};result=next::get}
        return result()
    }
}
class GenericQuery<C> internal constructor(head:()->C,children:()->List<GenericQuery<C>>) {
    private val value=GenericMemo(head);private val descendants=GenericMemo(children)
    fun head():C=value.get()
    fun children():List<GenericQuery<C>> = descendants.get()
    /** Every visited node exposes its gmapQ spine before its query value. */
    fun values():Sequence<C> = sequence {
        val pending=ArrayDeque<GenericQuery<C>>();pending.addLast(this@GenericQuery)
        while(pending.isNotEmpty()) {
            val node=pending.removeLast();val children=node.children()
            for(i in children.indices.reversed())pending.addLast(children[i])
            yield(node.head())
        }
    }
    fun valuesReversed():Sequence<C> = sequence {
        data class Work<C>(val node:GenericQuery<C>,val self:Boolean=false)
        val pending=ArrayDeque<Work<C>>();pending.addLast(Work(this@GenericQuery))
        while(pending.isNotEmpty()) {
            val work=pending.removeLast()
            if(work.self)yield(work.node.head()) else {
                val children=work.node.children();pending.addLast(Work(work.node,true))
                for(child in children)pending.addLast(Work(child))
            }
        }
    }
}

object PandocGeneric {
    /** Fully lazy source Monad dictionary, including caller-defined carriers. */
    @Suppress("UNCHECKED_CAST")
    fun <F,A,B> bottomUpMValue(monad:GenericMonad<F>,target:GenericData<A>,root:GenericData<B>,transform:(()->A)->WalkEffect<F,A>,value:()->B):WalkEffect<F,GenericValue<B>> =
        monad.flatMap({genericMonadic(monad,GenericValue(root,value),target,transform)}){result->monad.pure{result() as GenericValue<B>}}
    fun <F,A,B> bottomUpM(monad:GenericMonad<F>,target:GenericData<A>,root:GenericData<B>,transform:(A)->WalkEffect<F,A>,value:B):WalkEffect<F,B> =
        monad.flatMap({bottomUpMValue(monad,target,root,{transform(it())},{value})}){result->monad.pure{result().value()}}

    fun <A,B> bottomUp(target:GenericData<A>,root:GenericData<B>,transform:(A)->A,value:B):B =
        bottomUpLazy(target,root,{transform(it())},{value})
    fun <A,B> topDown(target:GenericData<A>,root:GenericData<B>,transform:(A)->A,value:B):B =
        topDownLazy(target,root,{transform(it())},{value})
    fun <A,B> bottomUpLazy(target:GenericData<A>,root:GenericData<B>,transform:(()->A)->A,value:()->B):B =
        pure(GenericValue(root,value),target,transform,false).cast(root)
    fun <A,B> topDownLazy(target:GenericData<A>,root:GenericData<B>,transform:(()->A)->A,value:()->B):B =
        pure(GenericValue(root,value),target,transform,true).cast(root)

    @Suppress("UNCHECKED_CAST")
    private fun <A> pure(input:GenericValue<*>,target:GenericData<A>,transform:(()->A)->A,topDown:Boolean):GenericValue<*> {
        val data=input.data as GenericData<Any?>
        fun changed(value:()->Any?):Any? = if(data.type==target.type) {
            data.fromCanonical(target.toCanonical(transform{target.fromCanonical(data.toCanonical(value()))}))
        } else value()
        val result=GenericMemo {
            if(topDown) {
                val before=GenericMemo{changed(input::erased)}
                val layer=data.layer(before::get)
                layer.rebuild(layer.fields.map{pure(it,target,transform,true)})
            } else {
                val rebuilt=GenericMemo {val layer=data.layer(input::erased);layer.rebuild(layer.fields.map{pure(it,target,transform,false)})}
                changed(rebuilt::get)
            }
        }
        return GenericValue(data,result::get)
    }
    fun <A,B,C> queryWith(target:GenericData<A>,root:GenericData<B>,query:(A)->C,value:B,monoid:GenericMonoid<C>):C =
        queryWithLazy(target,root,{query(it())},{value},monoid)
    fun <A,B,C> queryWithLazy(target:GenericData<A>,root:GenericData<B>,query:(()->A)->C,value:()->B,monoid:GenericMonoid<C>):C =
        monoid.fold(summary(GenericValue(root,value),target,query,monoid))
    @Suppress("UNCHECKED_CAST")
    private fun <A,C> summary(input:GenericValue<*>,target:GenericData<A>,query:(()->A)->C,monoid:GenericMonoid<C>):GenericQuery<C> =
        GenericQuery({if(input.data.type==target.type)query{input.cast(target)}else monoid.empty()}) {
            val data=input.data as GenericData<Any?>
            data.layer(input::erased).fields.map{summary(it,target,query,monoid)}
        }

    /** Source effect traversal with a deferred result, for actions whose result
     * itself is not demanded. bottomUpM is the strict Kotlin result convenience. */
    fun <F,A,B> bottomUpMValue(monad:WalkMonad<F>,target:GenericData<A>,root:GenericData<B>,transform:(()->A)->WalkEffect<F,A>,value:()->B):WalkEffect<F,GenericValue<B>> {
        // Identity is a source newtype: its bind does not demand descendant
        // actions merely to expose an independent rebuilt field. The strict
        // Kotlin carrier needs this dictionary adapter, not the strict effect
        // machine used for State/List/Maybe sequencing.
        if(monad===WalkIdentity) {
            @Suppress("UNCHECKED_CAST")
            val result=pure(GenericValue(root,value),target,{thunk->(transform(thunk) as WalkIdentity.Value<A>).value},false) as GenericValue<B>
            return monad.pure(result)
        }
        val initial=GenericMachine(GenericFrame(GenericValue(root,value)),null)
        return monad.tailRecM(initial) {state->
            val frame=state.frame;val layer=frame.layer.get()
            if(frame.index<layer.fields.size) {
                monad.pure(WalkLoop.Continue(GenericMachine(GenericFrame(layer.fields[frame.index]),GenericParent(frame.copy(index=frame.index+1),state.parent))))
            } else {
                val children=frame.results
                val rebuilt=GenericValue(frame.data){layer.rebuild(children)}
                fun finish(result:GenericValue<*>):WalkLoop<GenericMachine,GenericValue<B>> {
                    val parent=state.parent
                    @Suppress("UNCHECKED_CAST")
                    return if(parent==null)WalkLoop.Done(result as GenericValue<B>) else WalkLoop.Continue(GenericMachine(parent.frame.copy(results=parent.frame.results+result),parent.rest))
                }
                if(frame.data.type==target.type)monad.flatMap(transform{rebuilt.cast(target)}){result->
                    monad.pure(finish(GenericValue(frame.data){frame.data.fromCanonical(target.toCanonical(result))}))
                } else monad.pure(finish(rebuilt))
            }
        }
    }
    fun <F,A,B> bottomUpM(monad:WalkMonad<F>,target:GenericData<A>,root:GenericData<B>,transform:(A)->WalkEffect<F,A>,value:B):WalkEffect<F,B> =
        monad.flatMap(bottomUpMValue(monad,target,root,{transform(it())},{value})){monad.pure(it.value())}
}
private data class GenericFrame(val input:GenericValue<*>,val index:Int=0,val results:List<GenericValue<*>> = emptyList()) {
    @Suppress("UNCHECKED_CAST") val data=input.data as GenericData<Any?>
    val layer=GenericMemo{data.layer(input::erased)}
}
private data class GenericParent(val frame:GenericFrame,val rest:GenericParent?)
private data class GenericMachine(val frame:GenericFrame,val parent:GenericParent?)

object GenericMonoids {
    fun <A> sequence():GenericMonoid<Sequence<A>> = object:GenericMonoid<Sequence<A>> {
        override fun empty()=emptySequence<A>()
        override fun append(left:()->Sequence<A>,right:()->Sequence<A>)=sequence{yieldAll(left());yieldAll(right())}
        override fun fold(node:GenericQuery<Sequence<A>>)=node.values().flatMap{it}
    }
    fun <A> first():GenericMonoid<GenericMaybe<A>> = object:GenericMonoid<GenericMaybe<A>> {
        override fun empty():GenericMaybe<A> = GenericMaybe.None
        override fun append(left:()->GenericMaybe<A>,right:()->GenericMaybe<A>):GenericMaybe<A> = when(val value=left()){GenericMaybe.None->right();is GenericMaybe.Some->value}
        override fun fold(node:GenericQuery<GenericMaybe<A>>):GenericMaybe<A> = node.values().firstOrNull{it is GenericMaybe.Some} ?: GenericMaybe.None
    }
    fun <A> last():GenericMonoid<GenericMaybe<A>> = object:GenericMonoid<GenericMaybe<A>> {
        override fun empty():GenericMaybe<A> = GenericMaybe.None
        override fun append(left:()->GenericMaybe<A>,right:()->GenericMaybe<A>):GenericMaybe<A> = when(val value=right()){GenericMaybe.None->left();is GenericMaybe.Some->value}
        override fun fold(node:GenericQuery<GenericMaybe<A>>):GenericMaybe<A> = node.valuesReversed().firstOrNull{it is GenericMaybe.Some} ?: GenericMaybe.None
    }
    val sumLong:GenericMonoid<Long> = object:GenericMonoid<Long> {
        override fun empty()=0L
        override fun append(left:()->Long,right:()->Long)=left()+right()
        override fun fold(node:GenericQuery<Long>)=node.values().fold(0L,Long::plus)
    }
    val unit:GenericMonoid<Unit> = object:GenericMonoid<Unit> {
        override fun empty()=Unit
        override fun append(left:()->Unit,right:()->Unit)=Unit
        override fun fold(node:GenericQuery<Unit>) {if(node.children().isEmpty())node.head()}
    }
}
