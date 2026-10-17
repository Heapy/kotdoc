// Text.Pandoc.Shared.blockToInlines/blocksToInlines; Copyright (C) 2006-2024 John MacFarlane.
// GPL-2.0-or-later. Heap continuation specialization for RST substitutions.
package io.heapy.kotdoc.readers
import io.heapy.kotdoc.ast.*
internal object RstBlockInlines {
 private sealed interface Work {
  data class Blocks(val values:List<Block>):Work
  data class BlockValue(val value:Block):Work
 }
  fun joinInlines(parts:List<Inlines>,sep:Inlines=Inlines()):Inlines=parts.foldIndexed(Inlines()){i,a,b->a+(if(i==0)Inlines()else sep)+b}
  private val renderer=DeepRecursiveFunction<Work,Inlines>{work->when(work){
   is Work.Blocks->joinInlines(work.values.map{callRecursive(Work.BlockValue(it))},Builder.linebreak)
   is Work.BlockValue->when(val b=work.value){
    is Block.Plain->Inlines(b.content)
    is Block.Para->Inlines(b.content)
    is Block.Header->Inlines(b.content)
    is Block.LineBlock->Inlines(b.lines.flatMapIndexed{i,line->if(i==0)line else listOf(Inline.LineBreak)+line})
    is Block.CodeBlock->Builder.codeWith(b.attr,b.text)
    is Block.RawBlock->Inlines(listOf(Inline.RawInline(b.format,b.text)))
    is Block.BlockQuote->callRecursive(Work.Blocks(b.blocks))
    is Block.Div->callRecursive(Work.Blocks(b.blocks))
    is Block.Figure->callRecursive(Work.Blocks(b.blocks))
    is Block.OrderedList->joinInlines(b.items.map{callRecursive(Work.Blocks(it))})
    is Block.BulletList->joinInlines(b.items.map{callRecursive(Work.Blocks(it))})
    is Block.DefinitionList->joinInlines(b.items.map{(term,defs)->Inlines(term)+Builder.str(":")+Builder.space+joinInlines(defs.map{callRecursive(Work.Blocks(it))})})
    is Block.Table->{val rows=b.head.rows+b.bodies.flatMap{it.head+it.body}+b.foot.rows;joinInlines(rows.map{row->joinInlines(row.cells.map{callRecursive(Work.Blocks(it.blocks))})},Builder.linebreak)}
    Block.HorizontalRule->Inlines()
   }
  }}
  fun convert(bs:List<Block>)=renderer(Work.Blocks(bs)).values
}
