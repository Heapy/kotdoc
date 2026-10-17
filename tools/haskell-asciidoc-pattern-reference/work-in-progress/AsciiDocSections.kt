// Text.Pandoc.Shared.makeSections False Nothing, Pandoc 3.12.1.
// Copyright (C) 2006-2024 John MacFarlane. GPL-2.0-or-later.
package io.heapy.kotdoc.writers
import io.heapy.kotdoc.ast.*

/** Only the False/Nothing/empty-offset closure used by this writer. */
internal object AsciiDocSections {
 fun make(blocks:List<Block>):List<Block>{
  // The source does not query minLevel until a Header needs newnum.
  val minimum by lazy {
   var min=Long.MAX_VALUE
   object:PandocVisitor(){override fun visitBlock(value:Block){min=minOf(min,if(value is Block.Header)value.level else 99)}}.walkBlocks(blocks)
   min
  }
  fun headerLtEq(level:Long,input:Block):Boolean{var b=input;while(b is Block.Div){b=b.blocks.firstOrNull()?:return false};return b is Block.Header&&b.level<=level}
  val go=DeepRecursiveFunction<List<Block>,List<Block>>{input->
   val out=mutableListOf<Block>();var index=0
   while(index<input.size){when(val b=input[index++]){
    is Block.Header->{
     // With numbering=False the numeric components and stored lastnum never
     // reach output. Preserve only the source's null-newnum/class demands;
     // eagerly materializing [minLevel..level] would diverge at maxBound even
     // though replicate (level+1) wraps negative and the writer output is finite.
     if(minimum<=b.level)b.attr.classes.contains("unnumbered")
     var end=index;while(end<input.size&&!headerLtEq(b.level,input[end]))end++
     val children=callRecursive(input.subList(index,end));index=end
     val divAttr=b.attr.copy(classes=listOf("section")+b.attr.classes)
     val headerAttr=Attr(classes=b.attr.classes,keyValues=b.attr.keyValues.filter{(key,value)->key!="epub:type"&&(key!="role"||value in listOf("tab","presentation","none","treeitem","menuitem","button","heading"))})
     out.add(Block.Div(divAttr,listOf(b.copy(attr=headerAttr))+children))
    }
    is Block.Div->{
     val observed=AsciiDocPatternList(b.blocks)
     val head=asciiDocFirst(observed)as? Block.Header
     val merge=head!=null&&observed.dropFirst().asSequence().all{it !is Block.Header||it.level>head.level}&&("section" in b.attr.classes||b.attr.classes.none{it in listOf("column","columns","fragment","cell")})
     val inner=callRecursive(observed);val section=inner.singleOrNull()as? Block.Div;val innerHeader=section?.blocks?.firstOrNull()as? Block.Header
     if(merge&&section!=null&&innerHeader!=null){val combined=DocumentShared.combineAttr(section.attr,b.attr)
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
