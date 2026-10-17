package io.heapy.kotdoc.readers

import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.latex.LaTeXReaderText
import io.heapy.kotdoc.logging.Logging
import io.heapy.kotdoc.options.*
import io.heapy.kotdoc.state.PandocStateContext
import io.heapy.kotdoc.writers.MuseWriter
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.util.zip.GZIPInputStream
import kotlin.test.*

/** Expectations execute the pinned Shared.uniqueIdent and full original format modules. */
class IdentifierProfileTest {
 private fun resource(name:String)=checkNotNull(javaClass.getResourceAsStream("/identifier-profile/$name"))
 private fun records(name:String)=GZIPInputStream(resource("$name.jsonl.gz")).bufferedReader().useLines{it.map{line->Json.parseToJsonElement(line).jsonObject}.toList()}
 private fun exts(q:JsonObject)=Extensions.read(q["extensions"]?.jsonArray?.map{it.jsonPrimitive.content}?:emptyList())
 private fun check(record:JsonObject,kind:String){
  val q=record.getValue("request").jsonObject;val expected=record.getValue("expected").jsonObject
  when(kind){
   "helper"->{val xs=PandocJson.decode("""{"pandoc-api-version":[1,23,1,2],"meta":{},"blocks":[{"t":"Plain","c":${q.getValue("inlines")}}]}""").blocks.single() as Block.Plain;assertEquals(expected.getValue("identifier").jsonPrimitive.content,DokuWikiIdentifiers.unique(xs.content,q.getValue("used").jsonArray.map{it.jsonPrimitive.content}.toSet(),exts(q)))}
   "muse-writer"->{val o=q.getValue("options").jsonObject;val options=WriterOptions(wrapText=WrapOption.valueOf(o.getValue("wrap").jsonPrimitive.content),columns=o.getValue("columns").jsonPrimitive.long,extensions=exts(o),referenceLocation=ReferenceLocation.valueOf(o.getValue("referenceLocation").jsonPrimitive.content),dpi=o.getValue("dpi").jsonPrimitive.long,tabStop=o.getValue("tabStop").jsonPrimitive.long);val runtime=PandocStateContext();val logs=mutableListOf<io.heapy.kotdoc.logging.LogMessage>();val output=MuseWriter.write(options,PandocJson.decode(q.getValue("document").toString()),logs::add);assertEquals(expected.getValue("output").jsonPrimitive.content,output);assertEquals(expected.getValue("logs"),Json.parseToJsonElement(Logging.encodeLogMessagesText(logs)))}
   else->{val runtime=PandocStateContext();val options=ReaderOptions(extensions=exts(q));val input=q.getValue("input").jsonPrimitive.content;val d=if(kind=="dokuwiki-reader")DokuWikiReader.read(options,input,runtime)else MuseReader.read(options,input,runtime);assertEquals(expected.getValue("document"),Json.parseToJsonElement(PandocJson.encode(d)));assertEquals(expected.getValue("logs"),Json.parseToJsonElement(Logging.encodeLogMessagesText(runtime.getLog())))}
  }
 }
 @TestFactory fun originalProfileObservations()=listOf("helper","dokuwiki-reader","muse-reader","muse-writer").flatMap{kind->records(kind).map{r->DynamicTest.dynamicTest(r.getValue("request").jsonObject.getValue("name").jsonPrimitive.content){check(r,kind)}}}
 @Test fun completeLowercaseDomain(){val mappings=Json.parseToJsonElement(resource("lowercase-mappings.json").bufferedReader().readText()).jsonArray.associate{it.jsonArray[0].jsonPrimitive.int to it.jsonArray[1].jsonPrimitive.content};assertEquals(1433,mappings.size);for(cp in 0..0x10ffff){val input=if(cp in 0xd800..0xdfff)"\uFFFD"else String(Character.toChars(cp));assertEquals(mappings[cp]?:input,LaTeXReaderText.toLower(input),"U+${cp.toString(16)}")}}
 @Test fun unchangedCompleteIdentifierFlags(){val bytes=GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/dokuwiki-reader/identifier-flags-expanded.txt.gz"))).use{it.readBytes()};assertEquals(1114112,bytes.size);for(cp in bytes.indices)assertEquals(bytes[cp].toInt()-65,DokuWikiIdentifiers.flags(cp),"U+${cp.toString(16)}")}
 companion object{@JvmStatic fun main(args:Array<String>){val suite=IdentifierProfileTest();val cases=suite.originalProfileObservations();for(case in cases)case.executable.execute();suite.completeLowercaseDomain();suite.unchangedCompleteIdentifierFlags();println("${cases.size} original identifier/consumer observations and two complete1114112domains passed")}}
}
