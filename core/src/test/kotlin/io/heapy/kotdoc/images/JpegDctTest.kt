package io.heapy.kotdoc.images

import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.util.stream.Stream
import java.util.zip.GZIPInputStream
import kotlin.streams.asStream
import kotlin.test.assertContentEquals
import kotlin.test.assertSame

class JpegDctTest {
    @TestFactory fun `Float diagnostic DCT matches original coefficients`(): Stream<DynamicTest> {
        val source = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/images/jpeg-dct.jsonl.gz"))).bufferedReader()
        return source.lineSequence().drop(1).map { line ->
            val name = Json.parseToJsonElement(line).jsonObject.getValue("name").jsonPrimitive.content
            DynamicTest.dynamicTest(name) {
                val c = Json.parseToJsonElement(line).jsonObject
                val initial = c.getValue("work").jsonArray.map { it.jsonPrimitive.int }.toIntArray()
                val values = c.getValue("values").jsonArray.map { it.jsonPrimitive.int.toShort() }.toShortArray()
                val expected = c.getValue("expected").jsonObject
                if (c.getValue("op").jsonPrimitive.content == "constants") {
                    val blocks = expected.getValue("coefficients").jsonArray
                    values.forEachIndexed { i, value ->
                        val work = initial.copyOf(); val samples = ShortArray(64) { value }
                        assertSame(work, JpegEncoder.referenceDct(work, samples))
                        assertContentEquals(blocks[i].jsonArray.map { it.jsonPrimitive.int }.toIntArray(), work, "constant level $value")
                        assertContentEquals(ShortArray(64) { value }, samples)
                    }
                } else {
                    val work = initial.copyOf()
                    assertSame(work, JpegEncoder.referenceDct(work, values))
                    assertContentEquals(expected.getValue("coefficients").jsonArray.map { it.jsonPrimitive.int }.toIntArray(), work)
                    assertContentEquals(expected.getValue("input").jsonArray.map { it.jsonPrimitive.int.toShort() }.toShortArray(), values)
                }
            }
        }.asStream().onClose { source.close() }
    }
}
