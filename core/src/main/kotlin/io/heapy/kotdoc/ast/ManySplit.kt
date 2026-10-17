// containers Seq splitAt/dropWhile helpers. BSD-style; original source/license retained.
// Copyright (c) Ross Paterson 2005; (c) Louis Wasserman 2009;
// (c) Bertram Felgenhauer, David Feuer, Ross Paterson, and Milan Straka 2014.
// See licenses/Containers-BSD-3-Clause.txt.
package io.heapy.kotdoc.ast

/** Internal weighted finger-tree split, preserving source digit/node structure. */
internal object ManySplit {
 private data class Pivot<A>(val left:ManyTree<A>,val value:ManyCell<A>,val right:ManyTree<A>)
 private fun <A> part(input:List<A>,from:Int,to:Int=input.size):List<A> = object:AbstractList<A>() {
  override val size=to-from
  override fun get(index:Int):A {if(index !in 0 until size)throw IndexOutOfBoundsException(index);return input[from+index]}
 }
 private fun <A> weight(xs:List<ManyCell<A>>):Long=xs.sumOf{it.size}
 private fun <A> digit(xs:List<ManyCell<A>>,cached:Long?=null):ManyTree<A> = when(xs.size){
  0->ManyTree.Empty()
  1->{val cell=ManyThunk{xs[0]};ManyTree.Single(cell::get){cell.get().size}}
  else->{require(xs.size<=4);val split=(xs.size+1)/2;ManyTree.Deep({part(xs,0,split)},{ManyTree.Empty()},{part(xs,split)},cached?:weight(xs))}
 }
 private fun <A> pullLeft(size:Long,middle:()->ManyTree<A>,suffix:List<ManyCell<A>>):ManyTree<A> {
  val view=ManyTrees.left(middle())?:return digit(suffix,size)
  return ManyTree.Deep({(view.first as ManyCell.Node).children},{view.second},{suffix},size)
 }
 private fun <A> pullRight(size:Long,prefix:List<ManyCell<A>>,middle:()->ManyTree<A>):ManyTree<A> {
  val view=ManyTrees.right(middle())?:return digit(prefix,size)
  return ManyTree.Deep({prefix},{view.first},{(view.second as ManyCell.Node).children},size)
 }
 private fun <A> left(size:Long,prefix:List<ManyCell<A>>,middle:()->ManyTree<A>,suffix:List<ManyCell<A>>):ManyTree<A> =
  if(suffix.isEmpty())pullRight(size,prefix,middle)else ManyTree.Deep({prefix},middle,{suffix},size)
 private fun <A> right(size:Long,prefix:List<ManyCell<A>>,middle:()->ManyTree<A>,suffix:List<ManyCell<A>>):ManyTree<A> =
  if(prefix.isEmpty())pullLeft(size,middle,suffix)else ManyTree.Deep({prefix},middle,{suffix},size)
 private fun <A> at(index:Long,digit:List<ManyCell<A>>):Int {
  var remaining=index
  for(i in digit.indices){if(remaining<digit[i].size)return i;remaining-=digit[i].size}
  error("split digit index")
 }
 private fun <A> pivot(index:Long,tree:ManyTree<A>):Pivot<A> = when(tree){
  is ManyTree.Empty->error("splitTreeN of empty tree")
  is ManyTree.Single->Pivot(ManyTree.Empty(),tree.cell,ManyTree.Empty())
  is ManyTree.Deep->{
   val prefix=tree.prefix;val ps=weight(prefix);val middle={tree.middle}
   when{
    index<ps->{val i=at(index,prefix);val before=part(prefix,0,i);val after=part(prefix,i+1);val bs=weight(before);Pivot(digit(before),prefix[i],right(tree.size-bs-prefix[i].size,after,middle,tree.suffix))}
    index<ps+middle().size->{val p=pivot(index-ps,middle());val node=p.value as ManyCell.Node;val children=node.children;val i=at(index-ps-p.left.size,children);val before=part(children,0,i);val after=part(children,i+1);val bs=ps+p.left.size+weight(before);Pivot(left(bs,prefix,{p.left},before),children[i],right(tree.size-bs-children[i].size,after,{p.right},tree.suffix))}
    else->{val suffix=tree.suffix;val i=at(index-ps-middle().size,suffix);val before=part(suffix,0,i);val after=part(suffix,i+1);val bs=ps+middle().size+weight(before);Pivot(left(bs,prefix,middle,before),suffix[i],digit(after))}
   }
  }
 }
 fun <A> split(index:Long,tree:ManyTree<A>):Pair<ManyTree<A>,ManyTree<A>> {
  if((index-1).toULong()>=(tree.size-1).toULong())return if(index<=0)ManyTree.Empty<A>() to tree else tree to ManyTree.Empty()
  return when(tree){
   is ManyTree.Empty->ManyTree.Empty<A>() to ManyTree.Empty()
   is ManyTree.Single->if(index<=0)ManyTree.Empty<A>() to tree else tree to ManyTree.Empty()
   is ManyTree.Deep->{
    val prefix=tree.prefix;val ps=weight(prefix);val middle={tree.middle}
    when{
     index<ps->{val i=index.toInt();digit(part(prefix,0,i)) to ManyTree.Deep({part(prefix,i)},middle,{tree.suffix},tree.size-index)}
     index<ps+middle().size->{val p=pivot(index-ps,middle());val children=(p.value as ManyCell.Node).children;val i=(index-ps-p.left.size).toInt();val before=part(children,0,i);val after=part(children,i);left(index,prefix,{p.left},before) to right(tree.size-index,after,{p.right},tree.suffix)}
     else->{val i=(index-ps-middle().size).toInt();left(index,prefix,middle,part(tree.suffix,0,i)) to digit(part(tree.suffix,i))}
    }
   }
  }
 }
 fun <A> dropLeft(input:Many<A>,predicate:(()->A)->Boolean):Many<A> = Many.fromTree {
  val tree=input.tree.get();val cells=input.cells();var index=0L;var found=false
  while(cells.hasNext()){val cell=cells.next();if(!predicate(cell.value::get)){found=true;break};index++}
  if(found)split(index,tree).second else ManyTree.Empty()
 }
 fun <A> dropRight(input:Many<A>,predicate:(()->A)->Boolean):Many<A> = Many.fromTree {
  val tree=input.tree.get();val cells=input.cells(true);var index=tree.size-1;var found=false
  while(cells.hasNext()){val cell=cells.next();if(!predicate(cell.value::get)){found=true;break};index--}
  if(found)split(index+1,tree).first else ManyTree.Empty()
 }
}
