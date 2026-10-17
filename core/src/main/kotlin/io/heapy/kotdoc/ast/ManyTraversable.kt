// Translation of containers0.7 Seq/Digit/Node Traversable, retained BSD notice.
// ©Ross Paterson, Louis Wasserman, Bertram Felgenhauer, David Feuer and Milan Straka.
// Copyright (c) Ross Paterson 2005
// Copyright (c) Louis Wasserman 2009
// Copyright (c) Bertram Felgenhauer, David Feuer, Ross Paterson, and Milan Straka 2014
package io.heapy.kotdoc.ast

/** Source Applicative operations retain both action and result thunks. */
interface ManyApplicative<F> {
 fun <A> pure(value:()->A):WalkEffect<F,A>
 fun <A,B> fmap(value:()->WalkEffect<F,A>,f:(()->A)->B):WalkEffect<F,B>
 fun <A,B> ap(function:()->WalkEffect<F,(()->A)->B>,argument:()->WalkEffect<F,A>):WalkEffect<F,B>
 fun <A,B,C> liftA2(a:()->WalkEffect<F,A>,b:()->WalkEffect<F,B>,f:(()->A,()->B)->C):WalkEffect<F,C> = ap({fmap(a){x->{y:()->B->f(x,y)}}},b)
 fun <A,B,C,D> liftA3(a:()->WalkEffect<F,A>,b:()->WalkEffect<F,B>,c:()->WalkEffect<F,C>,f:(()->A,()->B,()->C)->D):WalkEffect<F,D> = ap({liftA2(a,b){x,y->{z:()->C->f(x,y,z)}}},c)
}
/** Monad constraints retain their actual Applicative superclass dictionary.
 * This describes effects; it is not a Monad instance for Many. */
interface ManyMonadEffects<F>:ManyApplicative<F>,GenericMonad<F>
object ManyTraversable {
 fun <F,A,B> traverse(values:Many<A>,applicative:ManyApplicative<F>,f:(()->A)->WalkEffect<F,B>):WalkEffect<F,Many<B>> {
  val a=applicative
  fun <T> fields(values:List<()->T>):List<T> = object:AbstractList<T>() {private val cells=values.map{ManyThunk(it)};override val size get()=cells.size;override fun get(index:Int)=cells[index].get()}
  @Suppress("UNCHECKED_CAST")
  fun <R> combine(input:List<ManyCell<A>>,cell:(ManyCell<A>)->WalkEffect<F,ManyCell<B>>,build:(List<ManyCell<B>>)->R):WalkEffect<F,R>{
   fun action(index:Int):WalkEffect<F,Any?> = when(val value=input[index]){is ManyCell.Leaf->f(value.value::get) as WalkEffect<F,Any?>;is ManyCell.Node->cell(value) as WalkEffect<F,Any?>}
   fun result(values:List<()->Any?>):R=build(fields(values.mapIndexed{index,value->{when(input[index]){is ManyCell.Leaf->ManyCell.Leaf{value() as B};is ManyCell.Node->value() as ManyCell<B>}}}))
   return when(input.size){
    1->a.fmap({action(0)}){x->result(listOf(x))}
    2->a.liftA2({action(0)},{action(1)}){x,y->result(listOf(x,y))}
    3->a.liftA3({action(0)},{action(1)},{action(2)}){x,y,z->result(listOf(x,y,z))}
    4->a.ap({a.liftA3({action(0)},{action(1)},{action(2)}){x,y,z->{w:()->Any?->result(listOf(x,y,z,w))}}},{action(3)})
    else->error("invalid Seq digit/node")
   }
  }
  fun cell(input:ManyCell<A>):WalkEffect<F,ManyCell<B>> = when(input){
   is ManyCell.Leaf->a.fmap({f(input.value::get)}){value->ManyCell.Leaf(value)}
   is ManyCell.Node->combine(input.children,::cell){children->ManyCell.Node(input.size){children}}
  }
  fun tree(input:ManyTree<A>):WalkEffect<F,ManyTree<B>> = when(input){
   is ManyTree.Empty->a.pure{ManyTree.Empty()}
   is ManyTree.Single->combine(listOf(input.cell),::cell){cells->ManyTree.Single({cells[0]},input.size)}
   is ManyTree.Deep->a.liftA3({combine(input.prefix,::cell){it}},{tree(input.middle)},{combine(input.suffix,::cell){it}}){prefix,middle,suffix->ManyTree.Deep(prefix,middle,suffix,input.size)}
  }
  return a.fmap({tree(values.tree.get())}){result->Many.fromTree(result)}
 }
 fun <F,A> sequenceA(values:Many<WalkEffect<F,A>>,applicative:ManyApplicative<F>):WalkEffect<F,Many<A>> = traverse(values,applicative){it()}
 fun <F,A,B> mapM(values:Many<A>,monad:ManyMonadEffects<F>,f:(()->A)->WalkEffect<F,B>):WalkEffect<F,Many<B>> = traverse(values,monad,f)
 fun <F,A> sequence(values:Many<WalkEffect<F,A>>,monad:ManyMonadEffects<F>):WalkEffect<F,Many<A>> = mapM(values,monad){it()}
}

/** GHC WrappedMonad's Applicative: each apply sequences its left action first. */
class ManyMonadApplicative<F>(private val monad:GenericMonad<F>):ManyMonadEffects<F>{
 override fun <A,B> flatMap(value:()->WalkEffect<F,A>,next:(()->A)->WalkEffect<F,B>):WalkEffect<F,B> = monad.flatMap(value,next)
 override fun <A> pure(value:()->A):WalkEffect<F,A> = monad.pure(value)
 override fun <A,B> fmap(value:()->WalkEffect<F,A>,f:(()->A)->B):WalkEffect<F,B> = monad.flatMap(value){x->monad.pure{f(x)}}
 override fun <A,B> ap(function:()->WalkEffect<F,(()->A)->B>,argument:()->WalkEffect<F,A>):WalkEffect<F,B> = monad.flatMap(function){fn->monad.flatMap(argument){value->monad.pure{fn()(value)}}}
}
