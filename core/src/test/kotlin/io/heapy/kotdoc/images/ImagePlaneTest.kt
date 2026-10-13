@file:Suppress("DEPRECATION")
package io.heapy.kotdoc.images

import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.security.MessageDigest
import java.util.stream.Stream
import java.util.zip.GZIPInputStream
import kotlin.streams.asStream
import kotlin.test.*

class ImagePlaneTest {
    private fun components(type: String) = when (type) {
        "ya8", "ya16" -> 2
        "rgb8", "rgb16", "rgbf", "ycbcr8" -> 3
        "rgba8", "rgba16", "cmyk8", "cmyk16", "ycbcrk8" -> 4
        else -> 1
    }
    private fun generated(type: String, kind: String, first: Int, w: Int, h: Int): IntArray {
        val stride = components(type)
        val limit = if (type.endsWith("16")) 65536 else 256
        return IntArray(w * h * stride) { at ->
            val i = at / stride; val channel = at % stride
            when (kind) {
                "ya8-upstream" -> if (channel == 0) i % 256 else ((i / 256 / 4) % 2) * 255
                "rgb-domain" -> when (channel) { 0 -> first; 1 -> i / 256; 2 -> i % 256; else -> (i * 73 + first) % 256 }
                else -> (i * (2 * channel + 1) + first + channel) % limit
            }
        }
    }

