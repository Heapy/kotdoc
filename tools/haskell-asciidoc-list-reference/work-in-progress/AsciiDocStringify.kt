// Text.Pandoc.Shared.stringify (walk fixInlines followed by Inline query).
// Pandoc authors, GPL-2.0-or-later; Walk field order BSD-3-Clause.
package io.heapy.kotdoc.writers
import io.heapy.kotdoc.ast.*

/** The source's fixInlines removes Note/citation metadata before the query can
 * demand their children. Quote delimiters are emitted around the visible list. */
internal fun asciiDocStringify(blocks:List<Block>):String=buildString {
 val frames=ArrayDeque<Iterator<Any>>();frames.addLast(listOf<Any>(blocks).iterator())
 fun children(values:List<Any>){frames.addLast(values.iterator())}
 while(frames.isNotEmpty()){
  val frame=frames.last();if(!frame.hasNext()){frames.removeLast();continue}
  when(val value=frame.next()){
   is String->append(value)
   is List<*>->frames.addLast(AsciiDocPatternList(value).asSequence().map{checkNotNull(it)}.iterator())
   is Pair<*,*>->children(listOf(checkNotNull(value.first),checkNotNull(value.second)))
   is Block.Plain->children(listOf(value.content));is Block.Para->children(listOf(value.content))
   is Block.LineBlock->children(listOf(value.lines));is Block.BlockQuote->children(listOf(value.blocks))
   is Block.OrderedList->children(listOf(value.items));is Block.BulletList->children(listOf(value.items))
   is Block.DefinitionList->children(listOf(value.items));is Block.Header->children(listOf(value.content))
   is Block.Figure->children(listOf(value.caption,value.blocks))
   is Block.Table->children(listOf(value.caption,value.head,value.bodies,value.foot))
   is Block.Div->children(listOf(value.blocks))
   is Inline.Str->append(value.text);is Inline.Code->append(value.text);is Inline.Math->append(value.text)
   Inline.Space,Inline.SoftBreak,Inline.LineBreak->append(' ')
   is Inline.RawInline->if(value.format.value=="html"&&value.text.startsWith("<br"))append(' ')
   is Inline.Emph->children(listOf(value.content));is Inline.Strong->children(listOf(value.content))
   is Inline.Underline->children(listOf(value.content));is Inline.Strikeout->children(listOf(value.content))
   is Inline.Superscript->children(listOf(value.content));is Inline.Subscript->children(listOf(value.content))
   is Inline.SmallCaps->children(listOf(value.content));is Inline.Span->children(listOf(value.content))
   is Inline.Link->children(listOf(value.content));is Inline.Image->children(listOf(value.content))
   is Inline.Cite->children(listOf(value.content));is Inline.Note->Unit
   is Inline.Quoted->{val single=value.type==QuoteType.SingleQuote;append(if(single)'‘'else'“');children(listOf(value.content,if(single)"’"else"”"))}
   is Caption->children(listOfNotNull(value.short,value.long))
   is TableHead->children(listOf(value.rows));is TableBody->children(listOf(value.head,value.body))
   is TableFoot->children(listOf(value.rows));is Row->children(listOf(value.cells));is Cell->children(listOf(value.blocks))
  }
 }
}
