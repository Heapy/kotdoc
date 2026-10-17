// Pinned Pandoc/source oracle checks. GPL-2.0-or-later; see licenses/.
package io.heapy.kotdoc.writers
import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.options.WriterOptions
import io.heapy.kotdoc.logging.LogMessage
import kotlinx.serialization.json.*
import java.util.zip.GZIPInputStream
import java.io.File
internal object AsciiDocDeepProof {
fun deepDocument(kind:String,depth:Int):Pandoc{
 val x=Inline.Str("x");val base=Block.Para(listOf(x))
 if(kind in listOf("emph","span","note")){var i:Inline=x;repeat(depth){i=when(kind){"emph"->Inline.Emph(listOf(i));"span"->Inline.Span(Attr(),listOf(i));else->Inline.Note(listOf(Block.Para(listOf(i))))}};return Pandoc(blocks=listOf(Block.Para(listOf(i))))}
 var b:Block=when(kind){"diagnostic"->Block.RawBlock(Format("unsupported"),"leaf");"bullet"->Block.BulletList(listOf(listOf(Block.Plain(listOf(x)))));"ordered"->Block.OrderedList(ListAttributes(),listOf(listOf(Block.Plain(listOf(x)))));else->base}
 repeat(depth){b=when(kind){"div","caption","diagnostic"->Block.Div(Attr(),listOf(b));"quote"->Block.BlockQuote(listOf(b));"bullet"->Block.BulletList(listOf(listOf(Block.Plain(listOf(x)),b)));"ordered"->Block.OrderedList(ListAttributes(),listOf(listOf(Block.Plain(listOf(x)),b)));"definition"->Block.DefinitionList(listOf(listOf(Inline.Str("term"))to listOf(listOf(b))));"table"->Block.Table(Attr(),Caption(null,emptyList()),listOf(ColSpec(Alignment.AlignDefault,ColWidth.Default)),TableHead(Attr(),emptyList()),listOf(TableBody(Attr(),0,emptyList(),listOf(Row(Attr(),listOf(Cell(Attr(),Alignment.AlignDefault,1,1,listOf(b))))))),TableFoot(Attr(),emptyList()));"mixed"->Block.BlockQuote(listOf(Block.Div(Attr(),listOf(Block.BulletList(listOf(listOf(base,b)))))));else->error(kind)}}
 if(kind=="caption") {
  val cell=Cell(Attr(),Alignment.AlignDefault,1,1,listOf(Block.Plain(listOf(Inline.Str("cell")))))
  val body=TableBody(Attr(),0,emptyList(),listOf(Row(Attr(),listOf(cell))))
  b=Block.Table(Attr(),Caption(null,listOf(b)),List(2){ColSpec(Alignment.AlignDefault,ColWidth.Default)},TableHead(Attr(),emptyList()),listOf(body),TableFoot(Attr(),emptyList()))
 }

 return Pandoc(blocks=listOf(b))
}
fun run(args:Array<String>){var count=0
 GZIPInputStream(File(args[0]).inputStream()).bufferedReader().useLines{lines->lines.forEach{line->val r=Json.parseToJsonElement(line).jsonObject;val q=r.getValue("request").jsonObject;val name=q.getValue("name").jsonPrimitive.content;println("RUN $name");val d=deepDocument(q.getValue("kind").jsonPrimitive.content,q.getValue("depth").jsonPrimitive.int);val logs=mutableListOf<LogMessage>();val output=if(q.getValue("legacy").jsonPrimitive.boolean)AsciiDocWriter.writeLegacy(WriterOptions(),d,logs::add)else AsciiDocWriter.write(WriterOptions(),d,logs::add);val expected=r.getValue("expected").jsonObject;check(output==expected.getValue("output").jsonPrimitive.content){"output $name"};val tags=logs.map{when(it){is LogMessage.BlockNotRendered->"BlockNotRendered";is LogMessage.InlineNotRendered->"InlineNotRendered";else->error("unexpected log")}};check(tags==expected.getValue("logs").jsonArray.map{it.jsonPrimitive.content}){"logs $name"};count++;println("PASS $name")}}
 println("PASS $count original deep observations")
}
}
