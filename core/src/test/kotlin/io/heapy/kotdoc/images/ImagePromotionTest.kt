package io.heapy.kotdoc.images

import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.util.stream.Stream
import java.util.zip.GZIPInputStream
import kotlin.streams.asStream
import kotlin.test.*

class ImagePromotionTest {
    @Test fun `RGBF equality matches original derived Eq`() {
        // GHC 9.12.2 evaluating the unchanged Types.hs returned
        // (False,True,True,False,False,False) for these six comparisons.
        val nanRed = PixelRGBF(Float.NaN, 1f, 2f)
        val plus = PixelRGBF(0f, 1f, 2f); val minus = PixelRGBF(-0f, 1f, 2f)
        val results = listOf(
            nanRed.equals(nanRed), plus.equals(minus),
            PixelRGBF(1f, 1f, 2f).equals(PixelRGBF(1f, 1f, 2f)),
            PixelRGBF(1f, 1f, 2f).equals(PixelRGBF(2f, 1f, 2f)),
            PixelRGBF(1f, Float.NaN, 2f).equals(PixelRGBF(1f, Float.NaN, 2f)),
            PixelRGBF(1f, 2f, Float.NaN).equals(PixelRGBF(1f, 2f, Float.NaN)),
        )
        assertEquals(listOf(false, true, true, false, false, false), results)
        assertEquals(plus.hashCode(), minus.hashCode())
    }

    private fun ramp(source: String, first: Int, count: Int): IntArray {
        val stride = when (source) { "ya8", "ya16" -> 2; "rgb8", "rgb16" -> 3; "rgba8" -> 4; else -> 1 }
        return IntArray(count * stride) { at ->
            val i = at / stride; val channel = at % stride
            when (source) {
                "y8" -> i % 256
                "y16" -> i % 65536
                "ya8" -> if (channel == 0) i / 256 else i % 256
                "ya16" -> if (channel == 0) i else (i * 257 + first) % 65536
                "rgb8" -> when (channel) { 0 -> first; 1 -> i / 256; else -> i % 256 }
                "rgb16" -> when (channel) { 0 -> i; 1 -> (i * 257 + first) % 65536; else -> 65535 - i }
                "rgba8" -> when (channel) { 0 -> i / 256; 1 -> i % 256; 2 -> 255 - i / 256; else -> (i * 73 + first) % 256 }
                else -> error(source)
            }
        }
    }

