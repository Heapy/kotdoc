package io.heapy.kotdoc.writers
import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.options.*
import io.heapy.kotdoc.templates.*
import io.heapy.kotdoc.errors.PandocError
import io.heapy.kotdoc.layout.CharacterStringChars
import io.heapy.kotdoc.logging.*
import io.heapy.kotdoc.parsing.ParserCharacters
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.util.zip.GZIPInputStream
import kotlin.test.*
/** Complete original Muse writer assertions and independent original-module observations. */
class MuseWriterTest {
 fun resource(name:String)=checkNotNull(javaClass.getResourceAsStream("/muse-writer/$name"))
 fun records(name:String)=GZIPInputStream(resource("$name.jsonl.gz")).bufferedReader().useLines{it.map{line->Json.parseToJsonElement(line).jsonObject}.toList()}
 fun baseOptions(o:JsonObject)=WriterOptions(wrapText=WrapOption.valueOf(o.getValue("wrap").jsonPrimitive.content),columns=o.getValue("columns").jsonPrimitive.long,extensions=Extensions.read(o.getValue("extensions").jsonArray.map{it.jsonPrimitive.content}),referenceLocation=ReferenceLocation.valueOf(o.getValue("referenceLocation").jsonPrimitive.content),dpi=o.getValue("dpi").jsonPrimitive.long,tabStop=o.getValue("tabStop").jsonPrimitive.long)
 fun options(q:JsonObject):WriterOptions {val o=q.getValue("options").jsonObject;return baseOptions(o).copy(template=q["template"]?.let{(TemplateCompiler.Text.compile("oracle.muse",it.jsonPrimitive.content) as CompiledTemplate.Success).template},variables=o["variables"]?.let{(TemplateValues.Text.fromJson(it) as Val.MapVal).context}?:Context())}
 fun deep(q:JsonObject):Pandoc {val kind=q.getValue("kind").jsonPrimitive.content;val depth=q.getValue("depth").jsonPrimitive.int
  if(kind=="emph"||kind=="note"){var i:Inline=Inline.Str("x");repeat(depth){i=if(kind=="emph")Inline.Emph(listOf(i))else Inline.Note(listOf(Block.Para(listOf(i))))};return Pandoc(blocks=listOf(Block.Para(listOf(i))))}
  if(kind=="grid-caption-poison"){
   val poison=object:AbstractList<Block>(){override val size:Int get()=1;override fun get(index:Int):Block=error("caption forced")}
   val cell=Cell(Attr(),io.heapy.kotdoc.ast.Alignment.AlignDefault,1,1,listOf(Block.Plain(listOf(Inline.Str("cell")))))
   val body=TableBody(Attr(),0,emptyList(),listOf(Row(Attr(),listOf(cell))))
   return Pandoc(blocks=listOf(Block.Table(Attr(),Caption(null,poison),listOf(ColSpec(io.heapy.kotdoc.ast.Alignment.AlignDefault,ColWidth.Default)),TableHead(Attr(),emptyList()),listOf(body),TableFoot(Attr(),emptyList()))))
  }
  if(kind=="caption"||kind=="grid-caption"){
   var cap:Block=Block.Para(listOf(Inline.Str("x")));repeat(depth){cap=Block.Div(Attr(),listOf(cap))}
   val cell=Cell(Attr(),io.heapy.kotdoc.ast.Alignment.AlignDefault,1,1,listOf(Block.Plain(listOf(Inline.Str("cell")))))
   val body=TableBody(Attr(),0,emptyList(),listOf(Row(Attr(),listOf(cell))))
   val table=Block.Table(Attr(),Caption(null,listOf(cap)),List(if(kind=="caption")2 else 1){ColSpec(io.heapy.kotdoc.ast.Alignment.AlignDefault,ColWidth.Default)},TableHead(Attr(),emptyList()),listOf(body),TableFoot(Attr(),emptyList()))
   return Pandoc(blocks=listOf(table))
  }
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

 fun check(r:JsonObject){val q=r.getValue("request").jsonObject;val expected=r.getValue("expected");val logs=mutableListOf<LogMessage>();val d=q["deep"]?.let{deep(it.jsonObject)}?:PandocJson.decode(q.getValue("document").toString())
  val actual=try{val output=MuseWriter.write(options(q),d,logs::add);buildJsonObject{put("name",q.getValue("name"));put("output",output);put("logs",Json.parseToJsonElement(Logging.encodeLogMessagesText(logs)))}}catch(error:PandocError){buildJsonObject{put("name",q.getValue("name"));put("error",CharacterStringChars.build(error.show()))}}
  assertEquals(expected,actual)
 }
 fun lines(s:String)=if(s.isEmpty())emptyList()else s.removeSuffix("\n").split('\n')
 @TestFactory fun all164OriginalAssertions()=records("original164").map{r->DynamicTest.dynamicTest("original/"+r.getValue("name").jsonPrimitive.content){val actual=MuseWriter.write(baseOptions(r.getValue("options").jsonObject),PandocJson.decode(r.getValue("document").toString()));assertEquals(lines(r.getValue("expected").jsonPrimitive.content),lines(actual));assertEquals(r.getValue("output").jsonPrimitive.content,actual)}}
 @TestFactory fun completeOriginalModule()=listOf("observations","extra","deep","goldens","large-widths","caption-depth","caption-shapes","caption-demand").flatMap{name->records(name).map{r->DynamicTest.dynamicTest(r.getValue("request").jsonObject.getValue("name").jsonPrimitive.content){check(r)}}}
 @Test fun fullOriginalCharacterPredicateDomain(){val bytes=GZIPInputStream(resource("classifiers.txt.gz")).use{it.readBytes()};assertEquals(1114112,bytes.size);for(p in bytes.indices){val actual=(if(ParserCharacters.isAlphaNum(p))1 else 0)+(if(ParserCharacters.isDigit(p))2 else 0)+(if(ParserCharacters.isSpace(p))4 else 0)+(if(p in 97..122)8 else 0)+(if(p in 65..90)16 else 0);assertEquals(bytes[p].toInt()-32,actual,"U+${p.toString(16)}")}}
 companion object {@JvmStatic fun main(args:Array<String>){val suite=MuseWriterTest();val cases=suite.all164OriginalAssertions()+suite.completeOriginalModule();for(case in cases)case.executable.execute();suite.fullOriginalCharacterPredicateDomain();println("Muse writer: ${cases.size} observations plus all1114112 five-predicate points passed")}}
}
