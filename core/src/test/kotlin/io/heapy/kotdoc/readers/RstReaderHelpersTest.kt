package io.heapy.kotdoc.readers
import io.heapy.kotdoc.parsing.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.util.zip.GZIPInputStream
import kotlin.test.assertEquals

/** Exact original gridtables contracts and independent original CSV/grid observations. */
class RstReaderHelpersTest {
 private fun grid(x:RstGridTable.Table):JsonObject=buildJsonObject{
  put("rows",buildJsonArray{for(row in x.rows)add(buildJsonArray{for(c in row)add(buildJsonObject{put("lines",buildJsonArray{c.lines.forEach{add(it)}});put("rowSpan",c.rowSpan);put("colSpan",c.colSpan)})})});put("head",x.head?.let(::JsonPrimitive)?:JsonNull);put("foot",x.foot?.let(::JsonPrimitive)?:JsonNull);put("specs",buildJsonArray{x.specs.forEach{(a,w)->add(buildJsonArray{add(a.name);add(w)})}})
 }
 private fun rows(name:String)=GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/rst-reader-helpers/$name.jsonl.gz"))).bufferedReader().useLines{it.map{line->Json.parseToJsonElement(line).jsonObject}.toList()}
 fun check(r:JsonObject){val q=r.getValue("request").jsonObject;val expected=r.getValue("expected").jsonObject.getValue("result");val input=q.getValue("input").jsonPrimitive.content;val kind=q.getValue("kind").jsonPrimitive.content
  val actual:JsonElement=when(kind){
   "csv"->{val o=RstCsvOptions(q["delimiter"]?.jsonPrimitive?.content?.codePointAt(0)?:44,if(q.containsKey("quote")){if(q["quote"]==JsonNull)null else q.getValue("quote").jsonPrimitive.content.codePointAt(0)}else 34,q["keepSpace"]?.jsonPrimitive?.boolean?:false,q["escape"]?.let{if(it==JsonNull)null else it.jsonPrimitive.content.codePointAt(0)});val p=RstCsv.parse(o,input);if(p.success)buildJsonObject{put("rows",buildJsonArray{p.value!!.forEach{add(buildJsonArray{it.forEach{add(it)}})}})}else buildJsonObject{put("error",p.error.showText())}}
   "grid-raw"->{val p=RstGridTable.parser<Unit>().run(ParseState(ParseInput(TextStream(input)),SourcePos("grid"),Unit));if(p.success){val table=p.value!!;buildJsonObject{put("table",grid(table));put("bounds",buildJsonArray{add(buildJsonArray{add(1);add(1)});add(buildJsonArray{add(table.rawCells.size);add(table.rawCells.firstOrNull()?.size?:0)})});put("cells",buildJsonArray{for(row in table.rawCells)for(cell in row)add(when(cell){is RstGridTable.RawCell.Content->buildJsonObject{put("content",buildJsonArray{cell.cell.lines.forEach{add(it)}});put("rowSpan",cell.cell.rowSpan);put("colSpan",cell.cell.colSpan)};is RstGridTable.RawCell.Continuation->buildJsonObject{put("continuation",buildJsonArray{add(cell.row);add(cell.column)})}})})}}else buildJsonObject{put("error",p.error.showText())}}
   "grid-follow","grid-follow-blank"->{val following=if(kind=="grid-follow-blank")Parsec.char<Unit>(10).then(Parsec.satisfy<Unit>{ParserCharacters.isAlpha(it)||ParserCharacters.isSpace(it)}.many1())else Parsec.satisfy<Unit>(ParserCharacters::isAlpha).many1();val p=RstGridTable.parser<Unit>().then(following).run(ParseState(ParseInput(TextStream(input)),SourcePos("grid"),Unit));if(p.success)buildJsonObject{put("text",buildString{p.value!!.forEach(::appendCodePoint)})}else buildJsonObject{put("error",p.error.showText())}}
   "lines"->{val p=RstGridTable.tableLine<Unit>().many().run(ParseState(ParseInput(TextStream(input)),SourcePos("grid"),Unit));if(p.success)buildJsonObject{put("lines",buildJsonArray{p.value!!.forEach{add(it)}});put("rest",(p.state.input.stream as TextStream).text())}else buildJsonObject{put("error",p.error.showText())}}
   "grid-rows"->{val raw=arrayOf(arrayOf<RstGridTable.Cell?>(RstGridTable.Cell(listOf("1"),2,1),RstGridTable.Cell(listOf("2"))),arrayOf<RstGridTable.Cell?>(null,RstGridTable.Cell(listOf("3"))));grid(RstGridTable.Table(RstGridTable.rowView(raw,setOf(2 to 1)),null,null,listOf(io.heapy.kotdoc.ast.Alignment.AlignDefault to 5,io.heapy.kotdoc.ast.Alignment.AlignDefault to 7)))}
   "grid-trace"->RstGridTable.trace(input.removeSuffix("\n").split('\n'))?.let(::grid)?:JsonNull
   "grid"->{val p=RstGridTable.parser<Unit>().run(ParseState(ParseInput(TextStream(input)),SourcePos("grid"),Unit));if(p.success)buildJsonObject{put("table",grid(p.value!!));val rest=(p.state.input.stream as TextStream).text();put("rest",rest)}else buildJsonObject{put("error",p.error.showText())}}
   else->error(kind)
  }

 assertEquals(expected,actual,q.getValue("name").jsonPrimitive.content)
 }
 @TestFactory fun originalHelpers()=listOf("observations","postgrid","deep","original-raw").flatMap{name->rows(name).map{r->DynamicTest.dynamicTest(r.getValue("request").jsonObject.getValue("name").jsonPrimitive.content){check(r)}}}
 companion object{@JvmStatic fun main(args:Array<String>){val suite=RstReaderHelpersTest();val tests=suite.originalHelpers();for(test in tests)test.executable.execute();println("${tests.size} original RST reader CSV/grid helper observations passed")}}
}
