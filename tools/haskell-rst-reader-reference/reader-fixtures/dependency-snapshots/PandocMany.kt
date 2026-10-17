// Port of pandoc-types Builder.Many (©2010–2023 John MacFarlane) and
// containers0.7 Seq finger-tree construction/folding (BSD-style).
// ©Ross Paterson2005, Louis Wasserman2009; ©Bertram Felgenhauer,
// David Feuer, Ross Paterson and Milan Straka2014. Original sources/licenses retained.
package io.heapy.kotdoc.ast

internal class ManyThunk<A>(value:()->A){private val result by lazy{runCatching(value)};fun get():A=result.getOrThrow()}
internal sealed interface ManyCell<A>{
 val size:Long
 class Leaf<A>(value:()->A):ManyCell<A>{val value=ManyThunk(value);override val size=1L}
 class Node<A>(override val size:Long,children:()->List<ManyCell<A>>):ManyCell<A>{
  private val fields=ManyThunk(children);val children get()=fields.get()
  constructor(children:List<ManyCell<A>>):this(children.sumOf{it.size},{children})
 }
}
internal sealed interface ManyTree<A>{
 val size:Long
 class Empty<A>:ManyTree<A>{override val size=0L}
 class Single<A>(cell:()->ManyCell<A>,override val size:Long):ManyTree<A>{
  private val field=ManyThunk(cell);val cell get()=field.get()
  constructor(cell:ManyCell<A>):this({cell},cell.size)
 }
 class Deep<A>(prefix:()->List<ManyCell<A>>,middle:()->ManyTree<A>,suffix:()->List<ManyCell<A>>,override val size:Long):ManyTree<A>{
  private val first=ManyThunk(prefix);val prefix get()=first.get()
  private val last=ManyThunk(suffix);val suffix get()=last.get()
  private val inner=ManyThunk(middle);val middle get()=inner.get()
  constructor(prefix:List<ManyCell<A>>,middle:()->ManyTree<A>,suffix:List<ManyCell<A>>,size:Long):this({prefix},middle,{suffix},size)
  constructor(prefix:List<ManyCell<A>>,middle:ManyTree<A>,suffix:List<ManyCell<A>>):this(prefix,{middle},suffix,prefix.sumOf{it.size}+middle.size+suffix.sumOf{it.size})
 }
}
internal class ManyView<A,B>(first:()->A,second:()->B){private val a=ManyThunk(first);private val b=ManyThunk(second);val first get()=a.get();val second get()=b.get()}
private class ManyWork<A>(val tree:(()->ManyTree<A>)?=null,val cell:ManyCell<A>?=null)
internal object ManyTrees {
 fun <A> fromCells(cells:List<ManyCell<A>>):ManyTree<A>{
  val n=cells.size
  fun node(start:Int)=ManyCell.Node(cells.subList(start,start+3))
  if(n==0)return ManyTree.Empty()
  if(n==1)return ManyTree.Single(cells[0])
  if(n<=6){val prefix=(n+1)/2;return ManyTree.Deep(cells.take(prefix),ManyTree.Empty(),cells.drop(prefix))}
  if(n<=15){
   val prefix=if(n%3==1)2 else 3;val suffix=if(n%3==0)3 else 2
   val nodes=(prefix until n-suffix step 3).map(::node)
   return ManyTree.Deep(cells.take(prefix),fromCells(nodes),cells.takeLast(suffix))
  }
  val prefix=cells.take(3);val midPrefix=listOf(node(3),node(6),node(9))
  var first=node(12);var offset=15;val emitted=mutableListOf<ManyCell<A>>()
  while(n-offset>=10){emitted+=ManyCell.Node(listOf(first,node(offset),node(offset+3)));first=node(offset+6);offset+=9}
  val midSuffix=mutableListOf<ManyCell<A>>(first)
  while(n-offset>3){midSuffix+=node(offset);offset+=3}
  return ManyTree.Deep(prefix,ManyTree.Deep(midPrefix,fromCells(emitted),midSuffix),cells.drop(offset))
 }
 fun <A> cons(cell:ManyCell<A>,tree:ManyTree<A>):ManyTree<A> = when(tree){
  is ManyTree.Empty->ManyTree.Single(cell)
  is ManyTree.Single->ManyTree.Deep({listOf(cell)},{ManyTree.Empty()},{listOf(tree.cell)},cell.size+tree.size)
  is ManyTree.Deep->if(tree.prefix.size==4){val middle=tree.middle;val group=ManyCell.Node(tree.prefix.drop(1));ManyTree.Deep({listOf(cell,tree.prefix[0])},{cons(group,middle)},{tree.suffix},cell.size+tree.size)}else ManyTree.Deep({listOf(cell)+tree.prefix},{tree.middle},{tree.suffix},cell.size+tree.size)
 }
 fun <A> snoc(tree:ManyTree<A>,cell:ManyCell<A>):ManyTree<A> = when(tree){
  is ManyTree.Empty->ManyTree.Single(cell)
  is ManyTree.Single->ManyTree.Deep({listOf(tree.cell)},{ManyTree.Empty()},{listOf(cell)},tree.size+cell.size)
  is ManyTree.Deep->if(tree.suffix.size==4){val middle=tree.middle;val group=ManyCell.Node(tree.suffix.take(3));ManyTree.Deep({tree.prefix},{snoc(middle,group)},{listOf(tree.suffix[3],cell)},tree.size+cell.size)}else ManyTree.Deep({tree.prefix},{tree.middle},{tree.suffix+cell},tree.size+cell.size)
 }
 fun <A> append(left:ManyTree<A>,between:List<ManyCell<A>>,right:ManyTree<A>):ManyTree<A> = when {
  left is ManyTree.Empty->between.asReversed().fold(right){tree,cell->cons(cell,tree)}
  right is ManyTree.Empty->between.fold(left){tree,cell->snoc(tree,cell)}
  left is ManyTree.Single->cons(left.cell,append(ManyTree.Empty(),between,right))
  right is ManyTree.Single->snoc(append(left,between,ManyTree.Empty()),right.cell)
  else->{left as ManyTree.Deep;right as ManyTree.Deep
   val cells=left.suffix+between+right.prefix;val nodes=mutableListOf<ManyCell<A>>();var index=0
    while(index<cells.size){val remaining=cells.size-index;val take=if(remaining==2||remaining==4)2 else 3;nodes+=ManyCell.Node(cells.subList(index,index+take));index+=take}
   val middle=append(left.middle,nodes,right.middle)
   ManyTree.Deep({left.prefix},{middle},{right.suffix},left.size+between.sumOf{it.size}+right.size)
  }
 }
 fun <A> left(tree:ManyTree<A>):ManyView<ManyCell<A>,ManyTree<A>>?=when(tree){
  is ManyTree.Empty->null
  is ManyTree.Single->ManyView({tree.cell},{ManyTree.Empty()})
  is ManyTree.Deep->ManyView({tree.prefix[0]},{if(tree.prefix.size>1)ManyTree.Deep(tree.prefix.drop(1),{tree.middle},tree.suffix,tree.size-tree.prefix[0].size)else{
   val middle=left(tree.middle);if(middle==null)fromCells(tree.suffix)else ManyTree.Deep((middle.first as ManyCell.Node).children,{middle.second},tree.suffix,tree.size-tree.prefix[0].size)
  }})
 }
 fun <A> right(tree:ManyTree<A>):ManyView<ManyTree<A>,ManyCell<A>>?=when(tree){
  is ManyTree.Empty->null
  is ManyTree.Single->ManyView({ManyTree.Empty()},{tree.cell})
  is ManyTree.Deep->ManyView({if(tree.suffix.size>1)ManyTree.Deep(tree.prefix,{tree.middle},tree.suffix.dropLast(1),tree.size-tree.suffix.last().size)else{
   val middle=right(tree.middle);if(middle==null)fromCells(tree.prefix)else ManyTree.Deep(tree.prefix,{middle.first},(middle.second as ManyCell.Node).children,tree.size-tree.suffix.last().size)
  }},{tree.suffix.last()})
 }
 fun <A,B> map(tree:ManyTree<A>,f:(()->A)->B):ManyTree<B>{
  fun cell(input:ManyCell<A>):ManyCell<B> = when(input){is ManyCell.Leaf->ManyCell.Leaf{f(input.value::get)};is ManyCell.Node->ManyCell.Node(input.size){input.children.map(::cell)}}
  return when(tree){is ManyTree.Empty->ManyTree.Empty();is ManyTree.Single->ManyTree.Single({cell(tree.cell)},tree.size);is ManyTree.Deep->ManyTree.Deep({tree.prefix.map(::cell)},{map(tree.middle,f)},{tree.suffix.map(::cell)},tree.size)}
 }
 fun <A,B> foldRight(tree:ManyTree<A>,initial:()->B,f:(()->A,()->B)->B):B=when(tree){
  is ManyTree.Empty->initial()
  is ManyTree.Single->foldCellRight(tree.cell,initial,f)
  is ManyTree.Deep->foldRightCells(tree.prefix,{foldRight(tree.middle,{foldRightCells(tree.suffix,initial,f)},f)},f)
 }
 private fun <A,B> foldCellRight(cell:ManyCell<A>,initial:()->B,f:(()->A,()->B)->B):B=when(cell){is ManyCell.Leaf->f(cell.value::get,initial);is ManyCell.Node->foldRightCells(cell.children,initial,f)}
 private fun <A,B> foldRightCells(cells:List<ManyCell<A>>,initial:()->B,f:(()->A,()->B)->B):B {
  var result=initial
  for(cell in cells.asReversed()){val rest=result;val next=ManyThunk{foldCellRight(cell,rest,f)};result=next::get}
  return result()
 }
 fun <A,B> foldLeft(tree:ManyTree<A>,initial:()->B,f:(()->B,()->A)->B):B=when(tree){
  is ManyTree.Empty->initial()
  is ManyTree.Single->foldCellLeft(tree.cell,initial,f)
  is ManyTree.Deep->foldLeftCells(tree.suffix,{foldLeft(tree.middle,{foldLeftCells(tree.prefix,initial,f)},f)},f)
 }
 private fun <A,B> foldCellLeft(cell:ManyCell<A>,initial:()->B,f:(()->B,()->A)->B):B=when(cell){is ManyCell.Leaf->f(initial,cell.value::get);is ManyCell.Node->foldLeftCells(cell.children,initial,f)}
 private fun <A,B> foldLeftCells(cells:List<ManyCell<A>>,initial:()->B,f:(()->B,()->A)->B):B {
  var result=initial
  for(cell in cells){val previous=result;val next=ManyThunk{foldCellLeft(cell,previous,f)};result=next::get}
  return result()
 }
 fun <A> foldLeft1(tree:ManyTree<A>,f:(()->A,()->A)->A):A=when(tree){
  is ManyTree.Empty->error("foldl1: empty sequence")
  is ManyTree.Single->cellLeft1(tree.cell,f)
  is ManyTree.Deep->foldLeftCells(tree.suffix,{foldLeft(tree.middle,{leftCells1(tree.prefix,f)},f)},f)
 }
 private fun <A> cellLeft1(cell:ManyCell<A>,f:(()->A,()->A)->A):A=when(cell){is ManyCell.Leaf->cell.value.get();is ManyCell.Node->leftCells1(cell.children,f)}
 private fun <A> leftCells1(cells:List<ManyCell<A>>,f:(()->A,()->A)->A):A=foldLeftCells(cells.drop(1),{cellLeft1(cells.first(),f)},f)
 fun <A> foldRight1(tree:ManyTree<A>,f:(()->A,()->A)->A):A=when(tree){
  is ManyTree.Empty->error("foldr1: empty sequence")
  is ManyTree.Single->cellRight1(tree.cell,f)
  is ManyTree.Deep->foldRightCells(tree.prefix,{foldRight(tree.middle,{rightCells1(tree.suffix,f)},f)},f)
 }
 private fun <A> cellRight1(cell:ManyCell<A>,f:(()->A,()->A)->A):A=when(cell){is ManyCell.Leaf->cell.value.get();is ManyCell.Node->rightCells1(cell.children,f)}
 private fun <A> rightCells1(cells:List<ManyCell<A>>,f:(()->A,()->A)->A):A=foldRightCells(cells.dropLast(1),{cellRight1(cells.last(),f)},f)
 fun <A,M> foldMap(tree:ManyTree<A>,f:(()->A)->M,monoid:GenericMonoid<M>):M {
  return foldTree(tree,f,monoid)
 }
 private fun <A,M> foldTree(tree:ManyTree<A>,f:(()->A)->M,monoid:GenericMonoid<M>):M {
  fun cell(input:ManyCell<A>):M=when(input){is ManyCell.Leaf->f(input.value::get);is ManyCell.Node->foldCells(input.children,f,monoid)}
  return when(tree){is ManyTree.Empty->monoid.empty();is ManyTree.Single->cell(tree.cell);is ManyTree.Deep->monoid.append({foldCells(tree.prefix,f,monoid)},{monoid.append({foldTree(tree.middle,f,monoid)},{foldCells(tree.suffix,f,monoid)})})}
 }
 private fun <A,M> foldCells(cells:List<ManyCell<A>>,f:(()->A)->M,monoid:GenericMonoid<M>):M {
  fun at(index:Int):M=when(val cell=cells[index]){is ManyCell.Leaf->f(cell.value::get);is ManyCell.Node->foldCells(cell.children,f,monoid)}
  var result:()->M={at(0)}
  for(index in 1 until cells.size){val previous=result;val combined=ManyThunk{monoid.append(previous){at(index)}};result=combined::get}
  return result()
 }
}

