// pandoc-types Builder Inlines/Blocks instances, ©2010–2023 John MacFarlane.
// GHC Semigroup/Monoid defaults ©The University of Glasgow. BSD notices retained.
package io.heapy.kotdoc.ast
import java.math.BigInteger
import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.TextChars

/** Specialized source instances: generic Many intentionally has no Semigroup. */
object ManyBlocks {
 fun empty():Many<Block> = Many.empty()
 fun append(left:()->Many<Block>,right:()->Many<Block>):Many<Block> = Many.fromTree{ManyTrees.append(left().tree.get(),emptyList(),right().tree.get())}
 fun mappend(left:()->Many<Block>,right:()->Many<Block>):Many<Block> = append(left,right)
 fun mconcat(values:List<Many<Block>>):Many<Block> = concatMany(values,::empty,::append)
 fun sconcat(first:()->Many<Block>,rest:List<Many<Block>>):Many<Block> = semigroupConcat(first,rest,::append)
 /** Seq's override accepts zero and negative counts, unlike Inlines' default. */
 fun stimes(count:Long,value:()->Many<Block>):Many<Block> = Many.fromTree{ManyRepetition.cycle(count,value().tree.get())}
 fun stimes(count:BigInteger,value:()->Many<Block>):Many<Block> = stimes(count.toLong(),value)
}
object ManyInlines {
 fun empty():Many<Inline> = Many.empty()
 fun append(left:()->Many<Inline>,right:()->Many<Inline>):Many<Inline> = Many.fromTree{
  val xs=left().tree.get();val viewedLeft=ManyTrees.right(xs)
  if(viewedLeft==null)right().tree.get() else{
   val ys=right().tree.get();val viewedRight=ManyTrees.left(ys)
   if(viewedRight==null)xs else{
    val x=(viewedLeft.second as ManyCell.Leaf).value.get();val y=(viewedRight.first as ManyCell.Leaf).value.get()
    val merged=meld(x,y)
    val middle=if(merged==null)ManyTrees.snoc(ManyTrees.snoc(viewedLeft.first,ManyCell.Leaf{x}),ManyCell.Leaf{y})else ManyTrees.snoc(viewedLeft.first,ManyCell.Leaf{merged})
    ManyTrees.append(middle,emptyList(),viewedRight.second)
   }
  }
 }
 fun mappend(left:()->Many<Inline>,right:()->Many<Inline>):Many<Inline> = append(left,right)
 fun mconcat(values:List<Many<Inline>>):Many<Inline> = concatMany(values,::empty,::append)
 fun sconcat(first:()->Many<Inline>,rest:List<Many<Inline>>):Many<Inline> = semigroupConcat(first,rest,::append)
 fun stimes(count:Long,value:()->Many<Inline>):Many<Inline> = stimes(BigInteger.valueOf(count),value)
 fun stimes(count:BigInteger,value:()->Many<Inline>):Many<Inline> {
  // The installed Builder dictionary worker is strict in its Inlines argument.
  val shared=ManyThunk(value)
  shared.get().tree.get()
  require(count.signum()>0){"stimes: positive multiplier expected"}
  fun accumulated(n:BigInteger,x:()->Many<Inline>,z:()->Many<Inline>):Many<Inline>{
   if(n==BigInteger.ONE)return append(x,z)
   val doubled=ManyThunk{append(x,x)}
   return if(!n.testBit(0))accumulated(n.shiftRight(1),doubled::get,z)else{
    val next=ManyThunk{append(x,z)};accumulated(n.shiftRight(1),doubled::get,next::get)
   }
  }
  fun go(n:BigInteger,x:()->Many<Inline>):Many<Inline>{
   if(n==BigInteger.ONE)return x()
   val doubled=ManyThunk{append(x,x)}
   return if(!n.testBit(0))go(n.shiftRight(1),doubled::get)else accumulated(n.shiftRight(1),doubled::get,x)
  }
  return go(count,shared::get)
 }
 fun fromString(value:String):Many<Inline> = Many.fromList(Builder.text(TextChars.fromString(value)).values)
 fun fromString(value:CharacterString):Many<Inline> = Many.fromList(Builder.text(TextChars.fromPoints(value.points())).values)
 private fun meld(x:Inline,y:Inline):Inline? = when{
  x===Inline.Space&&y===Inline.Space->Inline.Space
  (x===Inline.Space||x===Inline.SoftBreak)&&(y===Inline.Space||y===Inline.SoftBreak)->Inline.SoftBreak
  (x===Inline.Space||x===Inline.SoftBreak)&&y===Inline.LineBreak->Inline.LineBreak
  x===Inline.LineBreak&&(y===Inline.Space||y===Inline.SoftBreak)->Inline.LineBreak
  x is Inline.Str&&y is Inline.Str->Inline.Str(x.text+y.text)
  x is Inline.Emph&&y is Inline.Emph->Inline.Emph(lazyConcat(x.content,y.content))
  x is Inline.Underline&&y is Inline.Underline->Inline.Underline(lazyConcat(x.content,y.content))
  x is Inline.Strong&&y is Inline.Strong->Inline.Strong(lazyConcat(x.content,y.content))
  x is Inline.Subscript&&y is Inline.Subscript->Inline.Subscript(lazyConcat(x.content,y.content))
  x is Inline.Superscript&&y is Inline.Superscript->Inline.Superscript(lazyConcat(x.content,y.content))
  x is Inline.Strikeout&&y is Inline.Strikeout->Inline.Strikeout(lazyConcat(x.content,y.content))
  else->null
 }
 private fun <A> lazyConcat(left:List<A>,right:List<A>):List<A> = object:AbstractList<A>(){
  override val size get()=left.size+right.size
  override fun get(index:Int):A=if(index<left.size)left[index]else right[index-left.size]
  override fun iterator():Iterator<A> = sequence{yieldAll(left);yieldAll(right)}.iterator()
 }
}
private fun <A> concatMany(values:List<Many<A>>,empty:()->Many<A>,append:(()->Many<A>,()->Many<A>)->Many<A>):Many<A> {
 fun at(index:Int):Many<A> = if(index==values.size)empty()else append({values[index]}){at(index+1)}
 return Many.fromTree{at(0).tree.get()}
}
private fun <A> semigroupConcat(first:()->Many<A>,rest:List<Many<A>>,append:(()->Many<A>,()->Many<A>)->Many<A>):Many<A> {
 fun at(index:Int):Many<A> = if(index==rest.size-1)rest[index]else append({rest[index]}){at(index+1)}
 return if(rest.isEmpty())first()else append(first){at(0)}
}
