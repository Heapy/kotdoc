// Text.Pandoc.Shared makeSections query getLevel closure.
// Pandoc authors, GPL-2.0-or-later. Traversal from pandoc-types Walk, BSD-3-Clause.
package io.heapy.kotdoc.writers
import io.heapy.kotdoc.ast.*

/** Source preorder Block query. Iterator frames do not measure a public list
 * before visiting its first child; the explicit continuation is stack safe. */
internal fun asciiDocMinimumLevel(blocks:List<Block>):Long {
 val query=DeepRecursiveFunction<Any,Long>{value->
  var minimum=when(value){is Block.Header->value.level;is Block->99L;else->Long.MAX_VALUE}
  val children:Iterable<Any> = when(value){
   is List<*>->AsciiDocPatternList(value).asSequence().map{checkNotNull(it)}.asIterable()
   is Iterable<*>->value.asSequence().map{checkNotNull(it)}.asIterable()
   is Pair<*,*>->listOf(checkNotNull(value.first),checkNotNull(value.second))
   is Block.Plain->listOf(value.content);is Block.Para->listOf(value.content)
   is Block.LineBlock->listOf(value.lines);is Block.BlockQuote->listOf(value.blocks)
   is Block.OrderedList->listOf(value.items);is Block.BulletList->listOf(value.items)
   is Block.DefinitionList->listOf(value.items);is Block.Header->listOf(value.content)
   is Block.Figure->listOf(value.caption,value.blocks)
   is Block.Table->listOf(value.caption,value.head,value.bodies,value.foot)
   is Block.Div->listOf(value.blocks)
   is Inline.Emph->listOf(value.content);is Inline.Strong->listOf(value.content)
   is Inline.Underline->listOf(value.content);is Inline.Strikeout->listOf(value.content)
   is Inline.Superscript->listOf(value.content);is Inline.Subscript->listOf(value.content)
   is Inline.SmallCaps->listOf(value.content);is Inline.Quoted->listOf(value.content)
   is Inline.Cite->listOf(value.citations,value.content);is Inline.Link->listOf(value.content)
   is Inline.Image->listOf(value.content);is Inline.Note->listOf(value.blocks)
   is Inline.Span->listOf(value.content)
   is Citation->listOf(value.citationPrefix,value.citationSuffix)
   is Caption->listOfNotNull(value.short,value.long)
   is TableHead->listOf(value.rows);is TableBody->listOf(value.head,value.body)
   is TableFoot->listOf(value.rows);is Row->listOf(value.cells);is Cell->listOf(value.blocks)
   else->emptyList()
  }
  for(child in children)minimum=minOf(minimum,callRecursive(child))
  minimum
 }
 return query(blocks)
}
