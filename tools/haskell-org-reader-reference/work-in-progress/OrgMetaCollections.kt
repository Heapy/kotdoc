// Text.Pandoc.Readers.Org.Meta collectLines/collectAsList closure.
// Copyright (C) 2014-2024 Albert Krewinkel; GPL-2.0-or-later.
// Lazy fragments keep previous metadata values behind their constructor until
// collection is demanded. Explicit work frames replace recursive old-map lookups.
package io.heapy.kotdoc.readers.org
import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.parsing.Deferred
import io.heapy.kotdoc.readers.RstBlockInlines
internal class OrgMetaInlineList(private val old:()->MetaValue?,private val current:List<Inline>):AbstractList<Inline>() {
 private sealed interface Work {
  class Node(val value:OrgMetaInlineList):Work
  class Meta(val value:MetaValue?):Work
  class Values(val value:List<Inline>):Work
  class AfterOld(val start:Int,val current:List<Inline>):Work
  class Emit(val value:Inline):Work
 }
 private val flattened=Deferred.later {
  val result=ArrayList<Inline>();val stack=java.util.ArrayDeque<Work>();stack.addLast(Work.Node(this))
  while(stack.isNotEmpty())when(val work=stack.removeLast()){
   is Work.Node->{stack.addLast(Work.AfterOld(result.size,work.value.current));stack.addLast(Work.Meta(work.value.old()))}
   is Work.AfterOld->{if(result.size!=work.start)result.add(Inline.SoftBreak);stack.addLast(Work.Values(work.current))}
   is Work.Values->{val list=work.value;if(list is OrgMetaInlineList)stack.addLast(Work.Node(list))else result.addAll(list)}
   is Work.Emit->result.add(work.value)
   is Work.Meta->when(val value=work.value){
    is MetaValue.MetaInlines->stack.addLast(Work.Values(value.content))
    is MetaValue.MetaList->{val values=value.values;for(i in values.indices.reversed()){stack.addLast(Work.Meta(values[i]));if(i>0)stack.addLast(Work.Emit(Inline.SoftBreak))}}
    is MetaValue.MetaString->result.add(Inline.Str(value.value))
    is MetaValue.MetaBlocks->result.addAll(RstBlockInlines.convert(value.content))
    else->Unit
   }
  }
  result
 }
 override val size get()=flattened.value().size
 override fun get(index:Int)=flattened.value()[index]
}
internal class OrgMetaValueList(private val old:()->MetaValue?,private val current:MetaValue):AbstractList<MetaValue>() {
 private sealed interface Work {
  class Node(val value:OrgMetaValueList):Work
  class Old(val value:()->MetaValue?):Work
  class Values(val value:List<MetaValue>):Work
  class Emit(val value:MetaValue):Work
 }
 private val flattened=Deferred.later {
  val result=ArrayList<MetaValue>();val stack=java.util.ArrayDeque<Work>();stack.addLast(Work.Node(this))
  while(stack.isNotEmpty())when(val work=stack.removeLast()){
   is Work.Node->{stack.addLast(Work.Emit(work.value.current));stack.addLast(Work.Old(work.value.old))}
   is Work.Old->when(val value=work.value()){is MetaValue.MetaList->stack.addLast(Work.Values(value.values));null->Unit;else->result.add(value)}
   is Work.Values->{val values=work.value;if(values is OrgMetaValueList)stack.addLast(Work.Node(values))else result.addAll(values)}
   is Work.Emit->result.add(work.value)
  }
  result
 }
 override val size get()=flattened.value().size
 override fun get(index:Int)=flattened.value()[index]
}
internal fun orgLinesValue(key:String,value:Inlines,meta:MetadataMap<MetaValue>)=MetaValue.MetaInlines(OrgMetaInlineList({meta[key]},value.values))
internal fun orgListValue(key:String,value:MetaValue,meta:MetadataMap<MetaValue>)=MetaValue.MetaList(OrgMetaValueList({meta[key]},value))
