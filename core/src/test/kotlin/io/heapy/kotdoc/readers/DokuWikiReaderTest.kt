package io.heapy.kotdoc.readers

import io.heapy.kotdoc.ast.*
import io.heapy.kotdoc.errors.PandocError
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

/** Expectations are original upstream literals or complete original Haskell observations. */
class DokuWikiReaderTest {
    private fun resource(name: String) = checkNotNull(javaClass.getResourceAsStream("/dokuwiki-reader/$name"))
    private fun records(name: String): List<JsonObject> = GZIPInputStream(resource("$name.jsonl.gz")).bufferedReader().useLines { it.map { line -> Json.parseToJsonElement(line).jsonObject }.toList() }
    private fun options(request: JsonObject): ReaderOptions = (request["options"]?.let(ReaderOptions::fromJson) ?: ReaderOptions()).copy(
        extensions=Extensions.read(request["extensions"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()))
    private fun sources(request: JsonObject): Sources = request["chunks"]?.jsonArray?.let { chunks -> Sources(chunks.map { raw ->
        val c=raw.jsonArray;SourceChunk(SourcePos(c[0].jsonPrimitive.content,c[1].jsonPrimitive.long,c[2].jsonPrimitive.long),c[3].jsonPrimitive.content)
    }) } ?: Sources.fromText(request["input"]?.jsonPrimitive?.content ?: "")
    private fun check(record: JsonObject) {
        val request=record.getValue("request").jsonObject;val expected=record.getValue("expected").jsonObject
        val traces=mutableListOf<String>();val runtime=PandocStateContext(tracing=traces::add);runtime.setTrace(request["trace"]?.jsonPrimitive?.boolean ?: false)
        if(expected["error"]!=null) {
            val error=assertFailsWith<PandocError.PandocParseError> { DokuWikiReader.read(options(request),sources(request),runtime) }
            assertEquals(expected.getValue("error").jsonPrimitive.content,error.renderError())
        } else {
            val document=DokuWikiReader.read(options(request),sources(request),runtime)
            if(expected["showDocument"]!=null)assertEquals(expected.getValue("showDocument").jsonPrimitive.content,AstShow.show(document))
            else assertEquals(expected.getValue("document"),Json.parseToJsonElement(PandocJson.encode(document)))
            assertEquals(expected.getValue("logs"),Json.parseToJsonElement(Logging.encodeLogMessagesText(runtime.getLog())))
        }
        record["trace"]?.let { assertEquals(it.jsonArray.map { value -> value.jsonPrimitive.content },traces) }
    }
    @TestFactory fun all115OriginalAssertions(): List<DynamicTest> = records("original115").map { r ->
        val name=r.getValue("name").jsonPrimitive.content
        DynamicTest.dynamicTest("original/$name") {
            val math=name.startsWith("math groups and comments/")
            val extensions=if(!math)Extensions.Empty else Extensions.read(listOf(when(name.split('/')[1]) {
                "dollars","double dollars"->"tex_math_dollars";"parentheses","brackets"->"tex_math_single_backslash";else->"tex_math_double_backslash"
            }))
            val actual=DokuWikiReader.read(ReaderOptions(standalone=!math,extensions=extensions),r.getValue("input").jsonPrimitive.content)
            assertEquals(r.getValue("expected"),Json.parseToJsonElement(PandocJson.encode(actual)))
        }
    }
    @TestFactory fun originalModuleBranches(): List<DynamicTest> = listOf("observations","deep","trace","unicode17").flatMap { file -> records(file).map { record ->
        DynamicTest.dynamicTest(record.getValue("request").jsonObject.getValue("name").jsonPrimitive.content) { check(record) }
    } }
    @Test fun completePinnedIdentifierPredicateDomain() {
        val expected=GZIPInputStream(resource("identifier-flags-expanded.txt.gz")).use { it.readBytes() }
        assertEquals(0x110000,expected.size)
        for(point in expected.indices)assertEquals(expected[point].toInt()-65,DokuWikiIdentifiers.flags(point),"U+${point.toString(16)}")
        val ranges=resource("unicode17-ranges.tsv").bufferedReader().useLines { lines -> lines.map { it.split('\t').map(String::toInt) }.toList() }
        assertEquals(47,ranges.size);assertEquals(4803,ranges.sumOf { it[1]-it[0]+1 })
        for((start,end) in ranges)for(point in start..end)assertEquals(0,DokuWikiIdentifiers.flags(point),"Unicode17 U+${point.toString(16)}")
    }
    companion object {
        /** Also runnable with -Xmx512m -Xss256k and the ordinary test classpath. */
        @JvmStatic fun main(args: Array<String>) {
            val suite=DokuWikiReaderTest();val tests=suite.all115OriginalAssertions()+suite.originalModuleBranches()
            for(test in tests)test.executable.execute()
            suite.completePinnedIdentifierPredicateDomain()
            println("DokuWiki reader: ${tests.size} original observations and all1,114,112classifier points passed")
        }
    }
}
