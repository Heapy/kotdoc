// Pinned original Pandoc Org writer tests and independently captured observations.
// Copyright (C) Pandoc authors; GNU GPL version 2 or later.
package io.heapy.kotdoc.writers
import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.options.*
import io.heapy.kotdoc.logging.*
import io.heapy.kotdoc.templates.*
import io.heapy.kotdoc.layout.*
import io.heapy.kotdoc.parsing.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.assertEquals
import java.util.zip.GZIPInputStream
class OrgWriterTest {
 private fun bytes(name:String)=checkNotNull(javaClass.getResourceAsStream("/org-writer/$name")).use{it.readBytes()}
 private fun rows(name:String)=GZIPInputStream(bytes("$name.jsonl.gz").inputStream()).bufferedReader().use{r->r.readLines().map{Json.parseToJsonElement(it).jsonObject}}
 private fun options(q:JsonObject):WriterOptions {val o=q.getValue("options").jsonObject;return WriterOptions(wrapText=WrapOption.valueOf(o.getValue("wrap").jsonPrimitive.content),columns=o.getValue("columns").jsonPrimitive.long,extensions=Extensions.fromJson(o.getValue("extensions")),variables=o["variables"]?.let{(TemplateValues.Text.fromJson(it)as Val.MapVal).context}?:Context(),template=q["template"]?.let{(TemplateCompiler.Text.compile("oracle.org",it.jsonPrimitive.content)as CompiledTemplate.Success).template})}
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

 private fun observe(q:JsonObject):JsonObject {val logs=mutableListOf<LogMessage>();val document=q["deep"]?.let{deep(it.jsonObject)}?:PandocJson.decode(q.getValue("document").toString());val output=OrgWriter.write(options(q),document,logs::add);return buildJsonObject{put("name",q.getValue("name"));put("output",output);put("logs",Json.parseToJsonElement(Logging.encodeLogMessagesText(logs)))}}
 @TestFactory fun original()=rows("original8").map{r->DynamicTest.dynamicTest(r.getValue("name").jsonPrimitive.content){assertEquals(r.getValue("expected").jsonPrimitive.content,OrgWriter.write(options(r),PandocJson.decode(r.getValue("document").toString())))}}
 @TestFactory fun sourceBranches()=listOf("writer-observations","tables-observations","goldens-observations","extra-observations","deep-observations","header-int64-observations","tag-width-observations").flatMap{name->rows(name).map{r->DynamicTest.dynamicTest(r.getValue("request").jsonObject.getValue("name").jsonPrimitive.content){assertEquals(r.getValue("expected"),observe(r.getValue("request").jsonObject))}}}
 private fun decode(a:JsonElement):List<Inline> = (PandocJson.decode("""{"pandoc-api-version":[1,23,1],"meta":{},"blocks":[{"t":"Plain","c":$a}]}""").blocks.single()as Block.Plain).content
 private fun encode(a:List<Inline>):JsonElement = Json.parseToJsonElement(PandocJson.encode(Pandoc(blocks=listOf(Block.Plain(a))))).jsonObject.getValue("blocks").jsonArray.single().jsonObject.getValue("c")
 @TestFactory fun locator()=rows("locator-observations").map{r->val q=r.getValue("request").jsonObject;DynamicTest.dynamicTest(q.getValue("name").jsonPrimitive.content){val xs=decode(q.getValue("input"));val actual=when(q.getValue("kind").jsonPrimitive.content){"csl"->JsonPrimitive(OrgCitationLocator.cslJsonify(xs));"split"->encode(OrgCitationLocator.split(xs));"comma"->encode(OrgCitationLocator.maybeAddComma(xs));else->{val(info,rest)=OrgCitationLocator.parse(xs);buildJsonObject{put("info",info?.let{buildJsonObject{put("raw",it.raw);put("label",it.label);put("locator",it.locator)}}?:JsonNull);put("suffix",encode(rest))}}};assertEquals(r.getValue("expected").jsonObject.getValue("result"),actual)}}
 @TestFactory fun privateHelpers():List<DynamicTest>{val cls=Class.forName("io.heapy.kotdoc.writers.OrgWriter\$Session");val instance=cls.declaredConstructors.single().apply{isAccessible=true}.newInstance(WriterOptions(),{_:LogMessage->Unit});return rows("helper-observations").map{r->val q=r.getValue("request").jsonObject;DynamicTest.dynamicTest(q.getValue("name").jsonPrimitive.content){val kind=q.getValue("kind").jsonPrimitive.content;val method=cls.getDeclaredMethod(when(kind){"target"->"escapeTarget";"description"->"escapeDescription";"path"->"path";"language"->"language";"special"->"special";else->"escape"},String::class.java).apply{isAccessible=true};val value=method.invoke(instance,q.getValue("input").jsonPrimitive.content);@Suppress("UNCHECKED_CAST")val actual=if(kind=="escape")DocLayout.Text.render(null,value as Doc<String>)else value as String;assertEquals(r.getValue("expected").jsonObject.getValue("result"),JsonPrimitive(actual))}}}
 @TestFactory fun predicateDomain()=listOf(DynamicTest.dynamicTest("all1114112 original GHC predicate values"){val expected=GZIPInputStream(bytes("classifiers.bin.gz").inputStream()).use{it.readBytes()};assertEquals(1114112,expected.size);for(cp in expected.indices){val actual=(if(ParserCharacters.isSpace(cp))1 else 0)+(if(RegexCharacters.categoryName(cp)in setOf("Pc","Pd","Ps","Pe","Pi","Pf","Po"))2 else 0)+(if(ParserCharacters.isDigit(cp))4 else 0)+(if(ParserCharacters.isAlphaNum(cp))8 else 0);assertEquals(expected[cp].toInt(),actual,"U+${cp.toString(16)}")}})
 @TestFactory fun casefoldDomain()=listOf(DynamicTest.dynamicTest("all1114112 text2.1.2 casefold values"){val mappings=Json.parseToJsonElement(bytes("casefold-mappings.json").decodeToString()).jsonArray.associate{it.jsonArray[0].jsonPrimitive.int to it.jsonArray[1].jsonPrimitive.content};assertEquals(1557,mappings.size);for(cp in 0..0x10ffff){val input=String(Character.toChars(cp));assertEquals(mappings[cp]?:if(cp in 0xd800..0xdfff)"\ufffd"else input,OrgCaseFold.fold(input),"U+${cp.toString(16)}")}})
}
