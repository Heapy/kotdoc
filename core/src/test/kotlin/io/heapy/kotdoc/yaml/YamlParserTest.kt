package io.heapy.kotdoc.yaml

import io.heapy.kotdoc.logging.assertLogJsonEquals
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.util.zip.GZIPInputStream
import kotlin.test.*

/** Protocol projection; every expected event/state/error is original C output. */
class YamlParserTest {
    private fun mark(m: YamlMark)=buildJsonArray { add(m.index.toString());add(m.line.toString());add(m.column.toString()) }
    private fun token(t: YamlToken)=buildJsonObject {
        put("type",t.type.ordinal);put("start",mark(t.start));put("end",mark(t.end));put("fields",buildJsonArray {
            when(t.type) {
                YamlTokenType.StreamStart -> add(t.encoding.ordinal)
                YamlTokenType.VersionDirective -> { add(t.major);add(t.minor) }
                YamlTokenType.TagDirective,YamlTokenType.Tag -> { add(t.value.toHexString());add(t.suffix.toHexString()) }
                YamlTokenType.Anchor,YamlTokenType.Alias -> add(t.value.toHexString())
                YamlTokenType.Scalar -> { add(t.value.toHexString());add(t.style.ordinal) }
                else -> Unit
            }
        })
    }
    private fun event(e: YamlEvent)=buildJsonObject {
        put("type",e.type.ordinal);put("start",mark(e.start));put("end",mark(e.end));put("fields",buildJsonArray {
            fun nullable(v: ByteArray?) { add(v?.toHexString()?.let(::JsonPrimitive) ?: JsonNull) }
            when(e.type) {
                YamlEventType.StreamStart -> add(e.encoding.ordinal)
                YamlEventType.DocumentStart -> {
                    add(e.version?.let { buildJsonArray { add(it.major);add(it.minor) } } ?: JsonNull)
                    add(JsonArray(e.directives.map { buildJsonArray { add(it.handle.toHexString());add(it.prefix.toHexString()) } }))
                    add(e.implicit)
                }
                YamlEventType.DocumentEnd -> add(e.implicit)
                YamlEventType.Alias -> nullable(e.anchor)
                YamlEventType.Scalar -> { nullable(e.anchor);nullable(e.tag);add(e.value.toHexString());add(e.scalarStyle.ordinal);add(e.plainImplicit);add(e.quotedImplicit) }
                YamlEventType.SequenceStart,YamlEventType.MappingStart -> { nullable(e.anchor);nullable(e.tag);add(e.collectionStyle.ordinal);add(e.implicit) }
                else -> Unit
            }
        })
    }
    private fun run(a: JsonArray): JsonElement {
        val bytes=a[1].jsonArray.map { it.jsonPrimitive.int.toByte() }.toByteArray();val chunks=a[2].jsonArray.map { it.jsonPrimitive.int }
        var position=0;var chunkIndex=0;var calls=0
        val reader=YamlReader(YamlReadHandler { maximum ->
            calls++;val chunk=if(chunkIndex<chunks.size) chunks[chunkIndex++] else 16384
            if(chunk<0) null else { val n=minOf(chunk,maximum,bytes.size-position);bytes.copyOfRange(position,position+n).also { position+=n } }
        },YamlEncoding.entries[a[3].jsonPrimitive.int])
        val scanner=YamlScanner(reader);val parser=YamlParser(scanner)
        fun state()=buildJsonObject {
            put("state",parser.state.ordinal);put("states",JsonArray(parser.savedStates().map { JsonPrimitive(it.ordinal) }))
            put("marks",JsonArray(parser.savedMarks().map(::mark)))
            put("tags",JsonArray(parser.tagDirectives().map { buildJsonArray { add(it.handle.toHexString());add(it.prefix.toHexString()) } }))
            put("available",scanner.tokenAvailable)
            put("scanner",buildJsonObject {
                put("mark",mark(scanner.mark));put("flow",scanner.flowLevel);put("indent",scanner.indent)
                put("parsed",scanner.tokensParsed.toString());put("allowed",scanner.simpleKeyAllowed)
                put("keys",JsonArray(scanner.keyStates().map { k -> buildJsonArray { add(k.possible);add(k.required);add(k.number.toString());add(mark(k.mark)) } }))
                put("queued",JsonArray(scanner.queuedTokens().map(::token)))
                put("reader",buildJsonObject {
                    put("encoding",reader.encoding.ordinal);put("offset",reader.offset.toString());put("eof",reader.eof);put("unread",reader.unread)
                    put("buffer",reader.bufferedBytes().toHexString());put("raw",reader.remainingRawBytes().toHexString())
                })
                put("calls",calls);put("inputPosition",position);put("chunkIndex",chunkIndex)
            })
        }
        val steps=mutableListOf<JsonElement>()
        for(i in 0 until a[4].jsonPrimitive.int) {
            val result=parser.next();val e=if(result is YamlParseResult.Event) result.value else YamlEvent(YamlEventType.None,YamlMark(),YamlMark())
            steps.add(buildJsonObject { put("result",result !is YamlParseResult.Failure);put("event",event(e));put("state",state()) })
            if(result !is YamlParseResult.Event) break
        }
        val failure=parser.failure
        return buildJsonObject {
            put("steps",JsonArray(steps));put("error",buildJsonObject {
                put("type",failure?.type ?: 0);put("problem",failure?.problem?.let(::JsonPrimitive) ?: JsonNull)
                put("context",failure?.context?.let(::JsonPrimitive) ?: JsonNull)
                put("problemMark",mark(failure?.problemMark ?: YamlMark()));put("contextMark",mark(failure?.contextMark ?: YamlMark()))
                put("offset",failure?.readerFailure?.offset?.toString() ?: "0");put("value",failure?.readerFailure?.value ?: 0)
            })
        }
    }
    @TestFactory
    fun originalParserObservations(): List<DynamicTest> = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/yaml-parser/data.jsonl.gz"))).bufferedReader().use { r ->
        r.readLine();r.lineSequence().mapIndexed { i,line ->
            var transport: String?=line
            DynamicTest.dynamicTest("Parser ${i+1}") {
                val c=Json.parseToJsonElement(checkNotNull(transport)).jsonObject;transport=null
                assertLogJsonEquals(c.getValue("expected"),run(c.getValue("request").jsonArray))
            }
        }.toList()
    }
    @Test
    fun eventsOwnFieldsAndEmptyTagIsDistinctFromMissingTag() {
        val bytes=byteArrayOf(65);val directives=mutableListOf(YamlTagDirective(bytes,bytes))
        val e=YamlEvent(YamlEventType.Scalar,YamlMark(),YamlMark(),bytes,bytes,byteArrayOf(),directives=directives)
        bytes.fill(0);directives.clear();e.value.fill(0);e.anchor?.fill(0)
        assertContentEquals(byteArrayOf(65),e.value);assertContentEquals(byteArrayOf(65),e.anchor)
        assertContentEquals(byteArrayOf(),e.tag);assertEquals(1,e.directives.size)
        assertContentEquals(byteArrayOf(65),e.directives.single().handle)
        assertNull(YamlEvent(YamlEventType.Scalar,YamlMark(),YamlMark()).tag)
    }
}