    private fun pixel(op: String, input: IntArray, count: Int): IntArray {
        val source = op.substringBefore('-'); val target = op.substringAfter('-')
        fun components(t: String) = when { t.startsWith("rgba") -> 4; t.startsWith("rgb") -> 3; t.startsWith("ya") -> 2; else -> 1 }
        val stride = components(source); val outStride = components(target)
        val out = IntArray(count * outStride)
        for (i in 0..<count) {
            val src = i * stride; val at = i * outStride
            when (op) {
                "y8-ya8" -> {
                    val p = ImagePromotion.toYa8(input[src].toUByte())
                    out[at] = p.luma.toInt(); out[at + 1] = p.alpha.toInt()
                }
                "y8-yf" -> {
                    val p = ImagePromotion.toFloat(input[src].toUByte())
                    out[at] = p.toRawBits()
                }
                "y8-y16" -> {
                    val p = ImagePromotion.to16(input[src].toUByte())
                    out[at] = p.toInt()
                }
                "y8-rgb8" -> {
                    val p = ImagePromotion.toRgb8(input[src].toUByte())
                    out[at] = p.red.toInt(); out[at + 1] = p.green.toInt(); out[at + 2] = p.blue.toInt()
                }
                "y8-rgb16" -> {
                    val p = ImagePromotion.toRgb16(input[src].toUByte())
                    out[at] = p.red.toInt(); out[at + 1] = p.green.toInt(); out[at + 2] = p.blue.toInt()
                }
                "y8-rgba8" -> {
                    val p = ImagePromotion.toRgba8(input[src].toUByte())
                    out[at] = p.red.toInt(); out[at + 1] = p.green.toInt(); out[at + 2] = p.blue.toInt(); out[at + 3] = p.alpha.toInt()
                }
                "y16-ya16" -> {
                    val p = ImagePromotion.toYa16(input[src].toUShort())
                    out[at] = p.luma.toInt(); out[at + 1] = p.alpha.toInt()
                }
                "y16-rgb16" -> {
                    val p = ImagePromotion.toRgb16(input[src].toUShort())
                    out[at] = p.red.toInt(); out[at + 1] = p.green.toInt(); out[at + 2] = p.blue.toInt()
                }
                "y16-rgba16" -> {
                    val p = ImagePromotion.toRgba16(input[src].toUShort())
                    out[at] = p.red.toInt(); out[at + 1] = p.green.toInt(); out[at + 2] = p.blue.toInt(); out[at + 3] = p.alpha.toInt()
                }
                "yf-rgbf" -> {
                    val p = ImagePromotion.toRgbFloat(Float.fromBits(input[src]))
                    out[at] = p.red.toRawBits(); out[at + 1] = p.green.toRawBits(); out[at + 2] = p.blue.toRawBits()
                }
                "ya8-rgb8" -> {
                    val p = ImagePromotion.toRgb8(PixelYA8(input[src].toUByte(), input[src + 1].toUByte()))
                    out[at] = p.red.toInt(); out[at + 1] = p.green.toInt(); out[at + 2] = p.blue.toInt()
                }
                "ya8-rgb16" -> {
                    val p = ImagePromotion.toRgb16(PixelYA8(input[src].toUByte(), input[src + 1].toUByte()))
                    out[at] = p.red.toInt(); out[at + 1] = p.green.toInt(); out[at + 2] = p.blue.toInt()
                }
                "ya8-rgba8" -> {
                    val p = ImagePromotion.toRgba8(PixelYA8(input[src].toUByte(), input[src + 1].toUByte()))
                    out[at] = p.red.toInt(); out[at + 1] = p.green.toInt(); out[at + 2] = p.blue.toInt(); out[at + 3] = p.alpha.toInt()
                }
                "ya16-rgb16" -> {
                    val p = ImagePromotion.toRgb16(PixelYA16(input[src].toUShort(), input[src + 1].toUShort()))
                    out[at] = p.red.toInt(); out[at + 1] = p.green.toInt(); out[at + 2] = p.blue.toInt()
                }
                "ya16-rgba16" -> {
                    val p = ImagePromotion.toRgba16(PixelYA16(input[src].toUShort(), input[src + 1].toUShort()))
                    out[at] = p.red.toInt(); out[at + 1] = p.green.toInt(); out[at + 2] = p.blue.toInt(); out[at + 3] = p.alpha.toInt()
                }
                "rgb16-rgba16" -> {
                    val p = ImagePromotion.toRgba16(PixelRGB16(input[src].toUShort(), input[src + 1].toUShort(), input[src + 2].toUShort()))
                    out[at] = p.red.toInt(); out[at + 1] = p.green.toInt(); out[at + 2] = p.blue.toInt(); out[at + 3] = p.alpha.toInt()
                }
                "rgb8-rgba8" -> {
                    val p = ImagePromotion.toRgba8(PixelRGB8(input[src].toUByte(), input[src + 1].toUByte(), input[src + 2].toUByte()))
                    out[at] = p.red.toInt(); out[at + 1] = p.green.toInt(); out[at + 2] = p.blue.toInt(); out[at + 3] = p.alpha.toInt()
                }
                "rgb8-rgbf" -> {
                    val p = ImagePromotion.toRgbFloat(PixelRGB8(input[src].toUByte(), input[src + 1].toUByte(), input[src + 2].toUByte()))
                    out[at] = p.red.toRawBits(); out[at + 1] = p.green.toRawBits(); out[at + 2] = p.blue.toRawBits()
                }
                "rgb8-rgb16" -> {
                    val p = ImagePromotion.toRgb16(PixelRGB8(input[src].toUByte(), input[src + 1].toUByte(), input[src + 2].toUByte()))
                    out[at] = p.red.toInt(); out[at + 1] = p.green.toInt(); out[at + 2] = p.blue.toInt()
                }
                "rgb8-rgba16" -> {
                    val p = ImagePromotion.toRgba16(PixelRGB8(input[src].toUByte(), input[src + 1].toUByte(), input[src + 2].toUByte()))
                    out[at] = p.red.toInt(); out[at + 1] = p.green.toInt(); out[at + 2] = p.blue.toInt(); out[at + 3] = p.alpha.toInt()
                }
                "rgba8-rgba16" -> {
                    val p = ImagePromotion.toRgba16(PixelRGBA8(input[src].toUByte(), input[src + 1].toUByte(), input[src + 2].toUByte(), input[src + 3].toUByte()))
                    out[at] = p.red.toInt(); out[at + 1] = p.green.toInt(); out[at + 2] = p.blue.toInt(); out[at + 3] = p.alpha.toInt()
                }
                else -> error(op)
            }
        }
        return out
    }