    private fun pixel(op: String, type: String, input: IntArray, count: Int): IntArray {
        val stride = components(type)
        val outStride = if (op == "drop-pixel") stride - 1 else 1
        val out = IntArray(count * outStride)
        for (i in 0..<count) {
            val src = i * stride; val at = i * outStride
            when (op to type) {
                "luma-pixel" to "y8" -> out[at] = ImageLuma.computeLuma(input[src].toUByte()).toInt()
                "luma-pixel" to "y16" -> out[at] = ImageLuma.computeLuma(input[src].toUShort()).toInt()
                "luma-pixel" to "y32" -> out[at] = ImageLuma.computeLuma(input[src].toUInt()).toInt()
                "luma-pixel" to "yf" -> out[at] = ImageLuma.computeLuma(Float.fromBits(input[src])).toRawBits()
                "luma-pixel" to "ya8" -> out[at] = ImageLuma.computeLuma(PixelYA8(input[src].toUByte(), input[src + 1].toUByte())).toInt()
                "luma-pixel" to "rgb8" -> out[at] = ImageLuma.computeLuma(PixelRGB8(input[src].toUByte(), input[src + 1].toUByte(), input[src + 2].toUByte())).toInt()
                "luma-pixel" to "rgb16" -> out[at] = ImageLuma.computeLuma(PixelRGB16(input[src].toUShort(), input[src + 1].toUShort(), input[src + 2].toUShort())).toInt()
                "luma-pixel" to "rgbf" -> out[at] = ImageLuma.computeLuma(PixelRGBF(Float.fromBits(input[src]), Float.fromBits(input[src + 1]), Float.fromBits(input[src + 2]))).toRawBits()
                "luma-pixel" to "rgba8" -> out[at] = ImageLuma.computeLuma(PixelRGBA8(input[src].toUByte(), input[src + 1].toUByte(), input[src + 2].toUByte(), input[src + 3].toUByte())).toInt()
                "luma-pixel" to "ycbcr8" -> out[at] = ImageLuma.computeLuma(PixelYCbCr8(input[src].toUByte(), input[src + 1].toUByte(), input[src + 2].toUByte())).toInt()
                "drop-pixel" to "ya8" -> {
                    val p = ImageTransparency.dropTransparency(PixelYA8(input[src].toUByte(), input[src + 1].toUByte()))
                    out[at] = p.toInt()
                }
                "alpha" to "ya8" -> {
                    val p = ImageTransparency.getTransparency(PixelYA8(input[src].toUByte(), input[src + 1].toUByte()))
                    out[at] = p.toInt()
                }
                "opacity" to "ya8" -> {
                    val p = ImageTransparency.pixelOpacity(PixelYA8(input[src].toUByte(), input[src + 1].toUByte()))
                    out[at] = p.toInt()
                }
                "drop-pixel" to "ya16" -> {
                    val p = ImageTransparency.dropTransparency(PixelYA16(input[src].toUShort(), input[src + 1].toUShort()))
                    out[at] = p.toInt()
                }
                "alpha" to "ya16" -> {
                    val p = ImageTransparency.getTransparency(PixelYA16(input[src].toUShort(), input[src + 1].toUShort()))
                    out[at] = p.toInt()
                }
                "opacity" to "ya16" -> {
                    val p = ImageTransparency.pixelOpacity(PixelYA16(input[src].toUShort(), input[src + 1].toUShort()))
                    out[at] = p.toInt()
                }
                "drop-pixel" to "rgba8" -> {
                    val p = ImageTransparency.dropTransparency(PixelRGBA8(input[src].toUByte(), input[src + 1].toUByte(), input[src + 2].toUByte(), input[src + 3].toUByte()))
                    out[at] = p.red.toInt(); out[at + 1] = p.green.toInt(); out[at + 2] = p.blue.toInt()
                }
                "alpha" to "rgba8" -> {
                    val p = ImageTransparency.getTransparency(PixelRGBA8(input[src].toUByte(), input[src + 1].toUByte(), input[src + 2].toUByte(), input[src + 3].toUByte()))
                    out[at] = p.toInt()
                }
                "opacity" to "rgba8" -> {
                    val p = ImageTransparency.pixelOpacity(PixelRGBA8(input[src].toUByte(), input[src + 1].toUByte(), input[src + 2].toUByte(), input[src + 3].toUByte()))
                    out[at] = p.toInt()
                }
                "drop-pixel" to "rgba16" -> {
                    val p = ImageTransparency.dropTransparency(PixelRGBA16(input[src].toUShort(), input[src + 1].toUShort(), input[src + 2].toUShort(), input[src + 3].toUShort()))
                    out[at] = p.red.toInt(); out[at + 1] = p.green.toInt(); out[at + 2] = p.blue.toInt()
                }
                "alpha" to "rgba16" -> {
                    val p = ImageTransparency.getTransparency(PixelRGBA16(input[src].toUShort(), input[src + 1].toUShort(), input[src + 2].toUShort(), input[src + 3].toUShort()))
                    out[at] = p.toInt()
                }
                "opacity" to "rgba16" -> {
                    val p = ImageTransparency.pixelOpacity(PixelRGBA16(input[src].toUShort(), input[src + 1].toUShort(), input[src + 2].toUShort(), input[src + 3].toUShort()))
                    out[at] = p.toInt()
                }
                else -> error("$op/$type")
            }
        }
        return out
    }

