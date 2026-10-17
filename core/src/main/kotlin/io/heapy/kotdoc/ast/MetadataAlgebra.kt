// Definition Meta/Pandoc Semigroup/Monoid and GHC class defaults.
// Copyright (c) 2006-2023 John MacFarlane; GHC defaults ©University of Glasgow.
// BSD-3-Clause; original licenses are retained in licenses/.
package io.heapy.kotdoc.ast
import java.math.BigInteger

class MetadataAlgebra<A> internal constructor(private val emptyValue:()->A,private val combine:(()->A,()->A)->A,private val leftFirst:Boolean) {
 fun empty():A=emptyValue()
 fun append(left:()->A,right:()->A):A=combine(left,right)
 fun mappend(left:()->A,right:()->A):A=append(left,right)
 fun mconcat(input:()->GenericList<A>):A {
  val values=mutableListOf<()->A>();var current=input
  while(true)when(val node=current().step()){
   GenericList.Step.Nil->break
   is GenericList.Step.Cons->{values+=if(leftFirst){val value=node.head();{value}}else node::head;current=node::tail}
  }
  var result=empty()
  for(value in values.asReversed()){val right=result;result=append(value){right}}
  return result
 }
 fun sconcat(first:()->A,rest:()->GenericList<A>):A {
  val values=mutableListOf<A>();var value=first();var tail=rest
  while(true)when(val node=tail().step()){
   GenericList.Step.Nil->break
   is GenericList.Step.Cons->{values+=value;value=node.head();tail=node::tail}
  }
  var result=value
  for(left in values.asReversed()){val right=result;result=append({left}){right}}
  return result
 }
 fun stimes(count:BigInteger,input:()->A):A=stimes({count},input)
 fun stimes(multiplier:()->BigInteger,input:()->A):A {
  var power=input()
  val count=multiplier()
  require(count.signum()>0){"stimes: positive multiplier expected"}
  var remaining=count
  var accumulated:(()->A)?=null
  while(remaining!=BigInteger.ONE){
   if(remaining.testBit(0)){val p=power;val old=accumulated;val sum=if(old==null)p else append({p},old);accumulated={sum}}
   val p=power;power=append({p},{p});remaining=remaining.shiftRight(1)
  }
  val p=power;return accumulated?.let{append({p},it)}?:p
 }
 companion object {
  val meta=MetadataAlgebra(PandocMetadata::nullMeta,PandocMetadata::appendMeta,false)
  val pandoc=MetadataAlgebra({MetadataDocument({MetadataMap.empty()},{GenericList.empty()})},PandocMetadata::appendPandoc,true)
 }
}