    private fun dimensions(w: Int, h: Int, actualW: Int, actualH: Int) { assertEquals(w, actualW); assertEquals(h, actualH) }
    private fun samples(w: Int, h: Int, rw: Int, rh: Int, data: ByteArray, original: Any): IntArray {
        dimensions(w, h, rw, rh); assertNotSame(original, data)
        return IntArray(data.size) { data[it].toInt() and 255 }
    }
    private fun samples(w: Int, h: Int, rw: Int, rh: Int, data: ShortArray, original: Any): IntArray {
        dimensions(w, h, rw, rh); assertNotSame(original, data)
        return IntArray(data.size) { data[it].toInt() and 65535 }
    }
    private fun samples(w: Int, h: Int, rw: Int, rh: Int, data: FloatArray, original: Any): IntArray {
        dimensions(w, h, rw, rh); assertNotSame(original, data)
        return IntArray(data.size) { data[it].toRawBits() }
    }
    private fun image(op: String, w: Int, h: Int, input: IntArray): IntArray {
        val bytes = ByteArray(input.size) { input[it].toByte() }; val before8 = bytes.copyOf()
        val shorts = ShortArray(input.size) { input[it].toShort() }; val before16 = shorts.copyOf()
        val floats = FloatArray(input.size) { Float.fromBits(input[it]) }; val beforeF = IntArray(floats.size) { floats[it].toRawBits() }
        val result = when (op) {
            "y8-ya8" -> ImagePromotion.toYa8(ImageY8(w, h, bytes)).let { samples(w, h, it.width, it.height, it.data, bytes) }
            "y8-yf" -> ImagePromotion.toFloat(ImageY8(w, h, bytes)).let { samples(w, h, it.width, it.height, it.data, bytes) }
            "y8-y16" -> ImagePromotion.to16(ImageY8(w, h, bytes)).let { samples(w, h, it.width, it.height, it.data, bytes) }
            "y8-rgb8" -> ImagePromotion.toRgb8(ImageY8(w, h, bytes)).let { samples(w, h, it.width, it.height, it.data, bytes) }
            "y8-rgb16" -> ImagePromotion.toRgb16(ImageY8(w, h, bytes)).let { samples(w, h, it.width, it.height, it.data, bytes) }
            "y8-rgba8" -> ImagePromotion.toRgba8(ImageY8(w, h, bytes)).let { samples(w, h, it.width, it.height, it.data, bytes) }
            "y16-ya16" -> ImagePromotion.toYa16(ImageY16(w, h, shorts)).let { samples(w, h, it.width, it.height, it.data, shorts) }
            "y16-rgb16" -> ImagePromotion.toRgb16(ImageY16(w, h, shorts)).let { samples(w, h, it.width, it.height, it.data, shorts) }
            "y16-rgba16" -> ImagePromotion.toRgba16(ImageY16(w, h, shorts)).let { samples(w, h, it.width, it.height, it.data, shorts) }
            "yf-rgbf" -> ImagePromotion.toRgbFloat(ImageYF(w, h, floats)).let { samples(w, h, it.width, it.height, it.data, floats) }
            "ya8-rgb8" -> ImagePromotion.toRgb8(ImageYA8(w, h, bytes)).let { samples(w, h, it.width, it.height, it.data, bytes) }
            "ya8-rgb16" -> ImagePromotion.toRgb16(ImageYA8(w, h, bytes)).let { samples(w, h, it.width, it.height, it.data, bytes) }
            "ya8-rgba8" -> ImagePromotion.toRgba8(ImageYA8(w, h, bytes)).let { samples(w, h, it.width, it.height, it.data, bytes) }
            "ya16-rgb16" -> ImagePromotion.toRgb16(ImageYA16(w, h, shorts)).let { samples(w, h, it.width, it.height, it.data, shorts) }
            "ya16-rgba16" -> ImagePromotion.toRgba16(ImageYA16(w, h, shorts)).let { samples(w, h, it.width, it.height, it.data, shorts) }
            "rgb16-rgba16" -> ImagePromotion.toRgba16(ImageRGB16(w, h, shorts)).let { samples(w, h, it.width, it.height, it.data, shorts) }
            "rgb8-rgba8" -> ImagePromotion.toRgba8(ImageRGB8(w, h, bytes)).let { samples(w, h, it.width, it.height, it.data, bytes) }
            "rgb8-rgbf" -> ImagePromotion.toRgbFloat(ImageRGB8(w, h, bytes)).let { samples(w, h, it.width, it.height, it.data, bytes) }
            "rgb8-rgb16" -> ImagePromotion.toRgb16(ImageRGB8(w, h, bytes)).let { samples(w, h, it.width, it.height, it.data, bytes) }
            "rgb8-rgba16" -> ImagePromotion.toRgba16(ImageRGB8(w, h, bytes)).let { samples(w, h, it.width, it.height, it.data, bytes) }
            "rgba8-rgba16" -> ImagePromotion.toRgba16(ImageRGBA8(w, h, bytes)).let { samples(w, h, it.width, it.height, it.data, bytes) }
            else -> error(op)
        }
        assertContentEquals(before8, bytes); assertContentEquals(before16, shorts)
        assertContentEquals(beforeF, IntArray(floats.size) { floats[it].toRawBits() })
        return result
    }

