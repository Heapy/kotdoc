// Text.Pandoc.Shared.makeSections False Nothing, Pandoc 3.12.1.
// Copyright (C) 2006-2024 John MacFarlane. GPL-2.0-or-later.
package io.heapy.kotdoc.writers
import io.heapy.kotdoc.ast.*

/** Only the False/Nothing/empty-offset closure used by this writer. */
internal object AsciiDocSections {
 fun make(blocks:List<Block>):List<Block>{
  // The source does not query minLevel until a Header needs newnum.
  val minimum by lazy { asciiDocMinimumLevel(blocks) }
  fun headerLtEq(level:Long,input:Block):Boolean{var b=input;while(b is Block.Div){b=asciiDocFirst(b.blocks)?:return false};return b is Block.Header&&b.level<=level}
  val go=DeepRecursiveFunction<List<Block>,List<Block>>{input->
   val observedInput=AsciiDocPatternList(input)
   val out=mutableListOf<Block>();var index=0
   while(observedInput.hasAt(index)){when(val b=observedInput[index++]){
    is Block.Header->{
     // With numbering=False the numeric components and stored lastnum never
     // reach output. Preserve only the source's null-newnum/class demands;
     // eagerly materializing [minLevel..level] would diverge at maxBound even
     // though replicate (level+1) wraps negative and the writer output is finite.
     if(minimum<=b.level)asciiDocContains(b.attr.classes,"unnumbered")
     var end=index;while(observedInput.hasAt(end)&&!headerLtEq(b.level,observedInput[end]))end++
     val children=callRecursive((index until end).map{observedInput[it]});index=end
     val divAttr=b.attr.copy(classes=asciiDocPrepend("section",b.attr.classes))
     val headerKvs=asciiDocGenerated(sequence{for((key,value)in AsciiDocPatternList(b.attr.keyValues))if(key!="epub:type"&&(key!="role"||value in listOf("tab","presentation","none","treeitem","menuitem","button","heading")))yield(key to value)})
     val headerAttr=Attr(classes=b.attr.classes,keyValues=headerKvs)
     out.add(Block.Div(divAttr,listOf(b.copy(attr=headerAttr))+children))
    }
    is Block.Div->{
     val observed=AsciiDocPatternList(b.blocks)
     val head=asciiDocFirst(observed)as? Block.Header
     val merge=head!=null&&observed.dropFirst().asSequence().all{it !is Block.Header||it.level>head.level}&&(asciiDocContains(b.attr.classes,"section")||(!asciiDocContains(b.attr.classes,"column")&&!asciiDocContains(b.attr.classes,"columns")&&!asciiDocContains(b.attr.classes,"fragment")&&!asciiDocContains(b.attr.classes,"cell")))
     val inner=callRecursive(observed);val section=asciiDocSingleConstructor<Block,Block.Div>(inner);val innerHeader=section?.blocks?.let{asciiDocFirst(it)}as? Block.Header
     if(merge&&section!=null&&innerHeader!=null){val combined=asciiDocCombineAttr(section.attr,b.attr)
      out.add(if(b.attr.identifier==section.attr.identifier||b.attr.identifier.isEmpty())section.copy(attr=combined)else section.copy(attr=combined.copy(identifier=b.attr.identifier),blocks=listOf(innerHeader.copy(attr=innerHeader.attr.copy(identifier=section.attr.identifier)))+section.blocks.drop(1)))
     }else out.add(b.copy(blocks=inner))
    }
    else->out.add(b)
   }}
   out
  }
  return go(blocks)
 }
}
