package io.heapy.kotdoc.images

import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.util.stream.Stream
import java.util.zip.GZIPInputStream
import kotlin.streams.asStream
import kotlin.test.*

class JpegBinaryTest {
    @TestFactory fun `JPEG structures and ECS match original binary instances`(): Stream<DynamicTest> {
        val source = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/images/jpeg-binary.jsonl.gz"))).bufferedReader()
        return source.lineSequence().drop(1).map { line ->
            val name = Json.parseToJsonElement(line).jsonObject.getValue("name").jsonPrimitive.content
            DynamicTest.dynamicTest(name) {
                val c = Json.parseToJsonElement(line).jsonObject
                val expected = c.getValue("expected").jsonObject
                if ("exception" in expected) {
                    assertTrue(expected.getValue("exception").jsonPrimitive.content.contains("index out of bounds"))
                    assertFailsWith<IndexOutOfBoundsException> { execute(c) }
                } else assertEquals(expected, execute(c))
            }
        }.asStream().onClose { source.close() }
    }

    private fun JsonObjectBuilder.bytes(key: String, bytes: ByteArray) {
        put(key, JsonArray(bytes.map { JsonPrimitive(it.toInt() and 255) }))
    }
    private fun describe(value: Any): JsonObject = buildJsonObject {
        val output = JpegOutput()
        val size = when (value) {
            is JpgFrameKind -> { JpegBinary.put(output, value); -1 }
            is JFifUnit -> { output.u8(value.ordinal); 1 }
            is AdobeTransform -> { output.u8(value.ordinal); 1 }
            is JpgJFIFApp0 -> { JpegBinary.put(output, value); 14 }
            is JpgAdobeApp14 -> { JpegBinary.put(output, value); 12 }
            is JpgComponent -> { JpegBinary.put(output, value); value.size }
            is JpgFrameHeader -> { JpegBinary.put(output, value); value.size }
            is JpgScanSpecification -> { JpegBinary.put(output, value); value.size }
            is JpgScanHeader -> { JpegBinary.put(output, value); value.size }
            is JpgQuantTableSpec -> { JpegBinary.put(output, value); value.size }
            is JpgHuffmanTableSpec -> { JpegBinary.put(output, value); value.size }
            is Int -> { JpegBinary.putRestart(output, value); 4 }
            else -> error("Unexpected value $value")
        }
        put("show", value.toString()); put("size", size); bytes("encoded", output.result())
    }
    private fun execute(c: JsonObject): JsonObject {
        val op = c.getValue("op").jsonPrimitive.content
        val longs = c.getValue("values").jsonArray.map { it.jsonPrimitive.long }
        val ns = longs.map(Long::toInt)
        val rows = c.getValue("rows").jsonArray.map { row -> row.jsonArray.map { it.jsonPrimitive.int } }
        fun component(row: List<Int>) = JpgComponent(row[0], row[1], row[2], row[3])
        fun spec(row: List<Int>) = JpgScanSpecification(row[0], row[1], row[2])
        if (op.startsWith("write")) return describe(when (op) {
            "writeComponent" -> component(ns)
            "writeSpec" -> spec(ns)
            "writeFrame" -> JpgFrameHeader(ns[0], ns[1], ns[2], ns[3], ns[4], rows.map(::component))
            "writeScan" -> JpgScanHeader(ns[0], ns[1], rows.map(::spec), ns[2], ns[3], ns[4], ns[5])
            "writeQuant" -> JpgQuantTableSpec(ns[0], ns[1], rows.flatten().map(Int::toShort))
            "writeHuff" -> JpgHuffmanTableSpec(if (ns[0] == 0) DctComponent.DC else DctComponent.AC, ns[1], rows[0], rows.drop(1))
            "writeJfif" -> JpgJFIFApp0(JFifUnit.entries[ns[0]], ns[1], ns[2], if (ns[3] == 0) null else longs[4])
            "writeAdobe" -> JpgAdobeApp14(ns[0], ns[1], ns[2], AdobeTransform.entries[ns[3]])
            "writeApp" -> JpgFrameKind.App(ns[0])
            "writeExtension" -> JpgFrameKind.Extension(ns[0])
            "writeRestartEnd" -> JpgFrameKind.RestartEnd(ns[0])
            else -> error(op)
        })
        val input = JpegInput((if (op.startsWith("ecs")) rows.flatten() else ns).map(Int::toByte).toByteArray())
        return try {
            if (op == "ecs") {
                val result = JpegEcs.parseChunks(rows.map { it.map(Int::toByte).toByteArray() })
                if (rows.count { it.isNotEmpty() } <= 1) {
                    assertContentEquals(result.bytes, JpegEcs.parse(input))
                    assertEquals(result.consumed, input.position)
                }
                buildJsonObject { put("offset", result.consumed); bytes("rest", result.remaining); bytes("bytes", result.bytes) }
            } else {
                val description = when (op) {
                    "kind" -> describe(JpegBinary.kind(input))
                    "unit" -> describe(JpegBinary.unit(input))
                    "transform" -> describe(JpegBinary.adobeTransform(input))
                    "jfif" -> describe(JpegBinary.jfif(input))
                    "adobe" -> describe(JpegBinary.adobe(input))
                    "component" -> describe(JpegBinary.component(input))
                    "frame" -> describe(JpegBinary.frameHeader(input))
                    "scanSpec" -> describe(JpegBinary.scanSpecification(input))
                    "scan" -> describe(JpegBinary.scanHeader(input))
                    "quant" -> describe(JpegBinary.quantTable(input))
                    "huff" -> describe(JpegBinary.huffmanTable(input))
                    "restart" -> describe(JpegBinary.restart(input))
                    "quantList" -> {
                        val tables = JpegBinary.tableList(input, JpegBinary::quantTable)
                        val output = JpegOutput()
                        JpegBinary.putTableList(output, tables, { it.size }, JpegBinary::put)
                        buildJsonObject { put("show", tables.joinToString(",", "[", "]")); bytes("encoded", output.result()); put("size", 2 + tables.sumOf { it.size }) }
                    }
                    "huffList" -> {
                        val tables = JpegBinary.tableList(input, JpegBinary::huffmanTable)
                        val output = JpegOutput()
                        JpegBinary.putTableList(output, tables, { it.size }, JpegBinary::put)
                        buildJsonObject { put("show", tables.joinToString(",", "[", "]")); bytes("encoded", output.result()); put("size", 2 + tables.sumOf { it.size }) }
                    }
                    "skipUntil" -> { JpegBinary.skipUntilFrames(input); buildJsonObject {} }
                    "skipMarker" -> { JpegBinary.skipFrameMarker(input); buildJsonObject {} }
                    "ecsSimple" -> { val bytes = JpegEcs.simple(input); buildJsonObject { bytes("bytes", bytes) } }
                    else -> error(op)
                }
                buildJsonObject {
                    put("offset", input.position); bytes("rest", input.bytes.copyOfRange(input.position, input.bytes.size))
                    description.forEach { (key, value) -> put(key, value) }
                }
            }
        } catch (e: JpegBinaryException) {
            buildJsonObject { put("error", e.message); put("offset", e.offset); bytes("rest", input.bytes.copyOfRange(e.offset, input.bytes.size)) }
        }
    }
}
