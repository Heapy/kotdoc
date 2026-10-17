// Independent source observations, Pandoc authors, GPL-2.0-or-later.
package io.heapy.kotdoc.writers
import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.options.WriterOptions
import kotlinx.serialization.json.*
import java.io.File
import java.util.zip.GZIPInputStream
private open class ListPrefix<A>(val heads:List<()->A>,val tail:String?):AbstractList<A>() {
 override val size:Int get()=tail?.let{error(it)}?:heads.size
 override fun get(index:Int)=heads[index]()
 override fun iterator():Iterator<A> =object:Iterator<A>{var at=0;override fun hasNext()=if(at<heads.size)true else tail?.let{error(it)}?:false;override fun next()=get(at++)}
}
private class SourcePrefix<A>(heads:List<()->A>,tail:String?):ListPrefix<A>(heads,tail),AsciiDocListSpine<A> {
 override val size:Int get()=error("ordinary-size")
 override fun get(index:Int):A=error("ordinary-get")
 override fun iterator():Iterator<A> =error("ordinary-iterator")
 override fun asciiDocCell():AsciiDocListCell<A>? =if(heads.isEmpty())tail?.let{error(it)}else AsciiDocListCell(heads.first()){SourcePrefix(heads.drop(1),tail)}
}
fun listBoundaryCases(source:Boolean):List<Pair<String,()->List<Block>>> {
 fun <A> prefix(tail:String?,vararg heads:()->A):List<A> =if(source)SourcePrefix(heads.toList(),tail)else ListPrefix(heads.toList(),tail)
 fun badBlocks()=prefix<Block>("block-tail",{error("block-head")})
 fun badInlines()=prefix<Inline>("unused-tail",{error("inline-head")},{Inline.Str("second")})
 return listOf(
 "block-root" to {badBlocks()},"inline-prefix" to {listOf(Block.Para(badInlines()))},"plain-prefix" to {listOf(Block.Plain(badInlines()))},
 "quote-prefix" to {listOf(Block.BlockQuote(badBlocks()))},"div-prefix" to {listOf(Block.Div(Attr(),badBlocks()))},
 "inline-tail-before-head" to {listOf(Block.Para(prefix("inline-tail",{error("inline-head")})))},
 "inline-second-before-tail" to {listOf(Block.Para(prefix("unused-tail",{Inline.Str("first")},{error("second-head")})))},
 "inline-space-second" to {listOf(Block.Para(prefix("unused-tail",{Inline.Space},{error("second-head")})))},
 "header-query-root" to {prefix("unused-tail",{Block.Header(1,Attr(),listOf(Inline.Str("h")))},{error("block-head")})},
 "header-query-note" to {listOf(Block.Header(1,Attr(),listOf(Inline.Note(prefix("note-tail",{error("note-head")})))))},
 "header-query-citation" to {listOf(Block.Header(1,Attr(),listOf(Inline.Cite(listOf(Citation("x",prefix("",{Inline.Note(prefix("note-tail",{error("note-head")}))}),emptyList(),CitationMode.NormalCitation,0,0)),emptyList()))))},
 "header-query-figure" to {listOf(Block.Header(1,Attr(),emptyList()),Block.Figure(Attr(),Caption(null,prefix("caption-tail",{error("caption-head")})),emptyList()))},
 "header-query-table" to {listOf(Block.Header(1,Attr(),emptyList()),Block.Table(Attr(),Caption(null,emptyList()),emptyList(),TableHead(Attr(),listOf(Row(Attr(),listOf(Cell(Attr(),Alignment.AlignDefault,1,1,prefix("cell-tail",{error("cell-head")})))))),emptyList(),TableFoot(Attr(),emptyList())))},
 "empty" to {emptyList()},"finite" to {listOf(Block.Para(listOf(Inline.Str("first"),Inline.Space,Inline.Str("second"))))}
 ).filter{source||it.first!="inline-tail-before-head"}
}
fun observeListBoundary(name:String,make:()->List<Block>):JsonObject =try {
 val output=AsciiDocWriter.write(WriterOptions(),Pandoc(blocks=make()));buildJsonObject{put("name",name);put("length",output.length)}
}catch(e:IllegalStateException){buildJsonObject{put("name",name);put("thrown",e.message?:"")}}
fun main(args:Array<String>){val expected=args.flatMap{file->GZIPInputStream(File(file).inputStream()).bufferedReader().use{it.readLines()}}.associate{val r=Json.parseToJsonElement(it).jsonObject;r.getValue("name").jsonPrimitive.content to r};var count=0;for(source in listOf(false,true))for((name,make)in listBoundaryCases(source)+adjacentListCases(source)+commaListCases(source)){val actual=observeListBoundary(name,make);check(actual==expected[name]){"$source/$name expected=${expected[name]} actual=$actual"};println("PASS $source/$name");count++};println("PASS $count source/ordinary boundary observations")}
fun adjacentListCases(source:Boolean):List<Pair<String,()->List<Block>>> {
 fun <A> prefix(tail:String,vararg heads:()->A):List<A> =if(source)SourcePrefix(heads.toList(),tail)else ListPrefix(heads.toList(),tail)
 return listOf(
 "figure-caption-prefix" to {listOf(Block.Figure(Attr(),Caption(null,prefix("caption-tail",{error("caption-head")})),emptyList()))},
 "line-prefix" to {listOf(Block.LineBlock(prefix("line-tail",{error("line-head")})))},
 "span-class-prefix" to {listOf(Block.Para(listOf(Inline.Span(Attr("",prefix("class-tail",{error("class-head")})),listOf(Inline.Str("x"))))))},
 "table-column-prefix" to {listOf(Block.Table(Attr(),Caption(null,emptyList()),prefix("column-tail",{error("column-head")}),TableHead(Attr(),emptyList()),emptyList(),TableFoot(Attr(),emptyList())))}
 )
}
fun commaListCases(source:Boolean):List<Pair<String,()->List<Block>>> {
 fun <A> prefix(tail:String,vararg heads:()->A):List<A> =if(source)SourcePrefix(heads.toList(),tail)else ListPrefix(heads.toList(),tail)
 fun link(label:List<Inline>)=listOf(Block.Para(listOf(Inline.Link(Attr(),label,Target("x","")))))
 return listOf(
 "link-label-prefix" to {link(prefix("unused-tail",{error("inline-head")},{Inline.Str("second")}))},
 "link-emph-prefix" to {link(listOf(Inline.Emph(prefix("unused-tail",{error("inline-head")},{Inline.Str("second")}))))},
 "link-note-prefix" to {link(listOf(Inline.Note(prefix("block-tail",{error("block-head")}))))},
 "link-comma-prefix" to {link(prefix("unused-tail",{Inline.Str("a,b")},{error("second-head")}))},
 "link-comma-finite" to {link(listOf(Inline.Emph(listOf(Inline.Str("a,b"))),Inline.Str("c,d")))}
 )
}
fun tableListCases(source:Boolean):List<Pair<String,()->List<Block>>> {
 fun <A> prefix(tail:String,vararg heads:()->A):List<A> =if(source)SourcePrefix(heads.toList(),tail)else ListPrefix(heads.toList(),tail)
 fun cell(bs:List<Block>)=Cell(Attr(),Alignment.AlignDefault,1,1,bs)
 fun row(cs:List<Cell>)=Row(Attr(),cs)
 fun goodCell()=cell(listOf(Block.Plain(listOf(Inline.Str("x")))))
 fun goodRow()=row(listOf(goodCell()))
 fun badCells()=prefix<Cell>("cell-tail",{error("cell-head")},{goodCell()})
 fun badRows()=prefix<Row>("row-tail",{error("row-head")},{goodRow()})
 fun body(rows:List<Row>)=TableBody(Attr(),0,emptyList(),rows)
 fun table(h:List<Row> =emptyList(),b:List<TableBody> =emptyList(),f:List<Row> =emptyList(),cols:List<ColSpec> =listOf(ColSpec(Alignment.AlignDefault,ColWidth.Default)))=Block.Table(Attr(),Caption(null,emptyList()),cols,TableHead(Attr(),h),b,TableFoot(Attr(),f))
 fun badInline()=cell(listOf(Block.Plain(prefix("inline-tail",{error("inline-head")},{Inline.Str("second")}))))
 fun spanCell(n:Long)=badInline().copy(rowSpan=n)
 return listOf(
 "cells/header" to {listOf(table(h=listOf(row(badCells()))))},
 "cells/body-head" to {listOf(table(b=listOf(TableBody(Attr(),0,listOf(row(badCells())),emptyList()))))},
 "cells/body" to {listOf(table(b=listOf(body(listOf(row(badCells()))))))},
 "cells/footer" to {listOf(table(f=listOf(row(badCells()))))},
 "rows/header" to {listOf(table(h=badRows()))},
 "rows/body-head" to {listOf(table(b=listOf(TableBody(Attr(),0,badRows(),emptyList()))))},
 "rows/body" to {listOf(table(b=listOf(body(badRows()))))},
 "rows/footer" to {listOf(table(f=badRows()))},
 "bodies/prefix" to {listOf(table(b=prefix("body-tail",{error("body-head")},{body(emptyList())})))},
 "action/body-before-next-row" to {listOf(table(b=listOf(body(prefix("row-tail",{row(listOf(badInline()))},{error("row-head")})))))},
 "action/header-second-before-first-header" to {listOf(table(h=prefix("row-tail",{row(listOf(badInline()))},{error("row-head")})))},
 "action/footer-second-before-first-footer" to {listOf(table(f=prefix("row-tail",{row(listOf(badInline()))},{error("row-head")})))},
 "action/body-before-columns" to {listOf(table(b=listOf(body(listOf(row(listOf(badInline()))))),cols=prefix("column-tail",{error("column-head")})))},
 "action/empty-body-needs-columns" to {listOf(table(b=listOf(body(listOf(row(emptyList())))),cols=prefix("column-tail",{error("column-head")})))},
 "rowspan/body-consumes-next-row" to {listOf(table(b=listOf(body(prefix("row-tail",{row(listOf(spanCell(2)))},{error("row-head")})))))},
 "rowspan/body-ignored-later-tail" to {listOf(table(b=listOf(body(prefix("row-tail",{row(listOf(spanCell(2)))},{row(emptyList())})))))},
 "rowspan/body-needs-third-tail" to {listOf(table(b=listOf(body(prefix("row-tail",{row(listOf(spanCell(4)))},{row(emptyList())})))))},
 "cells/block-prefix" to {listOf(table(b=listOf(body(listOf(row(listOf(cell(prefix("block-tail",{error("block-head")},{Block.Plain(listOf(Inline.Str("second")))})))))))))},
 "nested/ignored-wrong-block-tail" to {listOf(table(b=listOf(body(listOf(row(listOf(cell(listOf(table(b=listOf(body(listOf(row(listOf(cell(prefix("unused-tail",{Block.BlockQuote(emptyList())},{error("unused-head")})))))))))))))))))})
 }
