// Pinned Pandoc writer expectations; Copyright Pandoc authors. GPL-2.0-or-later.
package io.heapy.kotdoc.writers
import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.options.*
import io.heapy.kotdoc.templates.*
import io.heapy.kotdoc.logging.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File
import java.util.zip.GZIPInputStream
class AsciiDocWriterTest {
 private val fixtures=File("../tools/haskell-asciidoc-writer-reference/fixtures")
 private fun rows(name:String)=GZIPInputStream(File(fixtures,"$name.jsonl.gz").inputStream()).bufferedReader().use{it.readLines().map{line->Json.parseToJsonElement(line).jsonObject}}
 private fun observe(q:JsonObject):JsonObject {
  val o=q["options"]?.jsonObject?:JsonObject(emptyMap())
  val options=WriterOptions(wrapText=o["wrap"]?.jsonPrimitive?.content?.let(WrapOption::valueOf)?:WrapOption.WrapAuto,columns=o["columns"]?.jsonPrimitive?.long?:72,extensions=o["extensions"]?.let(Extensions::fromJson)?:Extensions.Empty,tableOfContents=o["toc"]?.jsonPrimitive?.boolean?:false,dpi=o["dpi"]?.jsonPrimitive?.long?:96,variables=o["variables"]?.let{(TemplateValues.Text.fromJson(it)as Val.MapVal).context}?:Context(),template=q["template"]?.let{(TemplateCompiler.Text.compile("oracle.asciidoc",it.jsonPrimitive.content)as CompiledTemplate.Success).template})
  val logs=mutableListOf<LogMessage>();val doc=q["deep"]?.let { value ->
   val kind=value.jsonObject.getValue("kind").jsonPrimitive.content
   val width=when(kind){"list-width-nan"->Double.NaN;"list-width-inf"->Double.POSITIVE_INFINITY;"list-width-neg-inf"->Double.NEGATIVE_INFINITY;else->1e308}
   Pandoc(blocks=listOf(Block.Table(Attr(),Caption(null,emptyList()),listOf(ColSpec(io.heapy.kotdoc.ast.Alignment.AlignDefault,ColWidth.Width(width))),TableHead(Attr(),emptyList()),listOf(TableBody(Attr(),0,emptyList(),listOf(Row(Attr(),listOf(Cell(Attr(),io.heapy.kotdoc.ast.Alignment.AlignDefault,1,1,listOf(Block.Plain(listOf(Inline.Str("x")))))))))),TableFoot(Attr(),emptyList()))))
  }?:PandocJson.decode(q.getValue("document"))
  val output=if(q["legacy"]?.jsonPrimitive?.boolean==true)AsciiDocWriter.writeLegacy(options,doc,logs::add)else AsciiDocWriter.write(options,doc,logs::add)
  return buildJsonObject{put("name",q.getValue("name"));put("output",output);put("logs",Json.parseToJsonElement(Logging.encodeLogMessagesText(logs)))}
 }
 @TestFactory fun integrity():List<DynamicTest> {
  val manifest=Json.parseToJsonElement(File(fixtures.parentFile,"provenance.json").readText()).jsonObject
  return manifest.getValue("files").jsonObject.map{(name,pin)->DynamicTest.dynamicTest("source-pin/$name"){
   val historical=Json.parseToJsonElement(File("../tools/haskell-asciidoc-list-reference/historical115-bindings.json").readText()).jsonObject
   val source=historical[name]?.jsonPrimitive?.content?:name
   val actual=java.security.MessageDigest.getInstance("SHA-256").digest(File("..",source).readBytes()).joinToString(""){"%02x".format(it.toInt() and 255)}
   check(actual==pin.jsonPrimitive.content){"Changed source/tool/fixture: $name"}
  }}
 }
 @TestFactory fun original()=rows("original12").map{r->DynamicTest.dynamicTest(r.getValue("name").jsonPrimitive.content){val doc=PandocJson.decode(r.getValue("document"));val actual=if(r.getValue("legacy").jsonPrimitive.boolean)AsciiDocWriter.writeLegacy(WriterOptions(),doc)else AsciiDocWriter.write(WriterOptions(),doc);check(actual==r.getValue("actual").jsonPrimitive.content)}}
 @TestFactory fun sourceBranches()=listOf("goldens","branches","command-ast","header-int64","extra","nonfinite-width").flatMap{name->rows(name).map{r->val q=r.getValue("request").jsonObject;DynamicTest.dynamicTest(q.getValue("name").jsonPrimitive.content){check(observe(q)==r.getValue("expected")){q.getValue("name").toString()}}}}
 @TestFactory fun dependencyClosures()=listOf(
 "tables/2460" to {AsciiDocTableProof.run(arrayOf(File(fixtures,"table-helpers.jsonl.gz").path))},
 "scalar/12644" to {AsciiDocScalarProof.run(arrayOf(File(fixtures,"scalars.jsonl.gz").path))},
 "chomp/11154" to {AsciiDocChompProof.run(arrayOf(File(fixtures,"chomp-metrics.jsonl.gz").path))},
 "sections/281" to {AsciiDocSectionsProof.run(arrayOf(File(fixtures,"sections.jsonl.gz").path))},
 "sections-second/281" to {AsciiDocSectionsProof.run(arrayOf(File(fixtures,"sections-second.jsonl.gz").path))},
 "profile/1114112+5804" to {AsciiDocProfileProof.run(arrayOf(fixtures.path))},
 "demand/8" to {AsciiDocDemandProof.run(arrayOf(File(fixtures,"demand-native.jsonl.gz").path))},
 "deep/72" to {AsciiDocDeepProof.run(arrayOf(File(fixtures,"deep.jsonl.gz").path))}
 ).map{(name,run)->DynamicTest.dynamicTest(name){run()}}
}
