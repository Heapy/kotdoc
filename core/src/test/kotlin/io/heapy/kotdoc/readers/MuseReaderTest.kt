package io.heapy.kotdoc.readers

import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.errors.PandocError
import io.heapy.kotdoc.layout.CharacterStringChars
import io.heapy.kotdoc.logging.Logging
import io.heapy.kotdoc.options.*
import io.heapy.kotdoc.parsing.*
import io.heapy.kotdoc.state.PandocStateContext
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.util.zip.GZIPInputStream
import kotlin.test.*

/** Original literal expectations and observations of the complete pinned reader. */
class MuseReaderTest {
    private fun resource(name:String)=checkNotNull(javaClass.getResourceAsStream("/muse-reader/$name"))
    private fun records(name:String):List<JsonObject> = GZIPInputStream(resource("$name.jsonl.gz")).bufferedReader().useLines{it.map{line->Json.parseToJsonElement(line).jsonObject}.toList()}
    private fun options(request:JsonObject)=(request["options"]?.let(ReaderOptions::fromJson)?:ReaderOptions()).copy(extensions=Extensions.read(request["extensions"]?.jsonArray?.map{it.jsonPrimitive.content}?:emptyList()))
    private fun sources(request:JsonObject)=request["chunks"]?.jsonArray?.let{chunks->Sources(chunks.map{raw->val c=raw.jsonArray;SourceChunk(SourcePos(c[0].jsonPrimitive.content,c[1].jsonPrimitive.long,c[2].jsonPrimitive.long),c[3].jsonPrimitive.content)})}?:Sources.fromText(request["input"]?.jsonPrimitive?.content?:"")
    private fun check(record:JsonObject){
        val request=record.getValue("request").jsonObject;val expected=record.getValue("expected").jsonObject
        val traces=mutableListOf<String>();val runtime=PandocStateContext(tracing=traces::add);runtime.setTrace(request["trace"]?.jsonPrimitive?.boolean?:false)
        if(expected["error"]!=null){
            val error=assertFailsWith<PandocError.PandocParseError>{MuseReader.read(options(request),sources(request),runtime)}
            assertEquals(expected.getValue("error").jsonPrimitive.content,error.renderError())
            assertEquals(expected.getValue("showError").jsonPrimitive.content,CharacterStringChars.build(error.show()))
        }else{
            val document=MuseReader.read(options(request),sources(request),runtime)
            if(expected["showDocument"]!=null)assertEquals(expected.getValue("showDocument").jsonPrimitive.content,AstShow.show(document))
            else assertEquals(expected.getValue("document"),Json.parseToJsonElement(PandocJson.encode(document)))
            assertEquals(expected.getValue("logs"),Json.parseToJsonElement(Logging.encodeLogMessagesText(runtime.getLog())))
        }
        record["trace"]?.let{assertEquals(it.jsonArray.map{line->line.jsonPrimitive.content},traces)}
    }
    @TestFactory fun all250OriginalAssertions():List<DynamicTest> = records("original250").map{record->DynamicTest.dynamicTest("original/"+record.getValue("name").jsonPrimitive.content){
        val actual=MuseReader.read(ReaderOptions(extensions=Extensions.read(record.getValue("extensions").jsonArray.map{it.jsonPrimitive.content})),record.getValue("input").jsonPrimitive.content)
        assertEquals(record.getValue("expected"),Json.parseToJsonElement(PandocJson.encode(actual)))
    }}
    @TestFactory fun completeOriginalModuleBranches():List<DynamicTest> = listOf("observations","extra","deep","trace").flatMap{file->records(file).map{record->DynamicTest.dynamicTest(record.getValue("request").jsonObject.getValue("name").jsonPrimitive.content){check(record)}}}
    @Test fun originalLetterPredicateAliasCoversEveryHaskellChar(){
        val letters=GZIPInputStream(resource("letter-flags.txt.gz")).use{it.readBytes()};assertEquals(1114112,letters.size)
        for(point in letters.indices)assertEquals(letters[point].toInt()==49,ParserCharacters.isAlpha(point),"U+${point.toString(16)}")
        val proof=resource("letter-alias.json").bufferedReader().readText().let(Json::parseToJsonElement).jsonObject
        assertEquals(1114112,proof.getValue("points").jsonPrimitive.int);assertEquals(0,proof.getValue("mismatches").jsonPrimitive.int)
    }
    companion object {
        /** Executes the same independent observations at -Xmx512m -Xss256k. */
        @JvmStatic fun main(args:Array<String>){val suite=MuseReaderTest();val cases=suite.all250OriginalAssertions()+suite.completeOriginalModuleBranches();for(case in cases)case.executable.execute();suite.originalLetterPredicateAliasCoversEveryHaskellChar();println("Muse reader: ${cases.size} original observations and all1,114,112 letter-alias points passed")}
    }
}
