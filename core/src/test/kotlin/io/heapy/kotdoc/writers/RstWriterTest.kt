// Tests.Writers.RST, Copyright (C) 2006-2024 John MacFarlane; GPL-2.0-or-later.
package io.heapy.kotdoc.writers
import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.latex.LaTeXReaderText
import io.heapy.kotdoc.options.*
import io.heapy.kotdoc.templates.*
import io.heapy.kotdoc.errors.PandocError
import io.heapy.kotdoc.layout.CharacterStringChars
import io.heapy.kotdoc.logging.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.util.zip.GZIPInputStream
import kotlin.test.assertEquals
class RstWriterTest {
 fun options(q:JsonObject):WriterOptions {val o=q.getValue("options").jsonObject;return WriterOptions(wrapText=WrapOption.valueOf(o.getValue("wrap").jsonPrimitive.content),columns=o.getValue("columns").jsonPrimitive.long,extensions=Extensions.read(o.getValue("extensions").jsonArray.map{it.jsonPrimitive.content}),referenceLinks=o["referenceLinks"]?.jsonPrimitive?.boolean?:false,listTables=o["listTables"]?.jsonPrimitive?.boolean?:false,tableOfContents=o["toc"]?.jsonPrimitive?.boolean?:false,tocDepth=o["tocDepth"]?.jsonPrimitive?.long?:3,numberSections=o["numberSections"]?.jsonPrimitive?.boolean?:false,template=q["template"]?.takeUnless{it==JsonNull}?.let{(TemplateCompiler.Text.compile("oracle.rst",it.jsonPrimitive.content) as CompiledTemplate.Success).template},variables=o["variables"]?.let{(TemplateValues.Text.fromJson(it) as Val.MapVal).context}?:Context())}
 fun deep(q:JsonObject):Pandoc {val kind=q.getValue("kind").jsonPrimitive.content;val depth=q.getValue("depth").jsonPrimitive.int
  if(kind.startsWith("list-width-")){val width=when(kind){"list-width-nan"->Double.NaN;"list-width-inf"->Double.POSITIVE_INFINITY;"list-width-neg-inf"->Double.NEGATIVE_INFINITY;else->1e308};return Pandoc(blocks=listOf(Block.Table(Attr(),Caption(null,emptyList()),listOf(ColSpec(io.heapy.kotdoc.ast.Alignment.AlignDefault,ColWidth.Width(width))),TableHead(Attr(),emptyList()),listOf(TableBody(Attr(),0,emptyList(),listOf(Row(Attr(),listOf(Cell(Attr(),io.heapy.kotdoc.ast.Alignment.AlignDefault,1,1,listOf(Block.Plain(listOf(Inline.Str("x")))))))))),TableFoot(Attr(),emptyList()))))}
  if(kind=="emph"||kind=="note"||kind=="span"){var i:Inline=Inline.Str("x");repeat(depth){i=if(kind=="emph")Inline.Emph(listOf(i))else if(kind=="span")Inline.Span(Attr(),listOf(i))else Inline.Note(listOf(Block.Para(listOf(i))))};return Pandoc(blocks=listOf(Block.Para(listOf(i))))}
  if(kind=="caption"||kind=="grid-caption"){var b:Block=Block.Para(listOf(Inline.Str("x")));repeat(depth){b=Block.Div(Attr(),listOf(b))};val cols=List(if(kind=="caption")2 else 1){ColSpec(io.heapy.kotdoc.ast.Alignment.AlignDefault,ColWidth.Default)};return Pandoc(blocks=listOf(Block.Table(Attr(),Caption(null,listOf(b)),cols,TableHead(Attr(),emptyList()),listOf(TableBody(Attr(),0,emptyList(),listOf(Row(Attr(),listOf(Cell(Attr(),io.heapy.kotdoc.ast.Alignment.AlignDefault,1,1,listOf(Block.Plain(listOf(Inline.Str("cell")))))))))),TableFoot(Attr(),emptyList()))))}
  var b:Block=when(kind){"bullet"->Block.BulletList(listOf(listOf(Block.Plain(listOf(Inline.Str("x"))))));"ordered"->Block.OrderedList(ListAttributes(),listOf(listOf(Block.Plain(listOf(Inline.Str("x"))))));else->Block.Para(listOf(Inline.Str("x")))}
  repeat(depth){b=when(kind){
   "div"->Block.Div(Attr(),listOf(b))
   "quote"->Block.BlockQuote(listOf(b))
   "bullet"->Block.BulletList(listOf(listOf(Block.Plain(listOf(Inline.Str("x"))),b)))
   "ordered"->Block.OrderedList(ListAttributes(),listOf(listOf(Block.Plain(listOf(Inline.Str("x"))),b)))
   "definition"->Block.DefinitionList(listOf(listOf(Inline.Str("term")) to listOf(listOf(b))))
   "table"->{val cell=Cell(Attr(),io.heapy.kotdoc.ast.Alignment.AlignDefault,1,1,listOf(b));val row=Row(Attr(),listOf(cell));val body=TableBody(Attr(),0,emptyList(),listOf(row));Block.Table(Attr(),Caption(null,emptyList()),listOf(ColSpec(io.heapy.kotdoc.ast.Alignment.AlignDefault,ColWidth.Default)),TableHead(Attr(),emptyList()),listOf(body),TableFoot(Attr(),emptyList()))}
   "mixed"->{val bullet=Block.BulletList(listOf(listOf(Block.Para(listOf(Inline.Str("x"))),b)));Block.BlockQuote(listOf(Block.Div(Attr(),listOf(bullet))))}
   else->error(kind)
  }}

  return Pandoc(blocks=listOf(b))
 }