    private fun read(data: ByteArray) = IntArray(data.size) { data[it].toInt() and 255 }
    private fun read(data: ShortArray) = IntArray(data.size) { data[it].toInt() and 65535 }
    private fun read(data: IntArray) = data.copyOf()
    private fun read(data: FloatArray) = IntArray(data.size) { data[it].toRawBits() }
    private fun check(w: Int, h: Int, rw: Int, rh: Int, same: Boolean, original: Any, result: Any, originalData: Any, resultData: Any) {
        assertEquals(w, rw); assertEquals(h, rh)
        assertEquals(same, original === result)
        if (same) assertSame(originalData, resultData) else assertNotSame(originalData, resultData)
    }
    private fun image(op: String, type: String, selector: String, w: Int, h: Int, input: IntArray, shared: Boolean): IntArray {
        val bytes = ByteArray(input.size) { input[it].toByte() }; val before8 = bytes.copyOf()
        val shorts = ShortArray(input.size) { input[it].toShort() }; val before16 = shorts.copyOf()
        val words = input.copyOf(); val before32 = words.copyOf()
        val floats = FloatArray(input.size) { Float.fromBits(input[it]) }; val beforeF = read(floats)
        val result = when (op to type) {
            "unsafe" to "y8" -> {
                val original = ImageY8(w, h, bytes)
                val r = ImagePlanes.unsafeExtractComponent(selector.toLong(), original)
                check(w, h, r.width, r.height, shared, original, r, original.data, r.data)
                read(r.data)
            }
            "unsafe" to "y16" -> {
                val original = ImageY16(w, h, shorts)
                val r = ImagePlanes.unsafeExtractComponent(selector.toLong(), original)
                check(w, h, r.width, r.height, shared, original, r, original.data, r.data)
                read(r.data)
            }
            "unsafe" to "y32" -> {
                val original = ImageY32(w, h, words)
                val r = ImagePlanes.unsafeExtractComponent(selector.toLong(), original)
                check(w, h, r.width, r.height, shared, original, r, original.data, r.data)
                read(r.data)
            }
            "unsafe" to "yf" -> {
                val original = ImageYF(w, h, floats)
                val r = ImagePlanes.unsafeExtractComponent(selector.toLong(), original)
                check(w, h, r.width, r.height, shared, original, r, original.data, r.data)
                read(r.data)
            }
            "unsafe" to "ya8" -> {
                val original = ImageYA8(w, h, bytes)
                val r = ImagePlanes.unsafeExtractComponent(selector.toLong(), original)
                check(w, h, r.width, r.height, shared, original, r, original.data, r.data)
                read(r.data)
            }
            "unsafe" to "ya16" -> {
                val original = ImageYA16(w, h, shorts)
                val r = ImagePlanes.unsafeExtractComponent(selector.toLong(), original)
                check(w, h, r.width, r.height, shared, original, r, original.data, r.data)
                read(r.data)
            }
            "unsafe" to "rgb8" -> {
                val original = ImageRGB8(w, h, bytes)
                val r = ImagePlanes.unsafeExtractComponent(selector.toLong(), original)
                check(w, h, r.width, r.height, shared, original, r, original.data, r.data)
                read(r.data)
            }
            "unsafe" to "rgb16" -> {
                val original = ImageRGB16(w, h, shorts)
                val r = ImagePlanes.unsafeExtractComponent(selector.toLong(), original)
                check(w, h, r.width, r.height, shared, original, r, original.data, r.data)
                read(r.data)
            }
            "unsafe" to "rgbf" -> {
                val original = ImageRGBF(w, h, floats)
                val r = ImagePlanes.unsafeExtractComponent(selector.toLong(), original)
                check(w, h, r.width, r.height, shared, original, r, original.data, r.data)
                read(r.data)
            }
            "unsafe" to "rgba8" -> {
                val original = ImageRGBA8(w, h, bytes)
                val r = ImagePlanes.unsafeExtractComponent(selector.toLong(), original)
                check(w, h, r.width, r.height, shared, original, r, original.data, r.data)
                read(r.data)
            }
            "unsafe" to "rgba16" -> {
                val original = ImageRGBA16(w, h, shorts)
                val r = ImagePlanes.unsafeExtractComponent(selector.toLong(), original)
                check(w, h, r.width, r.height, shared, original, r, original.data, r.data)
                read(r.data)
            }
            "unsafe" to "ycbcr8" -> {
                val original = ImageYCbCr8(w, h, bytes)
                val r = ImagePlanes.unsafeExtractComponent(selector.toLong(), original)
                check(w, h, r.width, r.height, shared, original, r, original.data, r.data)
                read(r.data)
            }
            "unsafe" to "ycbcrk8" -> {
                val original = ImageYCbCrK8(w, h, bytes)
                val r = ImagePlanes.unsafeExtractComponent(selector.toLong(), original)
                check(w, h, r.width, r.height, shared, original, r, original.data, r.data)
                read(r.data)
            }
            "unsafe" to "cmyk8" -> {
                val original = ImageCMYK8(w, h, bytes)
                val r = ImagePlanes.unsafeExtractComponent(selector.toLong(), original)
                check(w, h, r.width, r.height, shared, original, r, original.data, r.data)
                read(r.data)
            }
            "unsafe" to "cmyk16" -> {
                val original = ImageCMYK16(w, h, shorts)
                val r = ImagePlanes.unsafeExtractComponent(selector.toLong(), original)
                check(w, h, r.width, r.height, shared, original, r, original.data, r.data)
                read(r.data)
            }
            "named" to "ya8" -> {
                val original = ImageYA8(w, h, bytes)
                val plane: YaPlane = when (selector) { "luma" -> PlaneLuma; "alpha" -> PlaneAlpha; else -> error(selector) }
                val r = ImagePlanes.extractComponent(plane, original)
                check(w, h, r.width, r.height, shared, original, r, original.data, r.data)
                read(r.data)
            }
            "named" to "ya16" -> {
                val original = ImageYA16(w, h, shorts)
                val plane: YaPlane = when (selector) { "luma" -> PlaneLuma; "alpha" -> PlaneAlpha; else -> error(selector) }
                val r = ImagePlanes.extractComponent(plane, original)
                check(w, h, r.width, r.height, shared, original, r, original.data, r.data)
                read(r.data)
            }
            "named" to "rgb8" -> {
                val original = ImageRGB8(w, h, bytes)
                val plane: RgbPlane = when (selector) { "red" -> PlaneRed; "green" -> PlaneGreen; "blue" -> PlaneBlue; else -> error(selector) }
                val r = ImagePlanes.extractComponent(plane, original)
                check(w, h, r.width, r.height, shared, original, r, original.data, r.data)
                read(r.data)
            }
            "named" to "rgb16" -> {
                val original = ImageRGB16(w, h, shorts)
                val plane: RgbPlane = when (selector) { "red" -> PlaneRed; "green" -> PlaneGreen; "blue" -> PlaneBlue; else -> error(selector) }
                val r = ImagePlanes.extractComponent(plane, original)
                check(w, h, r.width, r.height, shared, original, r, original.data, r.data)
                read(r.data)
            }
            "named" to "rgbf" -> {
                val original = ImageRGBF(w, h, floats)
                val plane: RgbPlane = when (selector) { "red" -> PlaneRed; "green" -> PlaneGreen; "blue" -> PlaneBlue; else -> error(selector) }
                val r = ImagePlanes.extractComponent(plane, original)
                check(w, h, r.width, r.height, shared, original, r, original.data, r.data)
                read(r.data)
            }
            "named" to "rgba8" -> {
                val original = ImageRGBA8(w, h, bytes)
                val plane: RgbaPlane = when (selector) { "red" -> PlaneRed; "green" -> PlaneGreen; "blue" -> PlaneBlue; "alpha" -> PlaneAlpha; else -> error(selector) }
                val r = ImagePlanes.extractComponent(plane, original)
                check(w, h, r.width, r.height, shared, original, r, original.data, r.data)
                read(r.data)
            }
            "named" to "rgba16" -> {
                val original = ImageRGBA16(w, h, shorts)
                val plane: RgbaPlane = when (selector) { "red" -> PlaneRed; "green" -> PlaneGreen; "blue" -> PlaneBlue; "alpha" -> PlaneAlpha; else -> error(selector) }
                val r = ImagePlanes.extractComponent(plane, original)
                check(w, h, r.width, r.height, shared, original, r, original.data, r.data)
                read(r.data)
            }
            "named" to "ycbcr8" -> {
                val original = ImageYCbCr8(w, h, bytes)
                val plane: YCbCrPlane = when (selector) { "luma" -> PlaneLuma; "cb" -> PlaneCb; "cr" -> PlaneCr; else -> error(selector) }
                val r = ImagePlanes.extractComponent(plane, original)
                check(w, h, r.width, r.height, shared, original, r, original.data, r.data)
                read(r.data)
            }
            "named" to "cmyk8" -> {
                val original = ImageCMYK8(w, h, bytes)
                val plane: CmykPlane = when (selector) { "cyan" -> PlaneCyan; "magenta" -> PlaneMagenta; "yellow" -> PlaneYellow; "black" -> PlaneBlack; else -> error(selector) }
                val r = ImagePlanes.extractComponent(plane, original)
                check(w, h, r.width, r.height, shared, original, r, original.data, r.data)
                read(r.data)
            }
            "named" to "cmyk16" -> {
                val original = ImageCMYK16(w, h, shorts)
                val plane: CmykPlane = when (selector) { "cyan" -> PlaneCyan; "magenta" -> PlaneMagenta; "yellow" -> PlaneYellow; "black" -> PlaneBlack; else -> error(selector) }
                val r = ImagePlanes.extractComponent(plane, original)
                check(w, h, r.width, r.height, shared, original, r, original.data, r.data)
                read(r.data)
            }
            "luma-image" to "y8" -> {
                val original = ImageY8(w, h, bytes)
                val r = ImageLuma.extractLumaPlane(original)
                check(w, h, r.width, r.height, shared, original, r, original.data, r.data)
                read(r.data)
            }
            "luma-image" to "y16" -> {
                val original = ImageY16(w, h, shorts)
                val r = ImageLuma.extractLumaPlane(original)
                check(w, h, r.width, r.height, shared, original, r, original.data, r.data)
                read(r.data)
            }
            "luma-image" to "y32" -> {
                val original = ImageY32(w, h, words)
                val r = ImageLuma.extractLumaPlane(original)
                check(w, h, r.width, r.height, shared, original, r, original.data, r.data)
                read(r.data)
            }
            "luma-image" to "yf" -> {
                val original = ImageYF(w, h, floats)
                val r = ImageLuma.extractLumaPlane(original)
                check(w, h, r.width, r.height, shared, original, r, original.data, r.data)
                read(r.data)
            }
            "luma-image" to "ya8" -> {
                val original = ImageYA8(w, h, bytes)
                val r = ImageLuma.extractLumaPlane(original)
                check(w, h, r.width, r.height, shared, original, r, original.data, r.data)
                read(r.data)
            }
            "luma-image" to "rgb8" -> {
                val original = ImageRGB8(w, h, bytes)
                val r = ImageLuma.extractLumaPlane(original)
                check(w, h, r.width, r.height, shared, original, r, original.data, r.data)
                read(r.data)
            }
            "luma-image" to "rgb16" -> {
                val original = ImageRGB16(w, h, shorts)
                val r = ImageLuma.extractLumaPlane(original)
                check(w, h, r.width, r.height, shared, original, r, original.data, r.data)
                read(r.data)
            }
            "luma-image" to "rgbf" -> {
                val original = ImageRGBF(w, h, floats)
                val r = ImageLuma.extractLumaPlane(original)
                check(w, h, r.width, r.height, shared, original, r, original.data, r.data)
                read(r.data)
            }
            "luma-image" to "rgba8" -> {
                val original = ImageRGBA8(w, h, bytes)
                val r = ImageLuma.extractLumaPlane(original)
                check(w, h, r.width, r.height, shared, original, r, original.data, r.data)
                read(r.data)
            }
            "luma-image" to "ycbcr8" -> {
                val original = ImageYCbCr8(w, h, bytes)
                val r = ImageLuma.extractLumaPlane(original)
                check(w, h, r.width, r.height, shared, original, r, original.data, r.data)
                read(r.data)
            }
            "drop-image" to "ya8" -> {
                val original = ImageYA8(w, h, bytes)
                val r = ImageTransparency.dropAlphaLayer(original)
                check(w, h, r.width, r.height, shared, original, r, original.data, r.data)
                read(r.data)
            }
            "drop-image" to "ya16" -> {
                val original = ImageYA16(w, h, shorts)
                val r = ImageTransparency.dropAlphaLayer(original)
                check(w, h, r.width, r.height, shared, original, r, original.data, r.data)
                read(r.data)
            }
            "drop-image" to "rgba8" -> {
                val original = ImageRGBA8(w, h, bytes)
                val r = ImageTransparency.dropAlphaLayer(original)
                check(w, h, r.width, r.height, shared, original, r, original.data, r.data)
                read(r.data)
            }
            "drop-image" to "rgba16" -> {
                val original = ImageRGBA16(w, h, shorts)
                val r = ImageTransparency.dropAlphaLayer(original)
                check(w, h, r.width, r.height, shared, original, r, original.data, r.data)
                read(r.data)
            }
            else -> error("$op/$type")
        }
        assertContentEquals(before8, bytes); assertContentEquals(before16, shorts); assertContentEquals(before32, words)
        assertContentEquals(beforeF, read(floats))
        return result
    }

