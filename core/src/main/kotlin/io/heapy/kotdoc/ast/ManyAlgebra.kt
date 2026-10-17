// pandoc-types Many derived Foldable/Functor/Traversable and specialized algebra.
// ©2010–2023 John MacFarlane; GHC Foldable defaults ©University of Glasgow.
// containers sources/notices retained alongside the original oracle.
package io.heapy.kotdoc.ast

interface ManyNumeric<A>{fun zero():A;fun one():A;fun plus(left:()->A,right:()->A):A;fun times(left:()->A,right:()->A):A}
interface ManyOrder<A>{fun greaterEqual(left:()->A,right:()->A):Boolean;fun lessEqual(left:()->A,right:()->A):Boolean}
object ManyFoldable {
 fun <A> toList(values:Many<A>):GenericList<A> = values.toSourceList()
 fun <A> `null`(values:Many<A>):Boolean=values.isNull()
 fun <A> length(values:Many<A>):Long=values.length
 fun <M> fold(values:Many<M>,monoid:GenericMonoid<M>):M=values.foldMapLazy({it()},monoid)
 fun <A,B> foldr(values:Many<A>,initial:()->B,f:(()->A,()->B)->B):B=ManyTrees.foldRight(values.tree.get(),initial,f)
 fun <A,B> foldl(values:Many<A>,initial:()->B,f:(()->B,()->A)->B):B=ManyTrees.foldLeft(values.tree.get(),initial,f)
 /** Strict only between successive accumulator results; the first seed remains
  * lazy when the first combining function ignores it (Seq singleton included). */
 fun <A,B> foldlPrime(values:Many<A>,initial:()->B,f:(()->B,()->A)->B):B=prime(values.cells(),initial,f)
 fun <A,B> foldrPrime(values:Many<A>,initial:()->B,f:(()->A,()->B)->B):B=prime(values.cells(true),initial){acc,a->f(a,acc)}
 private fun <A,B> prime(cells:Iterator<ManyCell.Leaf<A>>,initial:()->B,f:(()->B,()->A)->B):B {
  var accumulator=initial
  while(cells.hasNext()){val previous=accumulator;val cell=cells.next();val result=f(previous,cell.value::get);accumulator={result}}
  return accumulator()
 }
 fun <A,M> foldMapPrime(values:Many<A>,f:(()->A)->M,monoid:GenericMonoid<M>):M=foldlPrime(values,monoid::empty){acc,a->monoid.append(acc){f(a)}}
 fun <A> foldl1(values:Many<A>,f:(()->A,()->A)->A):A=ManyTrees.foldLeft1(values.tree.get(),f)
 fun <A> foldr1(values:Many<A>,f:(()->A,()->A)->A):A=ManyTrees.foldRight1(values.tree.get(),f)
 fun <A> elem(values:Many<A>,argument:()->A,equal:(()->A,()->A)->Boolean):Boolean=values.foldMapLazy({value->equal(argument,value)},object:GenericMonoid<Boolean>{override fun empty()=false;override fun append(left:()->Boolean,right:()->Boolean)=left()||right()})
 fun <A> maximum(values:Many<A>,order:ManyOrder<A>):A=extreme(values,"maximum",order::greaterEqual)
 fun <A> minimum(values:Many<A>,order:ManyOrder<A>):A=extreme(values,"minimum",order::lessEqual)
 private fun <A> extreme(values:Many<A>,name:String,keepLeft:(()->A,()->A)->Boolean):A {
  val cells=values.cells();if(!cells.hasNext())error("$name: empty structure")
  var result:()->A=cells.next().value::get
  while(cells.hasNext()){val previous=result;val cell=cells.next();result=if(keepLeft(previous,cell.value::get))previous else cell.value::get}
  return result()
 }
 fun <A> sum(values:Many<A>,numeric:ManyNumeric<A>):A=foldMapPrime(values,{it()},object:GenericMonoid<A>{override fun empty()=numeric.zero();override fun append(left:()->A,right:()->A)=numeric.plus(left,right)})
 fun <A> product(values:Many<A>,numeric:ManyNumeric<A>):A=foldMapPrime(values,{it()},object:GenericMonoid<A>{override fun empty()=numeric.one();override fun append(left:()->A,right:()->A)=numeric.times(left,right)})
}