/** Source Many/Seq representation with cached signed64-bit size and lazy elements. */
class Many<A> private constructor(sequence:()->ManyTree<A>) : Iterable<A> {
 constructor(sequence:ManySequence<A>):this({sequence.tree.get()})
 internal val tree=ManyThunk(sequence)
 val unMany:ManySequence<A> get()=ManySequence.fromTree{tree.get()}
 val length:Long get()=tree.get().size
 fun isNull():Boolean=tree.get() is ManyTree.Empty
 fun <B> fmapLazy(f:(()->A)->B):Many<B> = Many{ManyTrees.map(tree.get(),f)}
 fun <B> fmap(f:(()->A)->B):Many<B> = fmapLazy(f)
 /** Strict JVM callback convenience; use fmap for source argument demand. */
 fun <B> fmapValue(f:(A)->B):Many<B> = fmapLazy{f(it())}
 fun <B> replace(value:()->B):Many<B> = Many{ManyRepetition.replicate(length,ManyCell.Leaf(value))}
 fun <M> foldMapLazy(f:(()->A)->M,monoid:GenericMonoid<M>):M=ManyTrees.foldMap(tree.get(),f,monoid)
 fun <M> foldMap(f:(()->A)->M,monoid:GenericMonoid<M>):M=foldMapLazy(f,monoid)
 fun <M> foldMapValue(f:(A)->M,monoid:GenericMonoid<M>):M=foldMapLazy({f(it())},monoid)
 internal fun cells(reverse:Boolean=false):Iterator<ManyCell.Leaf<A>> = object:Iterator<ManyCell.Leaf<A>> {
  private val pending=ArrayDeque<ManyWork<A>>().also{it.addLast(ManyWork(tree={this@Many.tree.get()}))}
  private var next:ManyCell.Leaf<A>?=null
  private fun push(cells:List<ManyCell<A>>){for(cell in if(reverse)cells else cells.asReversed())pending.addLast(ManyWork(cell=cell))}
  private fun prepare():Boolean {
   if(next!=null)return true
   while(pending.isNotEmpty()){
    val work=pending.removeLast();val cell=work.cell
    if(cell!=null)when(cell){is ManyCell.Leaf->{next=cell;return true};is ManyCell.Node->push(cell.children)}
    else when(val tree=work.tree!!()){
     is ManyTree.Empty->Unit
     is ManyTree.Single->pending.addLast(ManyWork(cell=tree.cell))
     is ManyTree.Deep->{push(if(reverse)tree.prefix else tree.suffix);pending.addLast(ManyWork(tree={tree.middle}));push(if(reverse)tree.suffix else tree.prefix)}
    }
   }
   return false
  }
  override fun hasNext()=prepare()
  override fun next():ManyCell.Leaf<A>{if(!prepare())throw NoSuchElementException();return next!!.also{next=null}}
 }
 override fun iterator():Iterator<A> = cells().let{source->object:Iterator<A>{override fun hasNext()=source.hasNext();override fun next()=source.next().value.get()}}
 /** Source list view: head and tail are independent, with no Int length demand. */
 fun toSourceList():GenericList<A> = GenericList.defer {
  val cells=cells()
  fun at():GenericList<A> = GenericList.defer {if(!cells.hasNext())GenericList.empty()else{val cell=cells.next();GenericList.cons(cell.value::get,::at)}}
  at()
 }
 /** Finite JVM List adapter. Use toSourceList().step() for source demand. */
 fun toList():List<A> = object:AbstractList<A>(){
  override val size:Int get(){require(length<=Int.MAX_VALUE){"Many.toList JVM List size exceeds Int"};return length.toInt()}
  override fun get(index:Int):A {if(index<0)throw IndexOutOfBoundsException(index);val iterator=cells();repeat(index){if(!iterator.hasNext())throw IndexOutOfBoundsException(index);iterator.next()};if(!iterator.hasNext())throw IndexOutOfBoundsException(index);return iterator.next().value.get()}
  override fun iterator()=this@Many.iterator()
 }
 companion object {
  internal fun <A> fromTree(tree:()->ManyTree<A>):Many<A> = Many(tree)
  fun <A> empty():Many<A> = Many{ManyTree.Empty()}
  fun <A> singleton(value:()->A):Many<A> = Many{ManyTree.Single(ManyCell.Leaf(value))}
  fun <A> fromList(values:List<A>):Many<A> = Many{ManyTrees.fromCells(values.indices.map{index->ManyCell.Leaf{values[index]}})}
 }
}

/** Source Seq carrier for Many's constructor/selector. It deliberately exposes
 * only the sequence operations needed by this Builder surface, not containers' API. */
class ManySequence<A> private constructor(sequence:()->ManyTree<A>):Iterable<A>{
 internal val tree=ManyThunk(sequence)
 val length:Long get()=tree.get().size
 fun isNull():Boolean=tree.get() is ManyTree.Empty
 fun toList():List<A> = Many(this).toList()
 override fun iterator():Iterator<A> = Many(this).iterator()
 companion object {
  internal fun <A> fromTree(tree:()->ManyTree<A>):ManySequence<A> = ManySequence(tree)
  fun <A> fromList(values:List<A>):ManySequence<A> = Many.fromList(values).unMany
  fun <A> singleton(value:()->A):ManySequence<A> = Many.singleton(value).unMany
  fun <A> empty():ManySequence<A> = Many.empty<A>().unMany
  fun <A> deferred(sequence:()->ManySequence<A>):ManySequence<A> = ManySequence{sequence().tree.get()}
 }
}
