// Source list-pattern closure from Pandoc Writers.AsciiDoc (GPL-2.0-or-later).
// Copyright (C) 2006-2024 John MacFarlane.
package io.heapy.kotdoc.writers

/** A source [Constructor ...] pattern inspects the first constructor before the
 * tail spine. A mismatching constructor never demands the tail; a second cons
 * rejects the singleton without evaluating its head or the remaining tail. */
internal inline fun <A, reified B:A> asciiDocSingleConstructor(values:List<A>):B? {
 val cells=AsciiDocPatternList(values).iterator()
 if(!cells.hasNext())return null
 val head=cells.next()
 if(head !is B)return null
 return if(cells.hasNext())null else head
}
/** List equality with a singleton compares the first value before its tail. */
internal fun <A> asciiDocSingletonEquals(values:List<A>,expected:A):Boolean {
 val cells=AsciiDocPatternList(values).iterator()
 if(!cells.hasNext())return false
 if(cells.next()!=expected)return false
 return !cells.hasNext()
}

/** Optional source-list bridge for callers representing independent list spine,
 * head and tail demands. Ordinary Kotlin List values use their iterator boundary. */
interface AsciiDocListSpine<out A> { fun asciiDocCell():AsciiDocListCell<A>? }
class AsciiDocListCell<out A>(val head:()->A,val tail:()->List<A>)

/** One shared cursor implementation serves finite ordinary lists and explicit
 * source lists. Cached cells retain sharing; no dispatch depends on a fixture. */
internal class AsciiDocPatternList<A> private constructor(private val state:State<A>,private val offset:Int):AbstractList<A>(),AsciiDocListSpine<A> {
 constructor(values:List<A>):this(if(values is AsciiDocPatternList<A>)values.state else State(values),if(values is AsciiDocPatternList<A>)values.offset else 0)
 fun hasAt(index:Int):Boolean {require(index>=0);return state.hasAt(offset+index)}
 override fun get(index:Int):A {if(!hasAt(index))throw IndexOutOfBoundsException(index);return state.prefix[offset+index].head()}
 override fun isEmpty()= !hasAt(0)
 override val size:Int get(){var count=0;while(hasAt(count))count++;return count}
 override fun iterator():Iterator<A> =object:Iterator<A>{var index=0;override fun hasNext()=hasAt(index);override fun next():A {if(!hasNext())throw NoSuchElementException();return get(index++)}}
 override fun asciiDocCell():AsciiDocListCell<A>? =if(hasAt(0))AsciiDocListCell({get(0)},{dropFirst()})else null
 fun dropFirst():AsciiDocPatternList<A> {check(hasAt(0));return AsciiDocPatternList(state,offset+1)}
 fun mapFirst(transform:(A)->A):AsciiDocPatternList<A> {
  val parent=this
  return AsciiDocPatternList(object:AbstractList<A>(),AsciiDocListSpine<A>{
   override val size:Int get()=parent.size
   override fun get(index:Int)=if(index==0)transform(parent[0])else parent[index]
   override fun asciiDocCell():AsciiDocListCell<A>? =if(parent.hasAt(0))AsciiDocListCell({transform(parent[0])},{parent.dropFirst()})else null
  })
 }
 private class State<A>(values:List<A>) {
  val prefix=mutableListOf<AsciiDocListCell<A>>()
  private var remaining:()->List<A> ={values}
  private var ordinary:Iterator<A>?=null
  private var ordinaryAdvance:(()->Unit)?=null
  private fun ordinaryCell(iterator:Iterator<A>):AsciiDocListCell<A>? {
   ordinaryAdvance?.invoke()
   if(!iterator.hasNext()){ordinaryAdvance=null;return null}
   val next by lazy(LazyThreadSafetyMode.NONE){iterator.next()}
   ordinaryAdvance={next;Unit}
   return AsciiDocListCell({next},{emptyList()})
  }
  private var ended=false
  @Suppress("UNCHECKED_CAST")
  fun hasAt(index:Int):Boolean {
   while(prefix.size<=index&&!ended){
    var iterator=ordinary
    val cell=if(iterator!=null)ordinaryCell(iterator)else {
     val input=remaining()
     if(input is AsciiDocListSpine<*>)(input as AsciiDocListSpine<A>).asciiDocCell()
     else {iterator=input.iterator();ordinary=iterator;ordinaryCell(iterator)}
    }
    if(cell==null)ended=true else {
     val head by lazy(LazyThreadSafetyMode.NONE){cell.head()}
     val tail by lazy(LazyThreadSafetyMode.NONE){cell.tail()}
     prefix.add(AsciiDocListCell({head},{tail}));if(ordinary==null)remaining={tail}
    }
   }
   return index<prefix.size
  }
 }
}

internal fun <A> asciiDocFirst(values:List<A>):A? {val cells=AsciiDocPatternList(values).iterator();return if(cells.hasNext())cells.next()else null}

/** mapM-style traversal: do not ask a Collection for its full extent before the
 * first action. The loop preserves constructor/action-before-next-tail order. */
internal inline fun <A,B> asciiDocMap(values:Iterable<A>,action:(A)->B):List<B> {
 val input=if(values is List<A>)AsciiDocPatternList(values)else values
 val result=mutableListOf<B>();for(value in input)result.add(action(value));return result
}

/** Source-generated list: cache the productive iterator, never preallocate by
 * measuring an input Collection. Map/concat closures choose when heads are needed. */
internal fun <A> asciiDocGenerated(values:Sequence<A>):List<A> =AsciiDocPatternList(object:AbstractList<A>(){
 override fun iterator()=values.iterator()
 override val size:Int get()=error("generated extent must use its cursor")
 override fun get(index:Int):A=error("generated index must use its cursor")
})
internal fun <A,B> asciiDocLazyMap(values:List<A>,action:(A)->B):List<B> =asciiDocGenerated(sequence{for(value in AsciiDocPatternList(values))yield(action(value))})
internal fun <A> asciiDocNull(values:List<A>):Boolean =if(values is AsciiDocListSpine<*>)values.asciiDocCell()==null else !values.iterator().hasNext()
internal fun <A> asciiDocInit(values:List<A>):List<A> =asciiDocGenerated(sequence{val cells=AsciiDocPatternList(values);var index=0;while(cells.hasAt(index)&&cells.hasAt(index+1))yield(cells[index++])})
internal fun <A> asciiDocLast(values:List<A>):A? {var result:A?=null;for(value in AsciiDocPatternList(values))result=value;return result}

/** Pure map preserves independent cons/head/tail demands. Used only when handing
 * caption Inline monoid values to the finite Builder bridge; no input extent or
 * child head is inspected while exposing the mapped source spine. */
internal fun <A,B> asciiDocShapeMap(values:List<A>,action:(A)->B):List<B> {
 val input=AsciiDocPatternList(values)
 return AsciiDocPatternList(object:AbstractList<B>(),AsciiDocListSpine<B>{
  override val size:Int get()=error("shape extent must use cursor")
  override fun get(index:Int):B=error("shape index must use cursor")
  override fun asciiDocCell():AsciiDocListCell<B>? =if(!input.hasAt(0))null else AsciiDocListCell({action(input[0])},{asciiDocShapeMap(input.dropFirst(),action)})
 })
}

/** Pure caption concatenation and explicit length/sort require the whole source
 * spine before mapped heads. Ordinary List.size represents that extent; explicit
 * source lists obtain it through the cursor without invoking ordinary APIs. */
internal fun <A> asciiDocWholeSpine(values:List<A>):List<A> =if(values is AsciiDocListSpine<*>)AsciiDocPatternList(values)else values
