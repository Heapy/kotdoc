// Pinned Org/toLegacyTable demand, GPL-2.0-or-later; Pandoc authors.
package io.heapy.kotdoc.writers
import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.options.WriterOptions
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.assertEquals
import java.util.zip.GZIPInputStream
class OrgWriterDemandTest {
 @TestFactory fun ignoredColumnValues():List<DynamicTest> = cases("demand-observations")+cases("demand-extra-observations")
 private fun cases(name:String):List<DynamicTest> = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/org-writer/$name.jsonl.gz"))).bufferedReader().use{reader->reader.readLines().map{line->val row=Json.parseToJsonElement(line).jsonObject;val kind=row.getValue("request").jsonObject.getValue("kind").jsonPrimitive.content;DynamicTest.dynamicTest(kind){
  val events=mutableListOf<String>()
  fun failAt(event:String,message:String):Nothing{events.add("EVENT $event");error(message)}
  val observed=try{
   // Definition.hs uses StrictData: a bottom list root fails while constructing
   // Table; a valid list spine may still contain an unforced pair/tuple fields.
   val specs=if(kind=="bad-root")failAt("column-root","column root forced")else object:AbstractList<ColSpec>(){
    override val size:Int get()=when(kind){"empty"->0;"bad-spine-tail","empty-sections","empty-body","empty-head-row","empty-foot-row"->failAt("column-spine","column spine forced");else->1}
    override fun get(index:Int):ColSpec=when(kind){"bad-pair"->failAt("column-pair","column pair forced");"bad-tuple-fields"->ColSpec(failAt("alignment","alignment forced"),ColWidth.Default);else->error("unused column element")}
   }
   // This adapter memoizes a deferred Inline constructor, as Haskell does.
   var forced=false
   val caption=if(kind in setOf("bad-spine-tail","empty-sections","empty-body","empty-head-row","empty-foot-row"))Caption(null,listOf(Block.Plain(object:AbstractList<Inline>(){
    override val size=1
    override fun get(index:Int):Inline{if(!forced){events.add("EVENT caption");forced=true};return Inline.Str("cap")}
   })))else Caption(null,emptyList())
   val cell=Cell(Attr(),io.heapy.kotdoc.ast.Alignment.AlignDefault,1,1,listOf(Block.Plain(listOf(Inline.Str("x")))))
   val bodies=when(kind){"empty-sections","empty-head-row","empty-foot-row"->emptyList();"empty-body"->listOf(TableBody(Attr(),0,emptyList(),emptyList()));else->listOf(TableBody(Attr(),0,emptyList(),listOf(Row(Attr(),listOf(cell)))))}
   val table=Block.Table(Attr(),caption,specs,TableHead(Attr(),if(kind=="empty-head-row")listOf(Row(Attr(),emptyList()))else emptyList()),bodies,TableFoot(Attr(),if(kind=="empty-foot-row")listOf(Row(Attr(),emptyList()))else emptyList()))
   val output=OrgWriter.write(WriterOptions(),Pandoc(blocks=listOf(table)))
   buildJsonObject{put("kind",kind);put("output",output)}
  }catch(e:IllegalStateException){buildJsonObject{put("kind",kind);put("error",e.message)}}
  assertEquals(row.getValue("expected"),observed)
  assertEquals(row.getValue("events"),JsonArray(events.map(::JsonPrimitive)))
 }}}
}
