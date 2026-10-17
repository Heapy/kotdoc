// Independent private metadata collector observations; GPL-2.0-or-later.
package io.heapy.kotdoc.readers.org
import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.ast.Builder as B
import kotlinx.serialization.json.*
import java.util.zip.GZIPInputStream
import java.io.FileInputStream
private fun value(kind:String,n:Int):MetaValue? = when(kind){
 "absent"->null;"bool"->MetaValue.MetaBool(true);"map"->MetaValue.MetaMap(mapOf("inner" to MetaValue.MetaString("hidden")));"string"->MetaValue.MetaString("old value");"empty-string"->MetaValue.MetaString("");"inlines"->MetaValue.MetaInlines(listOf(Inline.Str("old"),Inline.Space,Inline.Str("value")));"empty-inlines"->MetaValue.MetaInlines(emptyList());"list"->MetaValue.MetaList(listOf(MetaValue.MetaInlines(emptyList()),MetaValue.MetaString("x"),MetaValue.MetaBool(false),MetaValue.MetaInlines(listOf(Inline.Str("y")))));"empty-list"->MetaValue.MetaList(emptyList())
 "blocks"->MetaValue.MetaBlocks(listOf(Block.Para(listOf(Inline.Str("one"))),Block.Div(Attr(),listOf(Block.Plain(listOf(Inline.Str("two"))))),Block.CodeBlock(Attr(),"code"),Block.BlockQuote(listOf(Block.Plain(listOf(Inline.Str("quote"))))),Block.BulletList(listOf(listOf(Block.Plain(listOf(Inline.Str("item")))))),Block.DefinitionList(listOf(listOf(Inline.Str("term"))to listOf(listOf(Block.Para(listOf(Inline.Str("definition"))))))),Block.HorizontalRule))
 "line-block"->MetaValue.MetaBlocks(listOf(Block.LineBlock(listOf(listOf(Inline.Str("a")),emptyList(),listOf(Inline.Str("b"))))))
 "raw-block"->MetaValue.MetaBlocks(listOf(Block.RawBlock("html","raw")))
 "header"->MetaValue.MetaBlocks(listOf(Block.Header(3,Attr(),listOf(Inline.Str("header")))))
 "ordered-list"->MetaValue.MetaBlocks(listOf(Block.OrderedList(ListAttributes(3,ListNumberStyle.Decimal,ListNumberDelim.Period),listOf(listOf(Block.Plain(listOf(Inline.Str("one")))),listOf(Block.Para(listOf(Inline.Str("two"))))))))
 "figure"->MetaValue.MetaBlocks(listOf(Block.Figure(Attr(),Caption(null,listOf(Block.Plain(listOf(Inline.Str("caption"))))),listOf(Block.Plain(listOf(Inline.Str("body")))))))
 "table"->{fun row(s:String)=Row(Attr(),listOf(Cell(Attr(),Alignment.AlignDefault,1,1,listOf(Block.Plain(listOf(Inline.Str(s)))))))
 MetaValue.MetaBlocks(listOf(Block.Table(Attr(),Caption(null,listOf(Block.Plain(listOf(Inline.Str("caption"))))),listOf(ColSpec(Alignment.AlignLeft,ColWidth.Width(0.5))),TableHead(Attr(),listOf(row("head"))),listOf(TableBody(Attr(),0,listOf(row("body-head")),listOf(row("body")))),TableFoot(Attr(),listOf(row("foot"))))))}
 "deep-list"->{var out:MetaValue=MetaValue.MetaString("bottom");repeat(n){out=MetaValue.MetaList(listOf(out))};out}
 "deep-div"->{var out:Block=Block.Para(listOf(Inline.Str("bottom")));repeat(n){out=Block.Div(Attr(),listOf(out))};MetaValue.MetaBlocks(listOf(out))}
 else->error(kind)
}
class OrgReaderMetaCollectionTest {
 @org.junit.jupiter.api.TestFactory fun cases()=listOf("meta-collections.jsonl.gz","meta-collections-extra.jsonl.gz").flatMap{file->orgRows(file).filterNot{file=="meta-collections.jsonl.gz"&&it.getValue("request").jsonObject.let{q->q["operation"]?.jsonPrimitive?.content=="list"&&q["kind"]?.jsonPrimitive?.content in listOf("deep-list","deep-div")}}.mapIndexed{i,row->org.junit.jupiter.api.DynamicTest.dynamicTest("$file/$i"){verify(row)}}}
 private fun verify(row:JsonObject){val q=row.getValue("request").jsonObject;var meta=MetadataMap.empty<MetaValue>();val old=value(q.getValue("kind").jsonPrimitive.content,q["depth"]?.jsonPrimitive?.int?:0);if(old!=null)meta=meta.inserted("x"){old};val empty=q["empty"]?.jsonPrimitive?.boolean?:false;val tail=Inlines(if(empty)emptyList()else listOf(Inline.Str("tail")));repeat(q["repeat"]?.jsonPrimitive?.int?:1){val previous=meta;meta=meta.inserted("x"){if(q.getValue("operation").jsonPrimitive.content=="lines")orgLinesValue("x",tail,previous)else orgListValue("x",MetaValue.MetaInlines(tail.values),previous)}}
val doc=Pandoc(meta);val expected=row.getValue("expected");val actual=if((expected as?JsonObject)?.get("showDocument")!=null)buildJsonObject{put("showDocument",AstShow.show(doc))}else Json.parseToJsonElement(PandocJson.encode(doc));org.junit.jupiter.api.Assertions.assertTrue(orgJsonEqual(actual,expected),"request: $q\nmetadata observation mismatch")
 }
}
