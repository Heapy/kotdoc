package io.heapy.kotdoc.yaml

import io.heapy.kotdoc.logging.assertLogJsonEquals
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.util.zip.GZIPInputStream
import kotlin.test.*

/** Input/state transport only. Every token, queue and error field is native. */
class YamlScannerTest {
    private fun mark(m: YamlMark) = buildJsonArray { add(m.index.toString());add(m.line.toString());add(m.column.toString()) }
    private fun token(t: YamlToken): JsonElement = buildJsonObject {
        put("type",t.type.ordinal);put("start",mark(t.start));put("end",mark(t.end))
        put("fields",buildJsonArray {
            when(t.type) {
                YamlTokenType.StreamStart -> add(t.encoding.ordinal)
                YamlTokenType.VersionDirective -> { add(t.major);add(t.minor) }
                YamlTokenType.TagDirective, YamlTokenType.Tag -> { add(t.value.toHexString());add(t.suffix.toHexString()) }
                YamlTokenType.Anchor, YamlTokenType.Alias -> add(t.value.toHexString())
                YamlTokenType.Scalar -> { add(t.value.toHexString());add(t.style.ordinal) }
                else -> Unit
            }
        })
    }
    private fun run(a: JsonArray): JsonElement {
        val data=a[1].jsonArray.map { it.jsonPrimitive.int.toByte() }.toByteArray()
        val chunks=a[2].jsonArray.map { it.jsonPrimitive.int }
        var position=0;var chunkIndex=0;var calls=0
        val reader=YamlReader(YamlReadHandler { maximum ->
            calls++
            val chunk=if(chunkIndex<chunks.size) chunks[chunkIndex++] else 16384
            if(chunk<0) null else {
                val n=minOf(chunk,maximum,data.size-position)
                data.copyOfRange(position,position+n).also { position+=n }
            }
        },YamlEncoding.entries[a[3].jsonPrimitive.int])
        val scanner=YamlScanner(reader)
        fun state() = buildJsonObject {
            put("mark",mark(scanner.mark));put("flow",scanner.flowLevel);put("indent",scanner.indent)
            put("parsed",scanner.tokensParsed.toString());put("allowed",scanner.simpleKeyAllowed)
            put("keys",JsonArray(scanner.keyStates().map { k -> buildJsonArray {
                add(k.possible);add(k.required);add(k.number.toString());add(mark(k.mark))
            } }))
            put("queued",JsonArray(scanner.queuedTokens().map(::token)))
            put("reader",buildJsonObject {
                put("encoding",reader.encoding.ordinal);put("offset",reader.offset.toString());put("eof",reader.eof)
                put("unread",reader.unread);put("buffer",reader.bufferedBytes().toHexString());put("raw",reader.remainingRawBytes().toHexString())
            })
            put("calls",calls);put("inputPosition",position);put("chunkIndex",chunkIndex)
        }
        val steps=mutableListOf<JsonElement>()
        for(i in 0 until a[4].jsonPrimitive.int) {
            val result=scanner.next()
            val t=if(result is YamlScanResult.Token) result.value else YamlToken(YamlTokenType.None,YamlMark(),YamlMark())
            steps.add(buildJsonObject { put("result",result !is YamlScanResult.Failure);put("token",token(t));put("state",state()) })
            if(result !is YamlScanResult.Token) break
        }
        val failure=scanner.failure
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
    fun originalScannerObservations(): List<DynamicTest> = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/yaml-scanner/data.jsonl.gz"))).bufferedReader().use { r ->
        r.readLine()
        r.lineSequence().mapIndexed { i,line ->
            var transport: String?=line
            DynamicTest.dynamicTest("Scanner ${i+1}") {
                val c=Json.parseToJsonElement(checkNotNull(transport)).jsonObject;transport=null
                assertLogJsonEquals(c.getValue("expected"),run(c.getValue("request").jsonArray))
            }
        }.toList()
    }
    @Test
    fun tokensOwnBytesAndTerminalCallsDoNotReadAgain() {
        var calls=0
        val input="key: \"value\"".toByteArray()
        var delivered=false
        val scanner=YamlScanner(YamlReader(YamlReadHandler { calls++;if(delivered) byteArrayOf() else { delivered=true;input } }))
        val tokens=mutableListOf<YamlToken>()
        while(true) { val result=scanner.next();if(result !is YamlScanResult.Token) break;tokens.add(result.value) }
        val scalar=tokens.first { it.type==YamlTokenType.Scalar }
        val bytes=scalar.value;bytes.fill(0)
        assertEquals("key",scalar.value.toString(Charsets.UTF_8))
        val before=calls
        repeat(10) { assertSame(YamlScanResult.End,scanner.next()) }
        assertEquals(before,calls)
    }
}
