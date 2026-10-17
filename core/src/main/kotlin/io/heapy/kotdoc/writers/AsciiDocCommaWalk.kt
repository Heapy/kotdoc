// Writers.AsciiDoc Link: walk (concatMap fixCommas), and its exact Walk closure.
// Pandoc authors, GPL-2.0-or-later; pandoc-types traversal BSD-3-Clause.
package io.heapy.kotdoc.writers
import io.heapy.kotdoc.ast.*

/** Postorder list transformation remains lazy in list tails and child fields.
 * Creating a transformed AST node never measures the extent of a child list. */
internal object AsciiDocCommaWalk {
 private fun <A,B> map(values:List<A>,action:(A)->B):List<B> =asciiDocGenerated(sequence{for(value in AsciiDocPatternList(values))yield(action(value))})
 fun inlines(values:List<Inline>):List<Inline> =asciiDocGenerated(sequence {
  for(value in AsciiDocPatternList(values)){
   val walked=inline(value)
   if(walked is Inline.Str){
    for((index,text)in walked.text.split(',').withIndex()){
     if(index>0)yield(Inline.RawInline(Format("asciidoc"),"&#44;"))
     yield(Inline.Str(text))
    }
   }else yield(walked)
  }
 })
 private fun citation(value:Citation)=value.copy(citationPrefix=inlines(value.citationPrefix),citationSuffix=inlines(value.citationSuffix))
 private fun inline(value:Inline):Inline=when(value){
  is Inline.Emph->value.copy(content=inlines(value.content));is Inline.Strong->value.copy(content=inlines(value.content))
  is Inline.Underline->value.copy(content=inlines(value.content));is Inline.Strikeout->value.copy(content=inlines(value.content))
  is Inline.Superscript->value.copy(content=inlines(value.content));is Inline.Subscript->value.copy(content=inlines(value.content))
  is Inline.SmallCaps->value.copy(content=inlines(value.content));is Inline.Quoted->value.copy(content=inlines(value.content))
  is Inline.Cite->value.copy(citations=map(value.citations,::citation),content=inlines(value.content))
  is Inline.Link->value.copy(content=inlines(value.content));is Inline.Image->value.copy(content=inlines(value.content))
  is Inline.Note->value.copy(blocks=blocks(value.blocks));is Inline.Span->value.copy(content=inlines(value.content))
  is Inline.Str,is Inline.Code,is Inline.Math,is Inline.RawInline,Inline.Space,Inline.SoftBreak,Inline.LineBreak->value
 }
 private fun blocks(values:List<Block>)=map(values,::block)
 private fun caption(value:Caption)=value.copy(short=value.short?.let(::inlines),long=blocks(value.long))
 private fun cell(value:Cell)=value.copy(blocks=blocks(value.blocks))
 private fun row(value:Row)=value.copy(cells=map(value.cells,::cell))
 private fun head(value:TableHead)=value.copy(rows=map(value.rows,::row))
 private fun body(value:TableBody)=value.copy(head=map(value.head,::row),body=map(value.body,::row))
 private fun foot(value:TableFoot)=value.copy(rows=map(value.rows,::row))
 private fun block(value:Block):Block=when(value){
  is Block.Plain->value.copy(content=inlines(value.content));is Block.Para->value.copy(content=inlines(value.content))
  is Block.LineBlock->value.copy(lines=map(value.lines,::inlines))
  is Block.BlockQuote->value.copy(blocks=blocks(value.blocks));is Block.Div->value.copy(blocks=blocks(value.blocks))
  is Block.OrderedList->value.copy(items=map(value.items,::blocks));is Block.BulletList->value.copy(items=map(value.items,::blocks))
  is Block.DefinitionList->value.copy(items=map(value.items){(label,definitions)->inlines(label) to map(definitions,::blocks)})
  is Block.Header->value.copy(content=inlines(value.content))
  is Block.Figure->value.copy(caption=caption(value.caption),blocks=blocks(value.blocks))
  is Block.Table->value.copy(caption=caption(value.caption),head=head(value.head),bodies=map(value.bodies,::body),foot=foot(value.foot))
  is Block.CodeBlock,is Block.RawBlock,Block.HorizontalRule->value
 }
}

