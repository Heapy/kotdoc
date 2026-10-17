// Source list-pattern closure from Pandoc Writers.AsciiDoc (GPL-2.0-or-later).
// Copyright (C) 2006-2024 John MacFarlane.
package io.heapy.kotdoc.writers

/** A source [Constructor ...] pattern inspects the first constructor before the
 * tail spine. A mismatching constructor never demands the tail; a second cons
 * rejects the singleton without evaluating its head or the remaining tail. */
internal inline fun <A, reified B:A> asciiDocSingleConstructor(values:List<A>):B? {
 val cells=values.iterator()
 if(!cells.hasNext())return null
 val head=cells.next()
 if(head !is B)return null
 return if(cells.hasNext())null else head
}
/** List equality with a singleton compares the first value before its tail. */
internal fun <A> asciiDocSingletonEquals(values:List<A>,expected:A):Boolean {
 val cells=values.iterator()
 if(!cells.hasNext())return false
 if(cells.next()!=expected)return false
 return !cells.hasNext()
}

/** Replays constructor observations when a failed pattern falls through to the
 * general renderer. Presence of the next cell never evaluates that cell's head.
 * The input iterator owns the ordinary Kotlin List boundary; this is not an
 * arbitrary Haskell-bottom carrier. */
internal class AsciiDocPatternList<A> private constructor(private val source:Iterator<A>):AbstractList<A>() {
 constructor(values:List<A>):this(values.iterator())
 fun dropFirst():AsciiDocPatternList<A> {val cells=iterator();if(cells.hasNext())cells.next();return AsciiDocPatternList(cells)}
 fun mapFirst(transform:(A)->A):AsciiDocPatternList<A> {
  val cells=iterator();var first=true
  return AsciiDocPatternList(object:Iterator<A>{override fun hasNext()=cells.hasNext();override fun next():A {val value=cells.next();return if(first){first=false;transform(value)}else value}})
 }
 private val prefix=mutableListOf<A>()
 private var present:Boolean?=null
 private fun available(index:Int):Boolean {
  if(index<prefix.size)return true
  check(index==prefix.size)
  return present ?: source.hasNext().also{present=it}
 }
 override fun iterator():Iterator<A> =object:Iterator<A>{
  var index=0
  override fun hasNext()=available(index)
  override fun next():A {
   if(!hasNext())throw NoSuchElementException()
   if(index==prefix.size){prefix.add(source.next());present=null}
   return prefix[index++]
  }
 }
 override fun isEmpty()= !available(0)
 override val size:Int get(){val cells=iterator();var count=0;while(cells.hasNext()){cells.next();count++};return count}
 override fun get(index:Int):A {require(index>=0);val cells=iterator();repeat(index){cells.next()};return cells.next()}
}

internal fun <A> asciiDocFirst(values:List<A>):A? {val cells=values.iterator();return if(cells.hasNext())cells.next()else null}

/** mapM-style traversal: do not ask a Collection for its full extent before the
 * first action. The loop preserves constructor/action-before-next-tail order. */
internal inline fun <A,B> asciiDocMap(values:Iterable<A>,action:(A)->B):List<B> {
 val result=mutableListOf<B>();for(value in values)result.add(action(value));return result
}