    private fun serialize(samples: IntArray, bytes: Int) = ByteArray(samples.size * bytes) { (samples[it / bytes] ushr (8 * (it % bytes))).toByte() }
    private fun canonicalComputedNaNs(samples: IntArray) = IntArray(samples.size) {
        val bits = samples[it]
        if ((bits and 0x7f800000) == 0x7f800000 && (bits and 0x007fffff) != 0) 0x7fc00000 else bits
    }
    private fun hash(data: ByteArray) = MessageDigest.getInstance("SHA-256").digest(data).toHexString()

    @TestFactory fun `original color planes transparency and luma`(): Stream<DynamicTest> {
        val reader = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/images/image-plane.jsonl.gz"))).bufferedReader()
        return reader.lineSequence().drop(1).map { line ->
            val name = Json.parseToJsonElement(line).jsonObject.getValue("name").jsonPrimitive.content
            DynamicTest.dynamicTest(name) {
                val c = Json.parseToJsonElement(line).jsonObject
                fun string(key: String) = c.getValue(key).jsonPrimitive.content
                val op = string("op"); val type = string("type"); val selector = string("selector")
                val w = c.getValue("width").jsonPrimitive.int; val h = c.getValue("height").jsonPrimitive.int
                val values = c.getValue("values").jsonArray.map { it.jsonPrimitive.long.toInt() }.toIntArray()
                val input = if (string("kind") == "values") values else generated(type, string("kind"), values[0], w, h)
                val expected = c.getValue("expected").jsonObject
                fun run(): IntArray = if (op in setOf("luma-pixel", "drop-pixel", "alpha", "opacity")) pixel(op, type, input, w * h)
                    else image(op, type, selector, w, h, input, expected["shared"]?.jsonPrimitive?.boolean ?: false)
                if ("exception" in expected) {
                    val error = assertFailsWith<IllegalStateException> { run() }
                    assertEquals(expected.getValue("exception").jsonPrimitive.content, error.message)
                } else {
                    val samples = run()
                    val size = if (type.endsWith('f') || type == "y32") 4 else if (type.endsWith("16")) 2 else 1
                    assertEquals(w, expected.getValue("width").jsonPrimitive.int); assertEquals(h, expected.getValue("height").jsonPrimitive.int)
                    val data = serialize(samples, size)
                    assertEquals(expected.getValue("length").jsonPrimitive.int, data.size)
                    if ("canonicalNaNSha256" in expected) {
                        // Arithmetic NaN payload/sign are platform-dependent; all other bits are exact.
                        val canonical = serialize(canonicalComputedNaNs(samples), 4)
                        assertEquals(expected.getValue("canonicalNaNSha256").jsonPrimitive.content, hash(canonical))
                        expected["hex"]?.let {
                            val captured = it.jsonPrimitive.content.hexToByteArray()
                            val bits = IntArray(captured.size / 4) { at ->
                                (0..<4).fold(0) { value, b -> value or ((captured[at * 4 + b].toInt() and 255) shl (8 * b)) }
                            }
                            assertContentEquals(serialize(canonicalComputedNaNs(bits), 4), canonical)
                        }
                    } else {
                        assertEquals(expected.getValue("sha256").jsonPrimitive.content, hash(data))
                        expected["hex"]?.let { assertContentEquals(it.jsonPrimitive.content.hexToByteArray(), data) }
                    }
                }
            }
        }.asStream().onClose { reader.close() }
    }
}
