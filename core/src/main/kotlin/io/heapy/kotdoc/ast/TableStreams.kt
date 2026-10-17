// Port of Text.Pandoc.Builder table list/tuple processing.
// Copyright (c) 2006-2023 John MacFarlane. BSD-3-Clause; see licenses/.
package io.heapy.kotdoc.ast

typealias TablePlacement = GenericTuple3<GenericList<Long>,GenericList<Cell>,GenericList<Cell>>
internal object TableStreams {
 val emptyCell=Cell(Attr(),Alignment.AlignDefault,1,1,emptyList())
 fun cell(input:()->Cell):Cell=input().also{GenericList.view(it.blocks).step()}
 fun row(input:()->Row):Row=input().also{GenericList.view(it.cells).step()}
 fun head(input:()->TableHead):TableHead=input().also{GenericList.view(it.rows).step()}
 fun body(input:()->TableBody):TableBody=input().also{GenericList.view(it.head).step();GenericList.view(it.body).step()}
 fun foot(input:()->TableFoot):TableFoot=input().also{GenericList.view(it.rows).step()}
 fun caption(input:()->Caption):Caption=input().also{GenericList.view(it.long).step()}
 fun <A> append(left:()->GenericList<A>,right:()->GenericList<A>):GenericList<A> = GenericList.defer{when(val n=left().step()){GenericList.Step.Nil->right();is GenericList.Step.Cons->GenericList.cons(n::head,{append(n::tail,right)})}}
 fun <A> map(input:()->List<A>,f:(()->A)->A):GenericList<A> = GenericList.defer{when(val n=GenericList.view(input()).step()){GenericList.Step.Nil->GenericList.empty();is GenericList.Step.Cons->GenericList.cons({f(n::head)},{map(n::tail,f)})}}
 fun <A,B> mapped(input:()->List<A>,f:(()->A)->B):GenericList<B> = GenericList.defer{when(val n=GenericList.view(input()).step()){GenericList.Step.Nil->GenericList.empty();is GenericList.Step.Cons->GenericList.cons({f(n::head)},{mapped(n::tail,f)})}}
 fun <A> repeat(value:()->A):GenericList<A> = GenericList.cons(value){repeat(value)}
 fun <A> replicate(count:Long,value:()->A):GenericList<A> = if(count<=0)GenericList.empty()else GenericList.cons(value){replicate(count-1,value)}
 fun <A> length(input:List<A>):Long {var current=GenericList.view(input);var result=0L;while(true)when(val n=current.step()){GenericList.Step.Nil->return result;is GenericList.Step.Cons->{result++;current=n.tail()}}}
 private sealed interface Decision {
  class Skip(val height:Long,val next:()->Plan):Decision
  class Place(val height:Long,val width:Long,val cell:()->Cell,val next:()->Plan):Decision
  class Stop(val rest:()->GenericList<Cell>):Decision
 }
 private class Plan(hang:()->GenericList<Long>,stream:()->GenericList<Cell>){
  private val hangs=GenericMemo(hang);private val cells=GenericMemo(stream)
  val decision=GenericMemo<Decision>{
   val old=hangs.get()
   when(val h=old.step()){
    is GenericList.Step.Cons->if(h.head()>1){val next=GenericMemo{Plan(h::tail,cells::get)};return@GenericMemo Decision.Skip(h.head()-1,next::get)}
    GenericList.Step.Nil->Unit
   }
   val original=cells.get()
   val c=original.step()
   if(c is GenericList.Step.Cons){
    val value=cell(c::head);val requested=maxOf(1L,value.colSpan)
    var count=0L;var rest=old
    // Source tests the predicate before acc<n, so the boundary head is demanded.
    while(true)when(val n=rest.step()){
     GenericList.Step.Nil->break
     is GenericList.Step.Cons->{if(n.head()!=1L||count>=requested)break;count++;rest=n.tail()}
    }
    if(count>0){
     val width=minOf(count,requested);val nextHang=rest;val next=GenericMemo{Plan({nextHang},c::tail)}
     val normalized=GenericMemo{cell{value.copy(colSpan=width)}}
     return@GenericMemo Decision.Place(value.rowSpan,width,normalized::get,next::get)
    }
   }
   Decision.Stop(cells::get)
  }
  fun overhang():GenericList<Long> = GenericList.defer{when(val d=decision.get()){
   is Decision.Skip->GenericList.cons({d.height},{d.next().overhang()})
   is Decision.Place->append({replicate(d.width){d.height}},{d.next().overhang()})
   is Decision.Stop->GenericList.empty()
  }}
  fun placed():GenericList<Cell> = GenericList.defer{
   var current=this
   while(true)when(val d=current.decision.get()){
    is Decision.Skip->current=d.next()
    is Decision.Place->return@defer GenericList.cons(d.cell){d.next().placed()}
    is Decision.Stop->return@defer GenericList.empty()
   }
   @Suppress("UNREACHABLE_CODE") GenericList.empty()
  }
  fun remaining():GenericList<Cell>{var current=this;while(true)when(val d=current.decision.get()){
   is Decision.Skip->current=d.next()
   is Decision.Place->current=d.next()
   is Decision.Stop->return d.rest()
  }}
 }
 fun placement(hang:()->List<Long>,stream:()->List<Cell>):()->TablePlacement {
  val plan=Plan({GenericList.view(hang())},{GenericList.view(stream())})
  val result=GenericMemo{plan.decision.get();GenericTuple3(plan::overhang,plan::placed,plan::remaining)}
  return result::get
 }
}
