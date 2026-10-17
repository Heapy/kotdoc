// Port of Text.Pandoc.Builder remaining constructors and text/trim normalization.
// Copyright (c) 2010–2023 John MacFarlane. BSD-3-Clause; see licenses/.
package io.heapy.kotdoc.ast
import io.heapy.kotdoc.layout.TextChars

/** Source constructors use callback/GenericNode carriers so lazy tuple fields stay lazy.
 * Existing AST bridges below are finite-value conveniences, not bottom substitutes. */
object PandocBuilders {
 fun fromInlines(value:Inlines):BuilderInlines=BuilderInlines(Many.fromList(value.values).fmap{GenericAstData.named<Inline>("Inline").toCanonical(it()) as GenericNode})
 fun fromBlocks(value:Blocks):BuilderBlocks=BuilderBlocks(Many.fromList(value.values).fmap{GenericAstData.named<Block>("Block").toCanonical(it()) as GenericNode})
 fun toInlines(value:BuilderInlines)=Inlines(value.source.fmap{GenericAstData.named<Inline>("Inline").fromCanonical(it())}.toSourceList())
 fun toBlocks(value:BuilderBlocks)=Blocks(value.source.fmap{GenericAstData.named<Block>("Block").fromCanonical(it())}.toSourceList())
 fun attr(value:()->Attr):GenericValue<Attr> = BuilderStreams.ast("Attr",value)
 fun listAttributes(value:()->ListAttributes):GenericValue<ListAttributes> = BuilderStreams.ast("ListAttributes",value)
 fun nullAttr()=attr{Attr()}
 fun str(value:()->String)=BuilderStreams.inline("Str",BuilderStreams.text(value))
 fun text(value:()->String):BuilderInlines=BuilderInlines(Many.fromTree{
  val input=TextChars.fromString(value());val points=input.codePoints().toArray();val groups=mutableListOf<String>();var start=0
  fun space(point:Int)=point==32||point==13||point==10||point==9
  while(start<points.size){var end=start+1;while(end<points.size&&space(points[start])==space(points[end]))end++;groups+=TextChars.fromPoints(points.copyOfRange(start,end));start=end}
  ManyTrees.fromCells(groups.map{group->ManyCell.Leaf{if(group.all{space(it.code)})BuilderStreams.nodes("Inline",if(group.any{it=='\r'||it=='\n'})"SoftBreak"else"Space",emptyList())else BuilderStreams.nodes("Inline","Str",listOf(BuilderStreams.text{group}))}})
 })
 private fun inline(constructor:String,input:()->BuilderInlines)=BuilderStreams.inline(constructor,BuilderStreams.inlines(input))
 fun emph(input:()->BuilderInlines)=inline("Emph",input)
 fun underline(input:()->BuilderInlines)=inline("Underline",input)
 fun strong(input:()->BuilderInlines)=inline("Strong",input)
 fun strikeout(input:()->BuilderInlines)=inline("Strikeout",input)
 fun superscript(input:()->BuilderInlines)=inline("Superscript",input)
 fun subscript(input:()->BuilderInlines)=inline("Subscript",input)
 fun smallcaps(input:()->BuilderInlines)=inline("SmallCaps",input)
 private fun quoted(kind:String,input:()->BuilderInlines)=BuilderStreams.inline("Quoted",BuilderStreams.enum("QuoteType",kind),BuilderStreams.inlines(input))
 fun singleQuoted(input:()->BuilderInlines)=quoted("SingleQuote",input)
 fun doubleQuoted(input:()->BuilderInlines)=quoted("DoubleQuote",input)
 fun cite(citations:()->List<GenericNode>,input:()->BuilderInlines)=BuilderStreams.inline("Cite",BuilderStreams.list("Citation",citations),BuilderStreams.inlines(input))
 fun codeWith(attr:GenericValue<*>,value:()->String)=BuilderStreams.inline("Code",attr,BuilderStreams.text(value))
 fun code(value:()->String)=codeWith(nullAttr(),value)
 val space get()=BuilderStreams.inline("Space")
 val softbreak get()=BuilderStreams.inline("SoftBreak")
 val linebreak get()=BuilderStreams.inline("LineBreak")
 private fun math(kind:String,value:()->String)=BuilderStreams.inline("Math",BuilderStreams.enum("MathType",kind),BuilderStreams.text(value))
 fun math(value:()->String)=math("InlineMath",value)
 fun displayMath(value:()->String)=math("DisplayMath",value)
 fun rawInline(format:()->String,value:()->String)=BuilderStreams.inline("RawInline",BuilderStreams.named("Format"){GenericDefinitionData.node("Format","Format",listOf(BuilderStreams.text(format)))},BuilderStreams.text(value))
 fun linkWith(attr:GenericValue<*>,url:()->String,title:()->String,label:()->BuilderInlines)=BuilderStreams.inline("Link",attr,BuilderStreams.inlines(label),BuilderStreams.target(url,title))
 fun link(url:()->String,title:()->String,label:()->BuilderInlines)=linkWith(nullAttr(),url,title,label)
 fun imageWith(attr:GenericValue<*>,url:()->String,title:()->String,alt:()->BuilderInlines)=BuilderStreams.inline("Image",attr,BuilderStreams.inlines(alt),BuilderStreams.target(url,title))
 fun image(url:()->String,title:()->String,alt:()->BuilderInlines)=imageWith(nullAttr(),url,title,alt)
 fun note(blocks:()->BuilderBlocks)=BuilderStreams.inline("Note",BuilderStreams.blocks(blocks))
 fun spanWith(attr:GenericValue<*>,value:()->BuilderInlines)=BuilderStreams.inline("Span",attr,BuilderStreams.inlines(value))
 fun trimInlines(input:()->BuilderInlines):BuilderInlines=BuilderInlines(Many.fromTree{
  fun isSpace(value:()->GenericNode):Boolean{val node=BuilderStreams.inline.force(value) as GenericNode;return node.constructor=="Space"||node.constructor=="SoftBreak"}
  ManySplit.dropLeft(ManySplit.dropRight(input().source,::isSpace),::isSpace).tree.get()
 })
 fun para(value:()->BuilderInlines)=BuilderStreams.block("Para",BuilderStreams.inlines(value))
 fun plain(value:()->BuilderInlines):BuilderBlocks=BuilderBlocks(Many.fromTree{
  val source=value();if(source.source.isNull())ManyTree.Empty()else BuilderStreams.block("Plain",BuilderStreams.inlines{source}).source.tree.get()
 })
 fun lineBlock(lines:()->List<BuilderInlines>)=BuilderStreams.block("LineBlock",BuilderStreams.inlineList(lines))
 fun codeBlockWith(attr:GenericValue<*>,value:()->String)=BuilderStreams.block("CodeBlock",attr,BuilderStreams.text(value))
 fun codeBlock(value:()->String)=codeBlockWith(nullAttr(),value)
 fun rawBlock(format:()->String,value:()->String)=BuilderStreams.block("RawBlock",BuilderStreams.named("Format"){GenericDefinitionData.node("Format","Format",listOf(BuilderStreams.text(format)))},BuilderStreams.text(value))
 fun blockQuote(value:()->BuilderBlocks)=BuilderStreams.block("BlockQuote",BuilderStreams.blocks(value))
 fun orderedListWith(attributes:GenericValue<*>,items:()->List<BuilderBlocks>)=BuilderStreams.block("OrderedList",attributes,BuilderStreams.blockList(items))
 fun orderedList(items:()->List<BuilderBlocks>)=orderedListWith(listAttributes{ListAttributes()},items)
 fun bulletList(items:()->List<BuilderBlocks>)=BuilderStreams.block("BulletList",BuilderStreams.blockList(items))
 fun definitionList(items:()->List<GenericTuple2<BuilderInlines,List<BuilderBlocks>>>):BuilderBlocks {
  val pair=GenericDatas.tuple2(GenericDatas.list(BuilderStreams.inline),GenericDatas.list(GenericDatas.list(BuilderStreams.block)))
  val values=GenericValue(GenericDatas.list(pair)){BuilderStreams.map(items){input->val item=GenericMemo(input);GenericTuple2({item.get().first.source.toSourceList()},{BuilderStreams.map({item.get().second}){it().source.toSourceList()}})}}
  return BuilderStreams.block("DefinitionList",values)
 }
 fun headerWith(attr:GenericValue<*>,level:()->Long,value:()->BuilderInlines)=BuilderStreams.block("Header",BuilderStreams.integer(level),attr,BuilderStreams.inlines(value))
 fun header(level:()->Long,value:()->BuilderInlines)=headerWith(nullAttr(),level,value)
 val horizontalRule get()=BuilderStreams.block("HorizontalRule")
 fun caption(short:()->GenericMaybe<List<GenericNode>>,content:()->BuilderBlocks):()->GenericNode {
  val result=GenericMemo{BuilderStreams.nodes("Caption","Caption",listOf(GenericValue(GenericDatas.maybe(GenericDatas.list(BuilderStreams.inline)),short),BuilderStreams.blocks(content)))};return result::get
 }
 fun simpleCaption(content:()->BuilderBlocks)=caption({GenericMaybe.None},content)
 val emptyCaption:()->GenericNode get()=simpleCaption{BuilderBlocks(Many.empty())}
 fun figureWith(attr:GenericValue<*>,caption:()->GenericNode,content:()->BuilderBlocks)=BuilderStreams.block("Figure",attr,BuilderStreams.named("Caption",caption),BuilderStreams.blocks(content))
 fun figure(caption:()->GenericNode,content:()->BuilderBlocks)=figureWith(nullAttr(),caption,content)
 fun simpleFigureWith(attr:GenericValue<*>,caption:()->BuilderInlines,url:()->String,title:()->String)=figure(simpleCaption{plain(caption)}){plain{imageWith(attr,url,title){BuilderInlines(Many.empty())}}}
 fun simpleFigure(caption:()->BuilderInlines,url:()->String,title:()->String)=simpleFigureWith(nullAttr(),caption,url,title)
 fun divWith(attr:GenericValue<*>,content:()->BuilderBlocks)=BuilderStreams.block("Div",attr,BuilderStreams.blocks(content))
}
