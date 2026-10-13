package io.heapy.kotdoc.images

import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.security.MessageDigest
import java.util.stream.Stream
import java.util.zip.GZIPInputStream
import kotlin.streams.asStream
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

class ImageColorConversionTest {
    @TestFactory fun `original RGB and YCbCr conversions`(): Stream<DynamicTest> {
        val reader = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/images/image-color.jsonl.gz"))).bufferedReader()
        return reader.lineSequence().drop(1).map { line ->
            val name = Json.parseToJsonElement(line).jsonObject.getValue("name").jsonPrimitive.content
            DynamicTest.dynamicTest(name) {
                val c = Json.parseToJsonElement(line).jsonObject
                fun string(key: String) = c.getValue(key).jsonPrimitive.content
                val mode = string("mode"); val rgb = string("direction") == "rgb"
                val width = c.getValue("width").jsonPrimitive.int
                val height = c.getValue("height").jsonPrimitive.int
                val values = c.getValue("values").jsonArray.map { it.jsonPrimitive.int.toByte() }.toByteArray()
                val input = if (mode.startsWith("exhaustive")) ByteArray(65536 * 3) {
                    when (it % 3) { 0 -> values[0]; 1 -> (it / 768).toByte(); else -> (it / 3).toByte() }
                } else values
                val before = input.copyOf()
                val expected = c.getValue("expected").jsonObject
                val data = if (mode.endsWith("pixel")) {
                    ByteArray(width * height * 3).also { out ->
                        for (at in out.indices step 3) {
                            val a = input[at].toUByte(); val b = input[at + 1].toUByte(); val d = input[at + 2].toUByte()
                            if (rgb) {
                                val p = ImageColorConversion.convertPixel(PixelRGB8(a, b, d))
                                out[at] = p.luma.toByte(); out[at + 1] = p.cb.toByte(); out[at + 2] = p.cr.toByte()
                            } else {
                                val p = ImageColorConversion.convertPixel(PixelYCbCr8(a, b, d))
                                out[at] = p.red.toByte(); out[at + 1] = p.green.toByte(); out[at + 2] = p.blue.toByte()
                            }
                        }
                    }
                } else if (rgb) {
                    val result = ImageColorConversion.convertImage(ImageRGB8(width, height, input))
                    assertEquals(expected.getValue("width").jsonPrimitive.int, result.width)
                    assertEquals(expected.getValue("height").jsonPrimitive.int, result.height)
                    if (mode == "jpeg") JpegEncoder.encode(JpgEncodingColor.YCbCr8, width, height, result.data, 100) else result.data
                } else {
                    val result = ImageColorConversion.convertImage(ImageYCbCr8(width, height, input))
                    assertEquals(expected.getValue("width").jsonPrimitive.int, result.width)
                    assertEquals(expected.getValue("height").jsonPrimitive.int, result.height)
                    result.data
                }
                assertNotSame(input, data)
                assertContentEquals(before, input)
                assertEquals(expected.getValue("length").jsonPrimitive.int, data.size)
                assertEquals(expected.getValue("sha256").jsonPrimitive.content, MessageDigest.getInstance("SHA-256").digest(data).toHexString())
                expected["hex"]?.let { assertContentEquals(it.jsonPrimitive.content.hexToByteArray(), data) }
            }
        }.asStream().onClose { reader.close() }
    }
}