// Identity field traversal for the borrowed identifier bridge. No source list
// constructor/head/tail is evaluated while creating its corresponding wrapper.
private class AsciiDocIdentityMapper(val emojiPattern:Boolean) {
 private fun <A,B> map(values:List<A>,action:(A)->B):List<B> =asciiDocShapeMap(values,action)
 fun inlines(values:List<Inline>):List<Inline> =map(values,::inline)
 private fun attr(value:Attr)=value.copy(classes=AsciiDocPatternList(value.classes),keyValues=AsciiDocPatternList(value.keyValues))
 private fun citation(value:Citation)=value.copy(citationPrefix=inlines(value.citationPrefix),citationSuffix=inlines(value.citationSuffix))
 private fun inline(value:Inline):Inline=when(value){
  is Inline.Emph->value.copy(content=inlines(value.content));is Inline.Strong->value.copy(content=inlines(value.content))
  is Inline.Underline->value.copy(content=inlines(value.content));is Inline.Strikeout->value.copy(content=inlines(value.content))
  is Inline.Superscript->value.copy(content=inlines(value.content));is Inline.Subscript->value.copy(content=inlines(value.content))
  is Inline.SmallCaps->value.copy(content=inlines(value.content));is Inline.Quoted->value.copy(content=inlines(value.content))
  is Inline.Cite->value.copy(citations=map(value.citations,::citation),content=inlines(value.content))
  is Inline.Link->value.copy(attr=attr(value.attr),content=inlines(value.content));is Inline.Image->value.copy(attr=attr(value.attr),content=inlines(value.content))
  is Inline.Note->value.copy(blocks=blocks(value.blocks));is Inline.Span->{
   val alias=if(emojiPattern&&value.attr.identifier.isEmpty()&&asciiDocSingletonEquals(value.attr.classes,"emoji")){
    val kvs=AsciiDocPatternList(value.attr.keyValues)
    if(kvs.hasAt(0)){val first=kvs[0];if(first.first=="data-emoji"&&!kvs.hasAt(1))first.second else null}else null
   }else null
   // Code has the same identifier stringify payload as Str, and prevents the
   // borrowed emoji pass from applying a second transform to the produced alias.
   if(alias!=null)Inline.Code(Attr(),alias)else value.copy(attr=if(emojiPattern)Attr()else attr(value.attr),content=inlines(value.content))
  }
  is Inline.Code->value.copy(attr=attr(value.attr))
  is Inline.Str,is Inline.Math,is Inline.RawInline,Inline.Space,Inline.SoftBreak,Inline.LineBreak->value
 }
 private fun blocks(values:List<Block>)=map(values,::block)
 private fun caption(value:Caption)=value.copy(short=value.short?.let(::inlines),long=blocks(value.long))
 private fun cell(value:Cell)=value.copy(attr=attr(value.attr),blocks=blocks(value.blocks))
 private fun row(value:Row)=value.copy(attr=attr(value.attr),cells=map(value.cells,::cell))
 private fun head(value:TableHead)=value.copy(attr=attr(value.attr),rows=map(value.rows,::row))
 private fun body(value:TableBody)=value.copy(attr=attr(value.attr),head=map(value.head,::row),body=map(value.body,::row))
 private fun foot(value:TableFoot)=value.copy(attr=attr(value.attr),rows=map(value.rows,::row))
 private fun block(value:Block):Block=when(value){
  is Block.Plain->value.copy(content=inlines(value.content));is Block.Para->value.copy(content=inlines(value.content))
  is Block.LineBlock->value.copy(lines=map(value.lines,::inlines))
  is Block.BlockQuote->value.copy(blocks=blocks(value.blocks));is Block.Div->value.copy(attr=attr(value.attr),blocks=blocks(value.blocks))
  is Block.OrderedList->value.copy(items=map(value.items,::blocks));is Block.BulletList->value.copy(items=map(value.items,::blocks))
  is Block.DefinitionList->value.copy(items=map(value.items){(label,definitions)->inlines(label) to map(definitions,::blocks)})
  is Block.Header->value.copy(attr=attr(value.attr),content=inlines(value.content))
  is Block.Figure->value.copy(attr=attr(value.attr),caption=caption(value.caption),blocks=blocks(value.blocks))
  is Block.Table->value.copy(columns=AsciiDocPatternList(value.columns),attr=attr(value.attr),caption=caption(value.caption),head=head(value.head),bodies=map(value.bodies,::body),foot=foot(value.foot))
  is Block.CodeBlock->value.copy(attr=attr(value.attr))
  is Block.RawBlock,Block.HorizontalRule->value
 }
}

internal object AsciiDocIdentityLists {
 fun inlines(values:List<Inline>):List<Inline> =AsciiDocIdentityMapper(false).inlines(values)
 fun identifierInlines(values:List<Inline>,unemoji:Boolean):List<Inline> =AsciiDocIdentityMapper(unemoji).inlines(values)
}
