// containers0.7 cycleNTimes/rigidify/thin/applicativeTree translation.
// ©Ross Paterson, Louis Wasserman, Bertram Felgenhauer, David Feuer and Milan Straka.
// Original BSD source and license are retained with the oracle.
// Copyright (c) Ross Paterson 2005
// Copyright (c) Louis Wasserman 2009
// Copyright (c) Bertram Felgenhauer, David Feuer, Ross Paterson, and Milan Straka 2014
package io.heapy.kotdoc.ast

internal object ManyRepetition {
 private fun <A> node(cells:List<ManyCell<A>>) = ManyCell.Node(cells)
 fun <A> replicate(n:Long,cell:ManyCell<A>):ManyTree<A>{require(n>=0){"replicate takes a nonnegative integer argument"};return repeated(n,cell)}
 private fun <A> repeated(n:Long,cell:ManyCell<A>):ManyTree<A> {
  if(n==0L)return ManyTree.Empty()
  if(n==1L)return ManyTree.Single(cell)
  val prefix:Int;val suffix:Int
  when(n){2L->{prefix=1;suffix=1};3L->{prefix=2;suffix=1};4L->{prefix=2;suffix=2};5L->{prefix=3;suffix=2};else->{prefix=3;suffix=3}}
  if(n<=6L)return ManyTree.Deep(List(prefix){cell},{ManyTree.Empty()},List(suffix){cell},n*cell.size)
  val remainder=(n%3).toInt();val left=if(remainder==1)2 else 3;val right=if(remainder==0)3 else 2
  val count=n/3-if(remainder==0)2 else 1
  val group=node(List(3){cell})
  return ManyTree.Deep(List(left){cell},{repeated(count,group)},List(right){cell},n*cell.size)
 }
 private fun <A> thin(tree:ManyTree<A>):ManyTree<A> = when(tree){
  is ManyTree.Empty->tree
  is ManyTree.Single->tree
  is ManyTree.Deep->{
   val pr=tree.prefix;val sf=tree.suffix
   var middle:()->ManyTree<A> = {tree.middle}
   val prefix=if(pr.size<=2)pr else{val previous=middle;middle={ManyTrees.cons(node(pr.takeLast(2)),previous())};pr.dropLast(2)}
   val suffix=if(sf.size<=2)sf else{val previous=middle;middle={ManyTrees.snoc(previous(),node(sf.take(2)))};sf.drop(2)}
   ManyTree.Deep(prefix,{thin(middle())},suffix,tree.size)
  }
 }
 private data class Rigid<A>(val size:Long,val prefix:ManyCell.Node<A>,val middle:()->ManyTree<A>,val suffix:ManyCell.Node<A>)
 private fun <A> cycleMiddle(n:Long,rigid:Rigid<A>):ManyTree<A> = when(val middle=rigid.middle()){
  is ManyTree.Empty->{val group=node(listOf(rigid.prefix,rigid.suffix));ManyTree.Deep(listOf(rigid.suffix),{repeated(n,group)},listOf(rigid.prefix),rigid.size*(n+1))}
  is ManyTree.Single->{val group=node(listOf(rigid.prefix,middle.cell,rigid.suffix));ManyTree.Deep(listOf(middle.cell,rigid.suffix),{repeated(n,group)},listOf(rigid.prefix,middle.cell),rigid.size*(n+1)+middle.size)}
  is ManyTree.Deep->{val next=Rigid(rigid.size,node(listOf(rigid.prefix)+middle.prefix),{middle.middle},node(middle.suffix+rigid.suffix));ManyTree.Deep(middle.prefix,{cycleMiddle(n,next)},middle.suffix,middle.size+rigid.size*(n+1))}
 }
 fun <A> cycle(n:Long,tree:ManyTree<A>):ManyTree<A> {
  if(n<=0)return ManyTree.Empty()
  if(n==1L)return tree
  if(tree is ManyTree.Empty)return tree
  if(tree is ManyTree.Single)return repeated(n,tree.cell)
  tree as ManyTree.Deep
  var prefix=tree.prefix;var suffix=tree.suffix;var middle:()->ManyTree<A> = {tree.middle}
  if(prefix.size==4){val extra=node(prefix.takeLast(2));val previous=middle;middle={ManyTrees.cons(extra,previous())};prefix=prefix.take(2)}
  if(prefix.size==1){val first=ManyTrees.left(middle())
   if(first==null){val cells=prefix+suffix;return when(cells.size){2->pairCycle(n,cells);3->pairCycle(n,cells);4->fullCycle(n,tree.size,node(cells.take(2)),{ManyTree.Empty()},node(cells.takeLast(2)));else->fullCycle(n,tree.size,node(cells.take(3)),{ManyTree.Empty()},node(cells.takeLast(2)))}}
   val children=(first.first as ManyCell.Node).children;middle={first.second}
   if(children.size==2)prefix=prefix+children else{prefix=prefix+children.take(1);val previous=middle;val extra=node(children.takeLast(2));middle={ManyTrees.cons(extra,previous())}}
  }
  if(suffix.size==4){val previous=middle;val extra=node(suffix.take(2));middle={ManyTrees.snoc(previous(),extra)};suffix=suffix.takeLast(2)}
  if(suffix.size==1){val last=ManyTrees.right(middle())
   if(last==null){val cells=prefix+suffix;return if(cells.size==3)pairCycle(n,cells)else fullCycle(n,tree.size,node(cells.take(2)),{ManyTree.Empty()},node(cells.takeLast(2)))}
   val children=(last.second as ManyCell.Node).children;middle={last.first}
   if(children.size==2)suffix=children+suffix else{val previous=middle;val extra=node(children.take(2));middle={ManyTrees.snoc(previous(),extra)};suffix=children.takeLast(1)+suffix}
  }
  val fixed=middle
  return fullCycle(n,tree.size,node(prefix),{thin(fixed())},node(suffix))
 }
 private fun <A> pairCycle(n:Long,cells:List<ManyCell<A>>):ManyTree<A>{val group=node(cells);return ManyTree.Deep(cells,{repeated(n-2,group)},cells,n*group.size)}
 private fun <A> fullCycle(n:Long,size:Long,prefix:ManyCell.Node<A>,middle:()->ManyTree<A>,suffix:ManyCell.Node<A>):ManyTree<A> = ManyTree.Deep(prefix.children,{cycleMiddle(n-2,Rigid(size,prefix,middle,suffix))},suffix.children,n*size)
}
