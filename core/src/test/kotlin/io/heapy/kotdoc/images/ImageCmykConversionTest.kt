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

class ImageCmykConversionTest {
    private fun sweep(op: String, first: Int): IntArray {
        val components = if (op.startsWith("rgb")) 3 else 4
        return IntArray(65536 * components) { at ->
            val i = at / components; val channel = at % components
            when (op) {
                "rgb8" -> when (channel) { 0 -> first; 1 -> i / 256; else -> i % 256 }
                "cmyk8" -> when (channel) { 0 -> i / 256; 1 -> 255 - i / 256; 2 -> (i / 256 * 73) % 256; else -> i % 256 }
                "rgb16" -> when (channel) { 0 -> i; 1 -> (i * 257 + first) % 65536; else -> 65535 - i }
                "cmyk16" -> when (channel) { 0 -> i; 1 -> 65535 - i; 2 -> (i * 257) % 65536; else -> first }
                else -> when (channel) { 0 -> first; 1 -> i / 256; 2 -> i % 256; else -> (i * 73 + first) % 256 }
            }
        }
    }

    private fun pixels(op: String, input: IntArray, count: Int): IntArray {
        val stride = if (op.startsWith("rgb")) 3 else 4
        val outStride = if (op.startsWith("rgb") || op == "ycck-cmyk") 4 else 3
        val out = IntArray(count * outStride)
        for (i in 0..<count) {
            val src = i * stride; val at = i * outStride
            fun put3(a: Int, b: Int, c: Int) { out[at] = a; out[at + 1] = b; out[at + 2] = c }
            fun put4(a: Int, b: Int, c: Int, d: Int) { put3(a, b, c); out[at + 3] = d }
            when (op) {
                "rgb8" -> ImageCmykConversion.toCmyk(PixelRGB8(input[src].toUByte(), input[src + 1].toUByte(), input[src + 2].toUByte())).let {
                    put4(it.cyan.toInt(), it.magenta.toInt(), it.yellow.toInt(), it.black.toInt())
                }
                "cmyk8" -> ImageCmykConversion.toRgb(PixelCMYK8(input[src].toUByte(), input[src + 1].toUByte(), input[src + 2].toUByte(), input[src + 3].toUByte())).let {
                    put3(it.red.toInt(), it.green.toInt(), it.blue.toInt())
                }
                "rgb16" -> ImageCmykConversion.toCmyk(PixelRGB16(input[src].toUShort(), input[src + 1].toUShort(), input[src + 2].toUShort())).let {
                    put4(it.cyan.toInt(), it.magenta.toInt(), it.yellow.toInt(), it.black.toInt())
                }
                "cmyk16" -> ImageCmykConversion.toRgb(PixelCMYK16(input[src].toUShort(), input[src + 1].toUShort(), input[src + 2].toUShort(), input[src + 3].toUShort())).let {
                    put3(it.red.toInt(), it.green.toInt(), it.blue.toInt())
                }
                "ycck-rgb" -> ImageCmykConversion.toRgb(PixelYCbCrK8(input[src].toUByte(), input[src + 1].toUByte(), input[src + 2].toUByte(), input[src + 3].toUByte())).let {
                    put3(it.red.toInt(), it.green.toInt(), it.blue.toInt())
                }
                "ycck-cmyk" -> ImageCmykConversion.toCmyk(PixelYCbCrK8(input[src].toUByte(), input[src + 1].toUByte(), input[src + 2].toUByte(), input[src + 3].toUByte())).let {
                    put4(it.cyan.toInt(), it.magenta.toInt(), it.yellow.toInt(), it.black.toInt())
                }
                else -> error(op)
            }
        }
        return out
    }

    private fun image(op: String, width: Int, height: Int, input: IntArray): IntArray {
        val data8 = ByteArray(input.size) { input[it].toByte() }; val before8 = data8.copyOf()
        val data16 = ShortArray(input.size) { input[it].toShort() }; val before16 = data16.copyOf()
        fun result8(w: Int, h: Int, data: ByteArray): IntArray {
            assertEquals(width, w); assertEquals(height, h); assertNotSame(data8, data)
            return IntArray(data.size) { data[it].toInt() and 255 }
        }
        fun result16(w: Int, h: Int, data: ShortArray): IntArray {
            assertEquals(width, w); assertEquals(height, h); assertNotSame(data16, data)
            return IntArray(data.size) { data[it].toInt() and 65535 }
        }
        val result = when (op) {
            "rgb8" -> ImageCmykConversion.toCmyk(ImageRGB8(width, height, data8)).let { result8(it.width, it.height, it.data) }
            "cmyk8" -> ImageCmykConversion.toRgb(ImageCMYK8(width, height, data8)).let { result8(it.width, it.height, it.data) }
            "rgb16" -> ImageCmykConversion.toCmyk(ImageRGB16(width, height, data16)).let { result16(it.width, it.height, it.data) }
            "cmyk16" -> ImageCmykConversion.toRgb(ImageCMYK16(width, height, data16)).let { result16(it.width, it.height, it.data) }
            "ycck-rgb" -> ImageCmykConversion.toRgb(ImageYCbCrK8(width, height, data8)).let { result8(it.width, it.height, it.data) }
            "ycck-cmyk" -> ImageCmykConversion.toCmyk(ImageYCbCrK8(width, height, data8)).let { result8(it.width, it.height, it.data) }
            else -> error(op)
        }
        assertContentEquals(before8, data8); assertContentEquals(before16, data16)
        return result
    }

    @TestFactory fun `six original CMYK and YCbCrK instances`(): Stream<DynamicTest> {
        val reader = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/images/image-cmyk.jsonl.gz"))).bufferedReader()
        return reader.lineSequence().drop(1).map { line ->
            val name = Json.parseToJsonElement(line).jsonObject.getValue("name").jsonPrimitive.content
            DynamicTest.dynamicTest(name) {
                val c = Json.parseToJsonElement(line).jsonObject
                fun string(key: String) = c.getValue(key).jsonPrimitive.content
                val op = string("op"); val width = c.getValue("width").jsonPrimitive.int; val height = c.getValue("height").jsonPrimitive.int
                val values = c.getValue("values").jsonArray.map { it.jsonPrimitive.int }.toIntArray()
                val input = if (string("kind") == "sweep") sweep(op, values[0]) else values
                val samples = if (string("path") == "pixel") pixels(op, input, width * height) else image(op, width, height, input)
                val bytes = if (op.endsWith("16")) ByteArray(samples.size * 2) { (samples[it / 2] ushr (8 * (it % 2))).toByte() }
                    else ByteArray(samples.size) { samples[it].toByte() }
                val expected = c.getValue("expected").jsonObject
                assertEquals(width, expected.getValue("width").jsonPrimitive.int)
                assertEquals(height, expected.getValue("height").jsonPrimitive.int)
                assertEquals(expected.getValue("length").jsonPrimitive.int, bytes.size)
                assertEquals(expected.getValue("sha256").jsonPrimitive.content, MessageDigest.getInstance("SHA-256").digest(bytes).toHexString())
                expected["hex"]?.let { assertContentEquals(it.jsonPrimitive.content.hexToByteArray(), bytes) }
            }
        }.asStream().onClose { reader.close() }
    }
}