fun nullTitleListCases(source:Boolean):List<Pair<String,()->List<Block>>> {
 fun <A> prefix(tail:String,vararg heads:()->A):List<A> =if(source)SourcePrefix(heads.toList(),tail)else ListPrefix(heads.toList(),tail)
 fun title(ils:List<Inline>)=listOf(Block.Div(Attr("",listOf("note")),listOf(Block.Div(Attr("",listOf("title")),listOf(Block.Plain(ils))),Block.Para(listOf(Inline.Str("x"))))))
 return listOf(
 "line/inner-prefix" to {listOf(Block.LineBlock(listOf(prefix("inline-tail",{error("inline-head")},{Inline.Str("second")}))))},
 "title/inline-prefix" to {title(prefix("inline-tail",{error("inline-head")},{Inline.Str("second")}))},
 "title/ignored-note" to {title(listOf(Inline.Note(prefix("block-tail",{error("block-head")})),Inline.Str("note")))},
 "title/ignored-citation" to {title(listOf(Inline.Cite(listOf(Citation("x",prefix("inline-tail",{error("inline-head")}),emptyList(),CitationMode.NormalCitation,0,0)),listOf(Inline.Str("note")))))}
 )
}
fun attrListCases(source:Boolean):List<Pair<String,()->List<Block>>> {
 fun <A> prefix(tail:String,vararg heads:()->A):List<A> =if(source)SourcePrefix(heads.toList(),tail)else ListPrefix(heads.toList(),tail)
 fun h(attr:Attr)=Block.Header(1,attr,listOf(Inline.Str("h")))
 return listOf(
 "class/span-prefix" to {listOf(Block.Para(listOf(Inline.Span(Attr("",prefix("class-tail",{"other"},{error("class-head")})),listOf(Inline.Str("x"))))))},
 "class/header-unnumbered-ignored-tail" to {listOf(h(Attr("",prefix("class-tail",{"unnumbered"}))))},
 "class/div-section-ignored-tail" to {listOf(Block.Div(Attr("",prefix("class-tail",{"section"})),listOf(h(Attr()))))},
 "kvs/header-ignored-tail" to {listOf(h(Attr(keyValues=prefix("kv-tail",{error("kv-head")}))))},
 "kvs/div-section-ignored-tail" to {listOf(Block.Div(Attr("",listOf("section"),prefix("kv-tail",{error("kv-head")})),listOf(h(Attr()))))},
 "class/header-other-head-before-tail" to {listOf(h(Attr("",prefix("class-tail",{"other"},{error("class-head")}))))},
 "class/div-merge-missing-section-tail" to {listOf(Block.Div(Attr("",prefix("class-tail",{"column"})),listOf(h(Attr()))))},
 "kvs/header-number-present" to {listOf(h(Attr(keyValues=prefix("kv-tail",{"number" to "9"},{error("unused-kv-head")}))))}
 )
}
fun metadataListCases(source:Boolean):List<Pair<String,()->Pandoc>> {
 fun <A> prefix(tail:String?,vararg heads:()->A):List<A> =if(source)SourcePrefix(heads.toList(),tail)else ListPrefix(heads.toList(),tail)
 fun badInline()=MetaValue.MetaInlines(prefix("inline-tail",{error("inline-head")},{Inline.Str("second")}))
 fun x(value:MetaValue,blocks:List<Block> =emptyList())=Pandoc(mapOf("x" to value),blocks)
 return listOf(
 "meta/root" to {x(MetaValue.MetaList(prefix("meta-tail",{error("meta-head")},{MetaValue.MetaBool(false)})))},
 "meta/leaf-action" to {x(MetaValue.MetaList(prefix("meta-tail",{badInline()},{MetaValue.MetaBool(false)})))},
 "meta/nested-list" to {x(MetaValue.MetaList(prefix("meta-tail",{MetaValue.MetaList(prefix("inner-tail",{error("meta-head")},{MetaValue.MetaBool(false)}))})))},
 "meta/nested-map" to {x(MetaValue.MetaList(prefix("meta-tail",{MetaValue.MetaMap(mapOf("a" to badInline()))})))},
 "meta/map-order" to {Pandoc(linkedMapOf("z" to MetaValue.MetaInlines(prefix(null,{error("map-z")})),"a" to MetaValue.MetaInlines(prefix(null,{error("map-a")}))),emptyList())},
 "meta/before-body" to {x(badInline(),prefix("body-tail",{error("body-head")}))}
 )
}
fun observeMetadataBoundary(name:String,make:()->Pandoc):JsonObject=try {
 val template=(io.heapy.kotdoc.templates.TemplateCompiler.Text.compile("oracle.asciidoc","\$body\$")as io.heapy.kotdoc.templates.CompiledTemplate.Success).template
 val output=AsciiDocWriter.write(WriterOptions(template=template),make());buildJsonObject{put("name",name);put("length",output.length)}
}catch(e:IllegalStateException){buildJsonObject{put("name",name);put("thrown",e.message?:"")}}
fun metadataMapCase():Pair<String,()->Pandoc> ="meta/map-value-order" to {
 val values=object:AbstractMap<String,MetaValue>(){
  override val entries:Set<Map.Entry<String,MetaValue>> =linkedSetOf("z","a").map{key->object:Map.Entry<String,MetaValue>{override val key=key;override val value:MetaValue get()=error("map-$key")}}.toSet()
 }
 Pandoc(mapOf("x" to MetaValue.MetaMap(values)),emptyList())
}
fun infiniteListCases(source:Boolean):List<Pair<String,()->List<Block>>> {
 fun repeated(first:String):List<String> {
  class Infinite(val first:String):AbstractList<String>(),AsciiDocListSpine<String>{
   override val size:Int get()=error("infinite extent")
   override fun get(index:Int)=if(index==0)first else "other"
   override fun iterator():Iterator<String> =object:Iterator<String>{var firstCell=true;override fun hasNext()=true;override fun next():String=if(firstCell){firstCell=false;first}else "other"}
   override fun asciiDocCell()=AsciiDocListCell({first},{Infinite("other")})
  }
  val values=Infinite(first)
  return if(source)values else object:AbstractList<String>(){override val size:Int get()=error("infinite extent");override fun get(index:Int)=values[index];override fun iterator()=values.iterator()}
 }
 fun h(attr:Attr)=Block.Header(1,attr,listOf(Inline.Str("h")))
 return listOf("infinite/header-unnumbered" to {listOf(h(Attr("",repeated("unnumbered"))))},"infinite/div-section" to {listOf(Block.Div(Attr("",repeated("section")),listOf(h(Attr()))))})
}
fun protocolOnlyCases():List<Pair<String,()->List<Block>>> {
 fun <A> cells(vararg values:A):List<A> =SourcePrefix(values.map{{it}},null)
 fun ils(s:String)=cells<Inline>(Inline.Str(s))
 fun caption(bs:List<Block>)=cells<Block>(Block.Figure(Attr(),Caption(null,bs),cells(Block.Plain(cells(Inline.Image(Attr(),ils("a"),Target("x.png","")))))))
 return listOf(
 "protocol/note-wrong-head" to {cells(Block.Para(cells(Inline.Note(SourcePrefix(listOf({Block.BlockQuote(emptyList())}),"unused-tail")))))},
 "protocol/note-plain" to {cells(Block.Para(cells(Inline.Note(cells(Block.Plain(ils("x")))))))},
 "protocol/span-empty" to {cells(Block.Para(cells(Inline.Span(Attr(classes=cells()),ils("x")))))},
 "protocol/span-mark" to {cells(Block.Para(cells(Inline.Span(Attr(classes=cells("mark")),ils("x")))))},
 "protocol/span-roles" to {cells(Block.Para(cells(Inline.Span(Attr(classes=cells("a","b")),ils("x")))))},
 "protocol/code-classes" to {cells(Block.CodeBlock(Attr(classes=cells("numberLines","kotlin")),"x"))},
 "protocol/line-outer" to {cells(Block.LineBlock(cells(ils("x"),ils("y"))))},
 "protocol/quote-recheck" to {cells(Block.BlockQuote(cells(Block.Plain(ils("x")))))},
 "protocol/caption-blocks" to {caption(cells(Block.Plain(ils("x"))))},
 "protocol/caption-list-merge" to {caption(cells(Block.BulletList(cells(cells(Block.Plain(cells(Inline.Emph(ils("x"))))),cells(Block.Plain(cells(Inline.Emph(ils("y")))))))))},
 "protocol/caption-merge" to {caption(cells(Block.Plain(cells(Inline.Emph(ils("x")))),Block.Plain(cells(Inline.Emph(ils("y"))))))}
 )
}