 private fun bytes(name:String)=checkNotNull(javaClass.getResourceAsStream("/rst-writer/$name")).use{it.readBytes()}
 private fun rows(name:String)=GZIPInputStream(bytes(name).inputStream()).bufferedReader().use{r->r.readLines().map{Json.parseToJsonElement(it).jsonObject}}
 private fun inlineDocument(xs:JsonElement)=PandocJson.decode(buildJsonObject{put("pandoc-api-version",JsonArray(listOf(1,23,1,2).map(::JsonPrimitive)));put("meta",JsonObject(emptyMap()));put("blocks",JsonArray(listOf(buildJsonObject{put("t","Plain");put("c",xs)})))})
 private fun sourceLines(s:String)=if(s.isEmpty())emptyList()else s.split('\n').let{if(s.endsWith('\n'))it.dropLast(1)else it}
 private fun observe(q:JsonObject):JsonObject {val logs=mutableListOf<LogMessage>();val d=q["deep"]?.let{deep(it.jsonObject)}?:PandocJson.decode(q.getValue("document").toString());return try{val output=RstWriter.write(options(q),d,logs::add);buildJsonObject{put("name",q.getValue("name"));put("output",output);put("logs",Json.parseToJsonElement(Logging.encodeLogMessagesText(logs)))}}catch(e:PandocError){buildJsonObject{put("name",q.getValue("name"));put("error",CharacterStringChars.build(e.show()))}}}
 @TestFactory fun original()=rows("original.jsonl.gz").map{r->DynamicTest.dynamicTest(r.getValue("name").jsonPrimitive.content){
  if("flattenInput" in r){val input=(inlineDocument(JsonArray(listOf(r.getValue("flattenInput")))).blocks.single()as Block.Plain).content.single();val expected=(inlineDocument(r.getValue("flattenExpected")).blocks.single()as Block.Plain).content;assertEquals(expected,RstWriter.flatten(input))}
  else{val actual=RstWriter.write(options(r),PandocJson.decode(r.getValue("document").toString()));assertEquals(r.getValue("output").jsonPrimitive.content,actual);assertEquals(sourceLines(r.getValue("expected").jsonPrimitive.content),sourceLines(actual))}
 }}
 @TestFactory fun sourceBranches()=listOf("observations","extra","boundaries","captions","casefolds","identifier-profiles","display-math-stop","deep","widths","goldens","command-writer").flatMap{name->rows("$name.jsonl.gz").map{r->DynamicTest.dynamicTest(r.getValue("request").jsonObject.getValue("name").jsonPrimitive.content){assertEquals(r.getValue("expected"),observe(r.getValue("request").jsonObject))}}}
 @TestFactory fun classifierDomain()=listOf(DynamicTest.dynamicTest("all1114112 pinnedGHC predicates"){val expected=GZIPInputStream(bytes("classifiers.txt.gz").inputStream()).use{it.readBytes()};assertEquals(1114112,expected.size);for(c in expected.indices)assertEquals(expected[c].toInt()-32,RstWriter.flags(c),"U+${c.toString(16)}")})
 @TestFactory fun textLowercaseDomain()=listOf(DynamicTest.dynamicTest("all1114112 sourceText2.1.2 lower mappings"){val mappings=Json.parseToJsonElement(bytes("lowercase-mappings.json").decodeToString()).jsonArray.associate{it.jsonArray[0].jsonPrimitive.int to it.jsonArray[1].jsonPrimitive.content};assertEquals(1433,mappings.size);for(c in 0..0x10ffff){val input=if(c in 0xd800..0xdfff)"\uFFFD"else String(Character.toChars(c));assertEquals(mappings[c]?:input,LaTeXReaderText.toLower(input),"U+${c.toString(16)}")}})

}
