package io.heapy.kotdoc.images

import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.util.stream.Stream
import java.util.zip.GZIPInputStream
import kotlin.streams.asStream
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JpegProgressiveTest {
    @TestFactory fun `progressive scan pixels match original JuicyPixels`(): Stream<DynamicTest> {
        val source = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/images/jpeg-progressive.jsonl.gz"))).bufferedReader()
        return source.lineSequence().drop(1).map { line ->
            val name = Json.parseToJsonElement(line).jsonObject.getValue("name").jsonPrimitive.content
            DynamicTest.dynamicTest(name) {
                val c = Json.parseToJsonElement(line).jsonObject
                val expected = c.getValue("expected").jsonObject
                val error = expected["error"]?.jsonPrimitive?.content
                if (error == null) {
                    val dims = c.getValue("dimensions").ints()
                    val result = execute(c)
                    assertEquals(dims[0], result.width); assertEquals(dims[1], result.height)
                    assertEquals(c.getValue("components").jsonArray.size, result.components)
                    assertContentEquals(expected.getValue("pixels").ints().map(Int::toByte).toByteArray(), result.pixels)
                } else if (error == "divide by zero") {
                    assertFailsWith<ArithmeticException> { execute(c) }
                } else if ("index out of bounds" in error) {
                    assertFailsWith<IndexOutOfBoundsException> { execute(c) }
                } else {
                    assertEquals(error, assertFailsWith<IllegalStateException> { execute(c) }.message)
                }
            }
        }.asStream().onClose { source.close() }
    }
    private fun JsonElement.ints() = jsonArray.map { it.jsonPrimitive.int }
    private fun parameter(value: JsonElement): JpgUnpackerParameter {
        val a = value.jsonArray; val ns = a[2].jsonArray.map { it.jsonPrimitive.long }
        return JpgUnpackerParameter(a[0].ints().toIntArray(), a[1].ints().toIntArray(), ns[0], ns[1], ns[2], ns[3],
            ns[4] to ns[5], ns[6] to ns[7], ns[8] to ns[9], ns[10], ns[11], ns[12], ns[13], ns[14])
    }
    private fun execute(c: JsonObject): JpgDecodedRaster {
        val dims = c.getValue("dimensions").ints()
        val components = c.getValue("components").jsonArray.map {
            val ns = it.ints(); JpgComponent(ns[0], ns[1], ns[2], ns[3])
        }
        val frame = JpgFrameHeader(0, 8, dims[1], dims[0], components.size, components)
        val quants = c.getValue("quants").jsonArray.map { it.ints().map(Int::toShort).toShortArray() }
        val scans = c.getValue("scans").jsonArray.map {
            val a = it.jsonArray
            JpgScanData(a[0].jsonArray.map(::parameter), a[1].ints().map(Int::toByte).toByteArray())
        }
        return JpegProgressive.unpack(dims[2].toLong() to dims[3].toLong(), frame, quants, scans)
    }
}