fun protocolHeaderCase():Pair<String,()->List<Block>> ="protocol/header" to {
 val inner:List<Inline> =SourcePrefix(listOf({Inline.Str("x")}),null)
 val outer:List<Inline> =SourcePrefix(listOf({Inline.Emph(inner)}),null)
 SourcePrefix(listOf({Block.Header(1,Attr(),outer)}),null)
}
fun borrowedBoundaryCases():List<Triple<String,Boolean,()->Pandoc>> {
 fun <A> cells(values:List<A>):List<A> =SourcePrefix(values.map{{it}},null)
 fun ils(s:String)=cells(listOf<Inline>(Inline.Str(s)))
 fun plain(values:List<Inline>)=Block.Plain(values)
 val text=listOf("inlines-empty" to MetaValue.MetaInlines(cells(emptyList())),"inlines" to MetaValue.MetaInlines(ils("x")),"plain-empty" to MetaValue.MetaBlocks(cells(listOf(plain(cells(emptyList()))))),"para" to MetaValue.MetaBlocks(cells(listOf(Block.Para(ils("x"))))),"wrong-block" to MetaValue.MetaBlocks(cells(listOf(Block.BlockQuote(cells(emptyList()))))),"two-blocks" to MetaValue.MetaBlocks(cells(listOf(plain(cells(emptyList())),plain(cells(emptyList()))))),"string-empty" to MetaValue.MetaString(""))
 fun listMeta(value:MetaValue)=MetaValue.MetaList(cells(listOf(value)))
 fun blockMeta(block:Block)=MetaValue.MetaBlocks(cells(listOf(block)))
 val authors=listOf(
  "inlines-empty" to MetaValue.MetaInlines(cells(emptyList())),
  "list-empty" to MetaValue.MetaList(cells(emptyList())),
  "ignored" to listMeta(MetaValue.MetaBool(false)),
  "list-inlines" to listMeta(MetaValue.MetaInlines(cells(emptyList()))),
  "list-plain" to listMeta(blockMeta(plain(cells(emptyList())))),
  "list-para" to listMeta(blockMeta(Block.Para(cells(emptyList())))),
  "list-string" to listMeta(MetaValue.MetaString("")))

 val cases=mutableListOf(Triple<String,Boolean,()->Pandoc>("metadata/absent",true,{Pandoc()}))
 for((key,values)in listOf("title" to text,"date" to text.take(2),"author" to authors))for((name,value)in values)cases.add(Triple("metadata/$key/$name",true,{Pandoc(mapOf(key to value),emptyList())}))
 for((name,kvs)in listOf("both" to listOf("width" to "20px","height" to "30px"),"empty" to emptyList(),"junk" to listOf("other" to "ignored","width" to "25%")))cases.add(Triple("dimensions/$name",false,{Pandoc(blocks=cells(listOf(Block.Para(cells(listOf(Inline.Image(Attr(keyValues=cells(kvs)),ils("a"),Target("x.png",""))))))))}))
 return cases
}
fun observeBorrowedBoundary(name:String,standalone:Boolean,make:()->Pandoc):JsonObject=try {
 val template=if(standalone)(io.heapy.kotdoc.templates.TemplateCompiler.Text.compile("oracle.asciidoc","\$if(titleblock)\$yes\$else\$no\$endif\$")as io.heapy.kotdoc.templates.CompiledTemplate.Success).template else null
 val output=AsciiDocWriter.write(WriterOptions(template=template),make());buildJsonObject{put("name",name);put("output",output)}
}catch(e:IllegalStateException){buildJsonObject{put("name",name);put("thrown",e.message?:"")}}
fun identifierBoundaryCases():List<Triple<String,io.heapy.kotdoc.options.ExtensionName,()->Pandoc>> {
 fun <A> cells(values:List<A>,tail:String?=null):List<A> =SourcePrefix(values.map{{it}},tail)
 val variants=listOf("alias","two-cons","wrong-key","unicode-alias","plain-emoji")
 return listOf("gfm" to io.heapy.kotdoc.options.ExtensionName.GfmAutoIdentifiers,"ascii" to io.heapy.kotdoc.options.ExtensionName.AsciiIdentifiers).flatMap{(flag,extension)->variants.map{name->Triple("identifier/$flag/$name",extension,{
  val ident=when(name){"alias"->"alias";"unicode-alias"->"section";"plain-emoji"->"heart";else->"x"}
  val kvs:List<Pair<String,String>> =when(name){
   "two-cons"->SourcePrefix(listOf({"data-emoji" to "alias"},{error("unused-kv-head")}),"unused-kv-tail")
   "wrong-key"->cells(listOf("other" to "alias"),"unused-kv-tail")
   else->cells(listOf("data-emoji" to if(name=="unicode-alias")"❤"else"alias"))
  }
  val inline:Inline=if(name=="plain-emoji")Inline.Str("❤")else Inline.Span(Attr("",cells(listOf("emoji")),kvs),cells(listOf(Inline.Str("x"))))
  Pandoc(blocks=cells(listOf(Block.Header(1,Attr(identifier=ident),cells(listOf(inline))))))
 })}}
}
fun observeIdentifierBoundary(name:String,extension:io.heapy.kotdoc.options.ExtensionName,make:()->Pandoc):JsonObject=try {
 val output=AsciiDocWriter.write(WriterOptions(extensions=io.heapy.kotdoc.options.Extensions.of(extension,io.heapy.kotdoc.options.ExtensionName.AutoIdentifiers)),make());buildJsonObject{put("name",name);put("output",output)}
}catch(e:IllegalStateException){buildJsonObject{put("name",name);put("thrown",e.message?:"")}}