    private fun <T> identity(path: String, value: T): T = if (path.startsWith("promote")) ImagePromotion.promoteIdentity(value) else ImagePromotion.convertIdentity(value)
    private fun identity(op: String, path: String, w: Int, h: Int, input: IntArray): IntArray {
        when (op) {
            "identity8" -> {
                val original = ImageRGB8(w, h, ByteArray(input.size) { input[it].toByte() })
                val result = identity(path, original); assertSame(original, result); assertSame(original.data, result.data)
                dimensions(w, h, result.width, result.height)
                return IntArray(result.data.size) { result.data[it].toInt() and 255 }
            }
            "identity16" -> {
                val original = ImageRGB16(w, h, ShortArray(input.size) { input[it].toShort() })
                val result = identity(path, original); assertSame(original, result); assertSame(original.data, result.data)
                dimensions(w, h, result.width, result.height)
                return IntArray(result.data.size) { result.data[it].toInt() and 65535 }
            }
            "identityf" -> {
                val original = ImageRGBF(w, h, FloatArray(input.size) { Float.fromBits(input[it]) })
                val result = identity(path, original); assertSame(original, result); assertSame(original.data, result.data)
                dimensions(w, h, result.width, result.height)
                return IntArray(result.data.size) { result.data[it].toRawBits() }
            }
            "identity-pixel" -> {
                val original = PixelRGB8(input[0].toUByte(), input[1].toUByte(), input[2].toUByte())
                val result = identity(path, original); assertSame(original, result)
                return intArrayOf(result.red.toInt(), result.green.toInt(), result.blue.toInt())
            }
            else -> error(op)
        }
    }

    @TestFactory fun `all original image promotions and identity rules`(): Stream<DynamicTest> {
        val reader = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/images/image-promotion.jsonl.gz"))).bufferedReader()
        return reader.lineSequence().drop(1).map { line ->
            val name = Json.parseToJsonElement(line).jsonObject.getValue("name").jsonPrimitive.content
            DynamicTest.dynamicTest(name) {
                val c = Json.parseToJsonElement(line).jsonObject
                fun string(key: String) = c.getValue(key).jsonPrimitive.content
                val op = string("op"); val path = string("path")
                val width = c.getValue("width").jsonPrimitive.int; val height = c.getValue("height").jsonPrimitive.int
                val values = c.getValue("values").jsonArray.map { it.jsonPrimitive.long.toInt() }.toIntArray()
                val input = if (string("kind") == "ramp") ramp(op.substringBefore('-'), values[0], width * height) else values
                val expected = c.getValue("expected").jsonObject
                val samples = if (string("kind") == "identity") {
                    assertTrue(expected.getValue("shared").jsonPrimitive.boolean)
                    identity(op, path, width, height, input)
                } else if (path == "pixel") pixel(op, input, width * height) else image(op, width, height, input)
                val target = if (op == "identityf") "rgbf" else if (op == "identity16") "rgb16" else op.substringAfter('-')
                val componentBytes = when { target.endsWith('f') -> 4; target.endsWith("16") -> 2; else -> 1 }
                val bytes = ByteArray(samples.size * componentBytes) { (samples[it / componentBytes] ushr (8 * (it % componentBytes))).toByte() }
                assertEquals(width, expected.getValue("width").jsonPrimitive.int); assertEquals(height, expected.getValue("height").jsonPrimitive.int)
                assertEquals(expected.getValue("length").jsonPrimitive.int, bytes.size)
                assertEquals(expected.getValue("sha256").jsonPrimitive.content, MessageDigest.getInstance("SHA-256").digest(bytes).toHexString())
                expected["hex"]?.let { assertContentEquals(it.jsonPrimitive.content.hexToByteArray(), bytes) }
            }
        }.asStream().onClose { reader.close() }
    }
}
