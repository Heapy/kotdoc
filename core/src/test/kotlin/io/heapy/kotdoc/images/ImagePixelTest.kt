package io.heapy.kotdoc.images

import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.security.MessageDigest
import java.util.stream.Stream
import java.util.zip.GZIPInputStream
import kotlin.streams.asStream
import kotlin.test.*

class ImagePixelTest {
    private data class Observation(val data: MutableMap<String, ByteArray> = mutableMapOf(), val values: MutableMap<String, Any> = mutableMapOf())
    private fun bytes(values: IntArray, size: Int) = ByteArray(values.size * size) { (values[it / size] ushr (8 * (it % size))).toByte() }
    private fun packed(data: ByteArray) = data.copyOf()
    private fun packed(data: ShortArray) = bytes(IntArray(data.size) { data[it].toInt() and 65535 }, 2)
    private fun packed(data: IntArray) = bytes(data, 4)
    private fun packed(data: FloatArray) = bytes(IntArray(data.size) { data[it].toRawBits() }, 4)
    private fun canonical(data: ByteArray): ByteArray {
        val values = IntArray(data.size / 4) { at ->
            (0..<4).fold(0) { n, b -> n or ((data[at * 4 + b].toInt() and 255) shl (8 * b)) }
        }
        for (i in values.indices) if ((values[i] and 0x7f800000) == 0x7f800000 && (values[i] and 0x007fffff) != 0) values[i] = 0x7fc00000
        return bytes(values, 4)
    }
    private fun hash(data: ByteArray) = MessageDigest.getInstance("SHA-256").digest(data).toHexString()
    private fun count(type: String) = when (type) {
        "ya8", "ya16" -> 2
        "rgb8", "rgb16", "rgbf", "ycbcr8" -> 3
        "rgba8", "rgba16", "cmyk8", "cmyk16", "ycbcrk8" -> 4
        else -> 1
    }
    private val floatBits = (0..1).flatMap { sign -> (0..255).flatMap { exp -> listOf(0, 1, 0x3fffff, 0x400000, 0x7ffffe, 0x7fffff).map { (sign shl 31) or (exp shl 23) or it } } }
    private val wordBits = (0..32).flatMap { n -> listOf((1L shl n) - 1, 1L shl n, (1L shl n) + 1) }.filter { it in 0..0xffffffffL }.distinct().sorted()
    private fun generated(type: String, kind: String): Pair<IntArray, IntArray> {
        val stride = count(type)
        val n = when (kind) { "pairs8", "range16" -> 65536; "range8" -> 256; "float-bits" -> floatBits.size; else -> wordBits.size }
        val left = IntArray(n * stride); val right = IntArray(left.size)
        for (i in 0..<n) for (c in 0..<stride) {
            val (a, b) = when (kind) {
                "pairs8" -> ((i / 256 + c * 17) % 256) to ((i % 256 + c * 73) % 256)
                "range8" -> ((i + c * 17) % 256) to ((255 - i + c * 73) % 256)
                "range16" -> ((i * (2 * c + 1) + c * 257) and 65535) to ((65535 - i * (2 * c + 1) + c * 73) and 65535)
                "float-bits" -> floatBits[(i + c * 257) % n] to floatBits[(n - 1 - i + c * 131) % n]
                else -> wordBits[i].toInt() to (0xffffffffL - wordBits[i]).toInt()
            }
            left[i * stride + c] = a; right[i * stride + c] = b
        }
        return left to right
    }
    private fun binary8(mode: String, i: Int, a: UByte, b: UByte): UByte = when (mode) {
        "left" -> a; "right" -> b
        "sum" -> (a.toInt() + b.toInt()).toUByte()
        "difference" -> (a.toInt() - b.toInt()).toUByte()
        "weighted" -> ((i + 1) * 17 + a.toInt() * 3 - b.toInt() * 5).toUByte()
        "index" -> (i + 123).toUByte()
        "fail1" -> if (i == 1) error("color callback channel 1") else a
        else -> error("color callback failure")
    }
    private fun alpha8(mode: String, a: UByte, b: UByte): UByte = when (mode) {
        "left" -> a; "right" -> b; "difference" -> (a.toInt() - b.toInt()).toUByte(); "tagged" -> (219).toUByte()
        else -> error("alpha callback forced")
    }
    private fun unary8(mode: String, a: UByte): UByte = when (mode) {
        "identity" -> a; "negate" -> (-a.toInt()).toUByte(); "offset" -> (a.toInt() + 37).toUByte(); "scale" -> (a.toInt() * 3 - 11).toUByte()
        else -> error("map callback failure")
    }
    private fun binary16(mode: String, i: Int, a: UShort, b: UShort): UShort = when (mode) {
        "left" -> a; "right" -> b
        "sum" -> (a.toInt() + b.toInt()).toUShort()
        "difference" -> (a.toInt() - b.toInt()).toUShort()
        "weighted" -> ((i + 1) * 17 + a.toInt() * 3 - b.toInt() * 5).toUShort()
        "index" -> (i + 123).toUShort()
        "fail1" -> if (i == 1) error("color callback channel 1") else a
        else -> error("color callback failure")
    }
    private fun alpha16(mode: String, a: UShort, b: UShort): UShort = when (mode) {
        "left" -> a; "right" -> b; "difference" -> (a.toInt() - b.toInt()).toUShort(); "tagged" -> (219).toUShort()
        else -> error("alpha callback forced")
    }
    private fun unary16(mode: String, a: UShort): UShort = when (mode) {
        "identity" -> a; "negate" -> (-a.toInt()).toUShort(); "offset" -> (a.toInt() + 37).toUShort(); "scale" -> (a.toInt() * 3 - 11).toUShort()
        else -> error("map callback failure")
    }
    private fun binary32(mode: String, i: Int, a: UInt, b: UInt): UInt = when (mode) {
        "left" -> a; "right" -> b
        "sum" -> a + b
        "difference" -> a - b
        "weighted" -> (i + 1).toUInt() * 17u + a * 3u - b * 5u
        "index" -> (i + 123).toUInt()
        "fail1" -> if (i == 1) error("color callback channel 1") else a
        else -> error("color callback failure")
    }
    private fun alpha32(mode: String, a: UInt, b: UInt): UInt = when (mode) {
        "left" -> a; "right" -> b; "difference" -> a - b; "tagged" -> 219u
        else -> error("alpha callback forced")
    }
    private fun unary32(mode: String, a: UInt): UInt = when (mode) {
        "identity" -> a; "negate" -> 0u - a; "offset" -> a + 37u; "scale" -> a * 3u - 11u
        else -> error("map callback failure")
    }
    private fun binaryF(mode: String, i: Int, a: Float, b: Float): Float = when (mode) {
        "left" -> a; "right" -> b
        "sum" -> a + b
        "difference" -> a - b
        "weighted" -> (i + 1).toFloat() * 17f + a * 3f - b * 5f
        "index" -> (i + 123).toFloat()
        "fail1" -> if (i == 1) error("color callback channel 1") else a
        else -> error("color callback failure")
    }
    private fun alphaF(mode: String, a: Float, b: Float): Float = when (mode) {
        "left" -> a; "right" -> b; "difference" -> a - b; "tagged" -> 219f
        else -> error("alpha callback forced")
    }
    private fun unaryF(mode: String, a: Float): Float = when (mode) {
        "identity" -> a; "negate" -> -a; "offset" -> a + 37f; "scale" -> a * 3f - 11f
        else -> error("map callback failure")
    }
    private fun pure(op: String, type: String, mode: String, left: IntArray, right: IntArray): Observation {
        val result = Observation()
        val n = count(type); val pixels = left.size / n
        val out = IntArray(pixels * if (op == "meta") 1 else n)
        val color = mode.substringBefore('/'); val alpha = mode.substringAfter('/', "")
        when (type) {
            "y8" -> {
                val prototype: UByte = 0u
                if (op == "meta") result.values["components"] = ImagePixelOps.componentCount(prototype)
                for (i in 0..<pixels) {
                    val src = i * n; val at = i * if (op == "meta") 1 else n
                    val p: UByte = left[src].toUByte()
                    if (op == "meta") { out[at] = ImagePixelOps.pixelOpacity(p).toInt(); continue }
                    fun other(): UByte = right[src].toUByte()
                    val r: UByte = when (op) {
                        "mix" -> ImagePixelOps.mixWith({ index, a, b -> binary8(mode, index, a, b) }, p, other())
                        "mix-alpha" -> ImagePixelOps.mixWithAlpha({ index, a, b -> binary8(color, index, a, b) }, { a, b -> alpha8(alpha, a, b) }, p, other())
                        "map" -> ImagePixelOps.colorMap({ unary8(mode, it) }, p)
                        else -> error(op)
                    }
                    out[at] = r.toInt()
                }
            }
            "y16" -> {
                val prototype: UShort = 0u
                if (op == "meta") result.values["components"] = ImagePixelOps.componentCount(prototype)
                for (i in 0..<pixels) {
                    val src = i * n; val at = i * if (op == "meta") 1 else n
                    val p: UShort = left[src].toUShort()
                    if (op == "meta") { out[at] = ImagePixelOps.pixelOpacity(p).toInt(); continue }
                    fun other(): UShort = right[src].toUShort()
                    val r: UShort = when (op) {
                        "mix" -> ImagePixelOps.mixWith({ index, a, b -> binary16(mode, index, a, b) }, p, other())
                        "mix-alpha" -> ImagePixelOps.mixWithAlpha({ index, a, b -> binary16(color, index, a, b) }, { a, b -> alpha16(alpha, a, b) }, p, other())
                        "map" -> ImagePixelOps.colorMap({ unary16(mode, it) }, p)
                        else -> error(op)
                    }
                    out[at] = r.toInt()
                }
            }
            "y32" -> {
                val prototype: UInt = 0u
                if (op == "meta") result.values["components"] = ImagePixelOps.componentCount(prototype)
                for (i in 0..<pixels) {
                    val src = i * n; val at = i * if (op == "meta") 1 else n
                    val p: UInt = left[src].toUInt()
                    if (op == "meta") { out[at] = ImagePixelOps.pixelOpacity(p).toInt(); continue }
                    fun other(): UInt = right[src].toUInt()
                    val r: UInt = when (op) {
                        "mix" -> ImagePixelOps.mixWith({ index, a, b -> binary32(mode, index, a, b) }, p, other())
                        "mix-alpha" -> ImagePixelOps.mixWithAlpha({ index, a, b -> binary32(color, index, a, b) }, { a, b -> alpha32(alpha, a, b) }, p, other())
                        "map" -> ImagePixelOps.colorMap({ unary32(mode, it) }, p)
                        else -> error(op)
                    }
                    out[at] = r.toInt()
                }
            }
            "yf" -> {
                val prototype: Float = 0f
                if (op == "meta") result.values["components"] = ImagePixelOps.componentCount(prototype)
                for (i in 0..<pixels) {
                    val src = i * n; val at = i * if (op == "meta") 1 else n
                    val p: Float = Float.fromBits(left[src])
                    if (op == "meta") { out[at] = ImagePixelOps.pixelOpacity(p).toRawBits(); continue }
                    fun other(): Float = Float.fromBits(right[src])
                    val r: Float = when (op) {
                        "mix" -> ImagePixelOps.mixWith({ index, a, b -> binaryF(mode, index, a, b) }, p, other())
                        "mix-alpha" -> ImagePixelOps.mixWithAlpha({ index, a, b -> binaryF(color, index, a, b) }, { a, b -> alphaF(alpha, a, b) }, p, other())
                        "map" -> ImagePixelOps.colorMap({ unaryF(mode, it) }, p)
                        else -> error(op)
                    }
                    out[at] = r.toRawBits()
                }
            }
            "ya8" -> {
                val prototype: PixelYA8 = PixelYA8(0u, 0u)
                if (op == "meta") result.values["components"] = ImagePixelOps.componentCount(prototype)
                for (i in 0..<pixels) {
                    val src = i * n; val at = i * if (op == "meta") 1 else n
                    val p: PixelYA8 = PixelYA8(left[src].toUByte(), left[src + 1].toUByte())
                    if (op == "meta") { out[at] = ImagePixelOps.pixelOpacity(p).toInt(); continue }
                    fun other(): PixelYA8 = PixelYA8(right[src].toUByte(), right[src + 1].toUByte())
                    val r: PixelYA8 = when (op) {
                        "mix" -> ImagePixelOps.mixWith({ index, a, b -> binary8(mode, index, a, b) }, p, other())
                        "mix-alpha" -> ImagePixelOps.mixWithAlpha({ index, a, b -> binary8(color, index, a, b) }, { a, b -> alpha8(alpha, a, b) }, p, other())
                        "map" -> ImagePixelOps.colorMap({ unary8(mode, it) }, p)
                        else -> error(op)
                    }
                    out[at] = r.luma.toInt()
                    out[at + 1] = r.alpha.toInt()
                }
            }
            "ya16" -> {
                val prototype: PixelYA16 = PixelYA16(0u, 0u)
                if (op == "meta") result.values["components"] = ImagePixelOps.componentCount(prototype)
                for (i in 0..<pixels) {
                    val src = i * n; val at = i * if (op == "meta") 1 else n
                    val p: PixelYA16 = PixelYA16(left[src].toUShort(), left[src + 1].toUShort())
                    if (op == "meta") { out[at] = ImagePixelOps.pixelOpacity(p).toInt(); continue }
                    fun other(): PixelYA16 = PixelYA16(right[src].toUShort(), right[src + 1].toUShort())
                    val r: PixelYA16 = when (op) {
                        "mix" -> ImagePixelOps.mixWith({ index, a, b -> binary16(mode, index, a, b) }, p, other())
                        "mix-alpha" -> ImagePixelOps.mixWithAlpha({ index, a, b -> binary16(color, index, a, b) }, { a, b -> alpha16(alpha, a, b) }, p, other())
                        "map" -> ImagePixelOps.colorMap({ unary16(mode, it) }, p)
                        else -> error(op)
                    }
                    out[at] = r.luma.toInt()
                    out[at + 1] = r.alpha.toInt()
                }
            }
            "rgb8" -> {
                val prototype: PixelRGB8 = PixelRGB8(0u, 0u, 0u)
                if (op == "meta") result.values["components"] = ImagePixelOps.componentCount(prototype)
                for (i in 0..<pixels) {
                    val src = i * n; val at = i * if (op == "meta") 1 else n
                    val p: PixelRGB8 = PixelRGB8(left[src].toUByte(), left[src + 1].toUByte(), left[src + 2].toUByte())
                    if (op == "meta") { out[at] = ImagePixelOps.pixelOpacity(p).toInt(); continue }
                    fun other(): PixelRGB8 = PixelRGB8(right[src].toUByte(), right[src + 1].toUByte(), right[src + 2].toUByte())
                    val r: PixelRGB8 = when (op) {
                        "mix" -> ImagePixelOps.mixWith({ index, a, b -> binary8(mode, index, a, b) }, p, other())
                        "mix-alpha" -> ImagePixelOps.mixWithAlpha({ index, a, b -> binary8(color, index, a, b) }, { a, b -> alpha8(alpha, a, b) }, p, other())
                        "map" -> ImagePixelOps.colorMap({ unary8(mode, it) }, p)
                        else -> error(op)
                    }
                    out[at] = r.red.toInt()
                    out[at + 1] = r.green.toInt()
                    out[at + 2] = r.blue.toInt()
                }
            }
            "rgb16" -> {
                val prototype: PixelRGB16 = PixelRGB16(0u, 0u, 0u)
                if (op == "meta") result.values["components"] = ImagePixelOps.componentCount(prototype)
                for (i in 0..<pixels) {
                    val src = i * n; val at = i * if (op == "meta") 1 else n
                    val p: PixelRGB16 = PixelRGB16(left[src].toUShort(), left[src + 1].toUShort(), left[src + 2].toUShort())
                    if (op == "meta") { out[at] = ImagePixelOps.pixelOpacity(p).toInt(); continue }
                    fun other(): PixelRGB16 = PixelRGB16(right[src].toUShort(), right[src + 1].toUShort(), right[src + 2].toUShort())
                    val r: PixelRGB16 = when (op) {
                        "mix" -> ImagePixelOps.mixWith({ index, a, b -> binary16(mode, index, a, b) }, p, other())
                        "mix-alpha" -> ImagePixelOps.mixWithAlpha({ index, a, b -> binary16(color, index, a, b) }, { a, b -> alpha16(alpha, a, b) }, p, other())
                        "map" -> ImagePixelOps.colorMap({ unary16(mode, it) }, p)
                        else -> error(op)
                    }
                    out[at] = r.red.toInt()
                    out[at + 1] = r.green.toInt()
                    out[at + 2] = r.blue.toInt()
                }
            }
            "rgbf" -> {
                val prototype: PixelRGBF = PixelRGBF(0f, 0f, 0f)
                if (op == "meta") result.values["components"] = ImagePixelOps.componentCount(prototype)
                for (i in 0..<pixels) {
                    val src = i * n; val at = i * if (op == "meta") 1 else n
                    val p: PixelRGBF = PixelRGBF(Float.fromBits(left[src]), Float.fromBits(left[src + 1]), Float.fromBits(left[src + 2]))
                    if (op == "meta") { out[at] = ImagePixelOps.pixelOpacity(p).toRawBits(); continue }
                    fun other(): PixelRGBF = PixelRGBF(Float.fromBits(right[src]), Float.fromBits(right[src + 1]), Float.fromBits(right[src + 2]))
                    val r: PixelRGBF = when (op) {
                        "mix" -> ImagePixelOps.mixWith({ index, a, b -> binaryF(mode, index, a, b) }, p, other())
                        "mix-alpha" -> ImagePixelOps.mixWithAlpha({ index, a, b -> binaryF(color, index, a, b) }, { a, b -> alphaF(alpha, a, b) }, p, other())
                        "map" -> ImagePixelOps.colorMap({ unaryF(mode, it) }, p)
                        else -> error(op)
                    }
                    out[at] = r.red.toRawBits()
                    out[at + 1] = r.green.toRawBits()
                    out[at + 2] = r.blue.toRawBits()
                }
            }
            "rgba8" -> {
                val prototype: PixelRGBA8 = PixelRGBA8(0u, 0u, 0u, 0u)
                if (op == "meta") result.values["components"] = ImagePixelOps.componentCount(prototype)
                for (i in 0..<pixels) {
                    val src = i * n; val at = i * if (op == "meta") 1 else n
                    val p: PixelRGBA8 = PixelRGBA8(left[src].toUByte(), left[src + 1].toUByte(), left[src + 2].toUByte(), left[src + 3].toUByte())
                    if (op == "meta") { out[at] = ImagePixelOps.pixelOpacity(p).toInt(); continue }
                    fun other(): PixelRGBA8 = PixelRGBA8(right[src].toUByte(), right[src + 1].toUByte(), right[src + 2].toUByte(), right[src + 3].toUByte())
                    val r: PixelRGBA8 = when (op) {
                        "mix" -> ImagePixelOps.mixWith({ index, a, b -> binary8(mode, index, a, b) }, p, other())
                        "mix-alpha" -> ImagePixelOps.mixWithAlpha({ index, a, b -> binary8(color, index, a, b) }, { a, b -> alpha8(alpha, a, b) }, p, other())
                        "map" -> ImagePixelOps.colorMap({ unary8(mode, it) }, p)
                        else -> error(op)
                    }
                    out[at] = r.red.toInt()
                    out[at + 1] = r.green.toInt()
                    out[at + 2] = r.blue.toInt()
                    out[at + 3] = r.alpha.toInt()
                }
            }
            "rgba16" -> {
                val prototype: PixelRGBA16 = PixelRGBA16(0u, 0u, 0u, 0u)
                if (op == "meta") result.values["components"] = ImagePixelOps.componentCount(prototype)
                for (i in 0..<pixels) {
                    val src = i * n; val at = i * if (op == "meta") 1 else n
                    val p: PixelRGBA16 = PixelRGBA16(left[src].toUShort(), left[src + 1].toUShort(), left[src + 2].toUShort(), left[src + 3].toUShort())
                    if (op == "meta") { out[at] = ImagePixelOps.pixelOpacity(p).toInt(); continue }
                    fun other(): PixelRGBA16 = PixelRGBA16(right[src].toUShort(), right[src + 1].toUShort(), right[src + 2].toUShort(), right[src + 3].toUShort())
                    val r: PixelRGBA16 = when (op) {
                        "mix" -> ImagePixelOps.mixWith({ index, a, b -> binary16(mode, index, a, b) }, p, other())
                        "mix-alpha" -> ImagePixelOps.mixWithAlpha({ index, a, b -> binary16(color, index, a, b) }, { a, b -> alpha16(alpha, a, b) }, p, other())
                        "map" -> ImagePixelOps.colorMap({ unary16(mode, it) }, p)
                        else -> error(op)
                    }
                    out[at] = r.red.toInt()
                    out[at + 1] = r.green.toInt()
                    out[at + 2] = r.blue.toInt()
                    out[at + 3] = r.alpha.toInt()
                }
            }
            "ycbcr8" -> {
                val prototype: PixelYCbCr8 = PixelYCbCr8(0u, 0u, 0u)
                if (op == "meta") result.values["components"] = ImagePixelOps.componentCount(prototype)
                for (i in 0..<pixels) {
                    val src = i * n; val at = i * if (op == "meta") 1 else n
                    val p: PixelYCbCr8 = PixelYCbCr8(left[src].toUByte(), left[src + 1].toUByte(), left[src + 2].toUByte())
                    if (op == "meta") { out[at] = ImagePixelOps.pixelOpacity(p).toInt(); continue }
                    fun other(): PixelYCbCr8 = PixelYCbCr8(right[src].toUByte(), right[src + 1].toUByte(), right[src + 2].toUByte())
                    val r: PixelYCbCr8 = when (op) {
                        "mix" -> ImagePixelOps.mixWith({ index, a, b -> binary8(mode, index, a, b) }, p, other())
                        "mix-alpha" -> ImagePixelOps.mixWithAlpha({ index, a, b -> binary8(color, index, a, b) }, { a, b -> alpha8(alpha, a, b) }, p, other())
                        "map" -> ImagePixelOps.colorMap({ unary8(mode, it) }, p)
                        else -> error(op)
                    }
                    out[at] = r.luma.toInt()
                    out[at + 1] = r.cb.toInt()
                    out[at + 2] = r.cr.toInt()
                }
            }
            "ycbcrk8" -> {
                val prototype: PixelYCbCrK8 = PixelYCbCrK8(0u, 0u, 0u, 0u)
                if (op == "meta") result.values["components"] = ImagePixelOps.componentCount(prototype)
                for (i in 0..<pixels) {
                    val src = i * n; val at = i * if (op == "meta") 1 else n
                    val p: PixelYCbCrK8 = PixelYCbCrK8(left[src].toUByte(), left[src + 1].toUByte(), left[src + 2].toUByte(), left[src + 3].toUByte())
                    if (op == "meta") { out[at] = ImagePixelOps.pixelOpacity(p).toInt(); continue }
                    fun other(): PixelYCbCrK8 = PixelYCbCrK8(right[src].toUByte(), right[src + 1].toUByte(), right[src + 2].toUByte(), right[src + 3].toUByte())
                    val r: PixelYCbCrK8 = when (op) {
                        "mix" -> ImagePixelOps.mixWith({ index, a, b -> binary8(mode, index, a, b) }, p, other())
                        "mix-alpha" -> ImagePixelOps.mixWithAlpha({ index, a, b -> binary8(color, index, a, b) }, { a, b -> alpha8(alpha, a, b) }, p, other())
                        "map" -> ImagePixelOps.colorMap({ unary8(mode, it) }, p)
                        else -> error(op)
                    }
                    out[at] = r.luma.toInt()
                    out[at + 1] = r.cb.toInt()
                    out[at + 2] = r.cr.toInt()
                    out[at + 3] = r.black.toInt()
                }
            }
            "cmyk8" -> {
                val prototype: PixelCMYK8 = PixelCMYK8(0u, 0u, 0u, 0u)
                if (op == "meta") result.values["components"] = ImagePixelOps.componentCount(prototype)
                for (i in 0..<pixels) {
                    val src = i * n; val at = i * if (op == "meta") 1 else n
                    val p: PixelCMYK8 = PixelCMYK8(left[src].toUByte(), left[src + 1].toUByte(), left[src + 2].toUByte(), left[src + 3].toUByte())
                    if (op == "meta") { out[at] = ImagePixelOps.pixelOpacity(p).toInt(); continue }
                    fun other(): PixelCMYK8 = PixelCMYK8(right[src].toUByte(), right[src + 1].toUByte(), right[src + 2].toUByte(), right[src + 3].toUByte())
                    val r: PixelCMYK8 = when (op) {
                        "mix" -> ImagePixelOps.mixWith({ index, a, b -> binary8(mode, index, a, b) }, p, other())
                        "mix-alpha" -> ImagePixelOps.mixWithAlpha({ index, a, b -> binary8(color, index, a, b) }, { a, b -> alpha8(alpha, a, b) }, p, other())
                        "map" -> ImagePixelOps.colorMap({ unary8(mode, it) }, p)
                        else -> error(op)
                    }
                    out[at] = r.cyan.toInt()
                    out[at + 1] = r.magenta.toInt()
                    out[at + 2] = r.yellow.toInt()
                    out[at + 3] = r.black.toInt()
                }
            }
            "cmyk16" -> {
                val prototype: PixelCMYK16 = PixelCMYK16(0u, 0u, 0u, 0u)
                if (op == "meta") result.values["components"] = ImagePixelOps.componentCount(prototype)
                for (i in 0..<pixels) {
                    val src = i * n; val at = i * if (op == "meta") 1 else n
                    val p: PixelCMYK16 = PixelCMYK16(left[src].toUShort(), left[src + 1].toUShort(), left[src + 2].toUShort(), left[src + 3].toUShort())
                    if (op == "meta") { out[at] = ImagePixelOps.pixelOpacity(p).toInt(); continue }
                    fun other(): PixelCMYK16 = PixelCMYK16(right[src].toUShort(), right[src + 1].toUShort(), right[src + 2].toUShort(), right[src + 3].toUShort())
                    val r: PixelCMYK16 = when (op) {
                        "mix" -> ImagePixelOps.mixWith({ index, a, b -> binary16(mode, index, a, b) }, p, other())
                        "mix-alpha" -> ImagePixelOps.mixWithAlpha({ index, a, b -> binary16(color, index, a, b) }, { a, b -> alpha16(alpha, a, b) }, p, other())
                        "map" -> ImagePixelOps.colorMap({ unary16(mode, it) }, p)
                        else -> error(op)
                    }
                    out[at] = r.cyan.toInt()
                    out[at + 1] = r.magenta.toInt()
                    out[at + 2] = r.yellow.toInt()
                    out[at + 3] = r.black.toInt()
                }
            }
            else -> error(type)
        }
        result.data["hex"] = bytes(out, if (type.endsWith('f') || type == "y32") 4 else if (type.endsWith("16")) 2 else 1)
        return result
    }
    private fun access(op: String, type: String, mode: String, p: LongArray, left: IntArray, right: IntArray): Observation {
        val result = Observation()
        if (op == "base" || op == "mutable-base") {
            val index = ImagePixelAccess.pixelBaseIndex(p[0], p[2], p[3], count(type))
            result.values["index"] = index
            if (p[0] in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() && p[1] in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
                val w = p[0].toInt(); val h = p[1].toInt()
                when (type) {
                    "y8" -> assertEquals(index, if (op == "base") ImagePixelAccess.pixelBaseIndex(ImageY8(w, h, ByteArray(0)), p[2], p[3]) else ImagePixelAccess.mutablePixelBaseIndex(MutableImageY8(w, h, ByteArray(0)), p[2], p[3]))
                    "y16" -> assertEquals(index, if (op == "base") ImagePixelAccess.pixelBaseIndex(ImageY16(w, h, ShortArray(0)), p[2], p[3]) else ImagePixelAccess.mutablePixelBaseIndex(MutableImageY16(w, h, ShortArray(0)), p[2], p[3]))
                    "y32" -> assertEquals(index, if (op == "base") ImagePixelAccess.pixelBaseIndex(ImageY32(w, h, IntArray(0)), p[2], p[3]) else ImagePixelAccess.mutablePixelBaseIndex(MutableImageY32(w, h, IntArray(0)), p[2], p[3]))
                    "yf" -> assertEquals(index, if (op == "base") ImagePixelAccess.pixelBaseIndex(ImageYF(w, h, FloatArray(0)), p[2], p[3]) else ImagePixelAccess.mutablePixelBaseIndex(MutableImageYF(w, h, FloatArray(0)), p[2], p[3]))
                    "ya8" -> assertEquals(index, if (op == "base") ImagePixelAccess.pixelBaseIndex(ImageYA8(w, h, ByteArray(0)), p[2], p[3]) else ImagePixelAccess.mutablePixelBaseIndex(MutableImageYA8(w, h, ByteArray(0)), p[2], p[3]))
                    "ya16" -> assertEquals(index, if (op == "base") ImagePixelAccess.pixelBaseIndex(ImageYA16(w, h, ShortArray(0)), p[2], p[3]) else ImagePixelAccess.mutablePixelBaseIndex(MutableImageYA16(w, h, ShortArray(0)), p[2], p[3]))
                    "rgb8" -> assertEquals(index, if (op == "base") ImagePixelAccess.pixelBaseIndex(ImageRGB8(w, h, ByteArray(0)), p[2], p[3]) else ImagePixelAccess.mutablePixelBaseIndex(MutableImageRGB8(w, h, ByteArray(0)), p[2], p[3]))
                    "rgb16" -> assertEquals(index, if (op == "base") ImagePixelAccess.pixelBaseIndex(ImageRGB16(w, h, ShortArray(0)), p[2], p[3]) else ImagePixelAccess.mutablePixelBaseIndex(MutableImageRGB16(w, h, ShortArray(0)), p[2], p[3]))
                    "rgbf" -> assertEquals(index, if (op == "base") ImagePixelAccess.pixelBaseIndex(ImageRGBF(w, h, FloatArray(0)), p[2], p[3]) else ImagePixelAccess.mutablePixelBaseIndex(MutableImageRGBF(w, h, FloatArray(0)), p[2], p[3]))
                    "rgba8" -> assertEquals(index, if (op == "base") ImagePixelAccess.pixelBaseIndex(ImageRGBA8(w, h, ByteArray(0)), p[2], p[3]) else ImagePixelAccess.mutablePixelBaseIndex(MutableImageRGBA8(w, h, ByteArray(0)), p[2], p[3]))
                    "rgba16" -> assertEquals(index, if (op == "base") ImagePixelAccess.pixelBaseIndex(ImageRGBA16(w, h, ShortArray(0)), p[2], p[3]) else ImagePixelAccess.mutablePixelBaseIndex(MutableImageRGBA16(w, h, ShortArray(0)), p[2], p[3]))
                    "ycbcr8" -> assertEquals(index, if (op == "base") ImagePixelAccess.pixelBaseIndex(ImageYCbCr8(w, h, ByteArray(0)), p[2], p[3]) else ImagePixelAccess.mutablePixelBaseIndex(MutableImageYCbCr8(w, h, ByteArray(0)), p[2], p[3]))
                    "ycbcrk8" -> assertEquals(index, if (op == "base") ImagePixelAccess.pixelBaseIndex(ImageYCbCrK8(w, h, ByteArray(0)), p[2], p[3]) else ImagePixelAccess.mutablePixelBaseIndex(MutableImageYCbCrK8(w, h, ByteArray(0)), p[2], p[3]))
                    "cmyk8" -> assertEquals(index, if (op == "base") ImagePixelAccess.pixelBaseIndex(ImageCMYK8(w, h, ByteArray(0)), p[2], p[3]) else ImagePixelAccess.mutablePixelBaseIndex(MutableImageCMYK8(w, h, ByteArray(0)), p[2], p[3]))
                    "cmyk16" -> assertEquals(index, if (op == "base") ImagePixelAccess.pixelBaseIndex(ImageCMYK16(w, h, ShortArray(0)), p[2], p[3]) else ImagePixelAccess.mutablePixelBaseIndex(MutableImageCMYK16(w, h, ShortArray(0)), p[2], p[3]))
                    else -> error(type)
                }
            }
            return result
        }
        val w = p[0].toInt(); val h = p[1].toInt(); val x = p[2]; val y = p[3]; val index = p[4]
        assertEquals(p[0], w.toLong()); assertEquals(p[1], h.toLong())
        when (type) {
            "y8" -> {
                val data = ByteArray(left.size) { left[it].toByte() }
                fun replacement(): UByte = right[0].toUByte()
                fun serial(value: UByte) = bytes(intArrayOf(value.toInt()), 1)
                val original = ImageY8(w, h, data)
                val mutable = MutableImageY8(w, h, data)
                when (op) {
                    "freeze" -> {
                        val snapshot = ImageMutable.freezeImage(mutable)
                        assertNotSame(mutable.data, snapshot.data)
                        result.values["shared"] = mutable.data === snapshot.data
                        if (mode != "view") ImagePixelAccess.unsafeWritePixel(mutable.data, index, replacement())
                        result.data["imageHex"] = packed(snapshot.data); result.data["dataHex"] = packed(mutable.data)
                        result.values["width"] = snapshot.width; result.values["height"] = snapshot.height
                    }
                    "unsafe-freeze" -> {
                        val snapshot = ImageMutable.unsafeFreezeImage(mutable)
                        assertSame(mutable.data, snapshot.data)
                        result.values["shared"] = mutable.data === snapshot.data
                        result.data["dataHex"] = packed(snapshot.data)
                        result.values["width"] = snapshot.width; result.values["height"] = snapshot.height
                    }
                    "thaw", "unsafe-thaw" -> {
                        val before = packed(original.data)
                        val writable = if (op == "thaw") ImageMutable.thawImage(original) else ImageMutable.unsafeThawImage(original)
                        if (op == "thaw") assertNotSame(original.data, writable.data) else assertSame(original.data, writable.data)
                        result.values["shared"] = original.data === writable.data
                        if (mode != "view") ImagePixelAccess.unsafeWritePixel(writable.data, index, replacement())
                        result.data["sourceHex"] = if (op == "thaw") packed(original.data) else before
                        result.data["dataHex"] = packed(writable.data)
                        result.values["width"] = writable.width; result.values["height"] = writable.height
                    }
                    "create" -> {
                        val writable = ImageMutable.createMutableImage(w, h, replacement())
                        result.data["dataHex"] = packed(writable.data)
                        result.values["width"] = writable.width; result.values["height"] = writable.height
                    }
                    "new" -> {
                        val writable = ImageMutable.newMutableY8(w, h)
                        assertNotSame(data, writable.data); assertEquals(data.size, writable.data.size)
                        data.copyInto(writable.data) // Original oracle also initializes every component before observation.
                        result.data["dataHex"] = packed(writable.data)
                        result.values["width"] = writable.width; result.values["height"] = writable.height
                    }
                    else -> {
                        try {
                            val value = when (op) {
                                "pixel" -> serial(ImagePixelAccess.pixelAt(original, x, y))
                                "read" -> serial(ImagePixelAccess.readPixel(mutable, x, y))
                                "unsafe-pixel" -> serial(ImagePixelAccess.unsafePixelAtY8(data, index))
                                "unsafe-read" -> serial(ImagePixelAccess.unsafeReadPixel(mutable, index))
                                "write" -> { ImagePixelAccess.writePixel(mutable, x, y, replacement()); byteArrayOf() }
                                "unsafe-write" -> { ImagePixelAccess.unsafeWritePixel(data, index, replacement()); byteArrayOf() }
                                else -> error(op)
                            }
                            result.data["pixelHex"] = value
                        } catch (error: RuntimeException) { result.values["exception"] = error.message.orEmpty() }
                        result.data["dataHex"] = packed(data)
                    }
                }
            }
            "y16" -> {
                val data = ShortArray(left.size) { left[it].toShort() }
                fun replacement(): UShort = right[0].toUShort()
                fun serial(value: UShort) = bytes(intArrayOf(value.toInt()), 2)
                val original = ImageY16(w, h, data)
                val mutable = MutableImageY16(w, h, data)
                when (op) {
                    "freeze" -> {
                        val snapshot = ImageMutable.freezeImage(mutable)
                        assertNotSame(mutable.data, snapshot.data)
                        result.values["shared"] = mutable.data === snapshot.data
                        if (mode != "view") ImagePixelAccess.unsafeWritePixel(mutable.data, index, replacement())
                        result.data["imageHex"] = packed(snapshot.data); result.data["dataHex"] = packed(mutable.data)
                        result.values["width"] = snapshot.width; result.values["height"] = snapshot.height
                    }
                    "unsafe-freeze" -> {
                        val snapshot = ImageMutable.unsafeFreezeImage(mutable)
                        assertSame(mutable.data, snapshot.data)
                        result.values["shared"] = mutable.data === snapshot.data
                        result.data["dataHex"] = packed(snapshot.data)
                        result.values["width"] = snapshot.width; result.values["height"] = snapshot.height
                    }
                    "thaw", "unsafe-thaw" -> {
                        val before = packed(original.data)
                        val writable = if (op == "thaw") ImageMutable.thawImage(original) else ImageMutable.unsafeThawImage(original)
                        if (op == "thaw") assertNotSame(original.data, writable.data) else assertSame(original.data, writable.data)
                        result.values["shared"] = original.data === writable.data
                        if (mode != "view") ImagePixelAccess.unsafeWritePixel(writable.data, index, replacement())
                        result.data["sourceHex"] = if (op == "thaw") packed(original.data) else before
                        result.data["dataHex"] = packed(writable.data)
                        result.values["width"] = writable.width; result.values["height"] = writable.height
                    }
                    "create" -> {
                        val writable = ImageMutable.createMutableImage(w, h, replacement())
                        result.data["dataHex"] = packed(writable.data)
                        result.values["width"] = writable.width; result.values["height"] = writable.height
                    }
                    "new" -> {
                        val writable = ImageMutable.newMutableY16(w, h)
                        assertNotSame(data, writable.data); assertEquals(data.size, writable.data.size)
                        data.copyInto(writable.data) // Original oracle also initializes every component before observation.
                        result.data["dataHex"] = packed(writable.data)
                        result.values["width"] = writable.width; result.values["height"] = writable.height
                    }
                    else -> {
                        try {
                            val value = when (op) {
                                "pixel" -> serial(ImagePixelAccess.pixelAt(original, x, y))
                                "read" -> serial(ImagePixelAccess.readPixel(mutable, x, y))
                                "unsafe-pixel" -> serial(ImagePixelAccess.unsafePixelAtY16(data, index))
                                "unsafe-read" -> serial(ImagePixelAccess.unsafeReadPixel(mutable, index))
                                "write" -> { ImagePixelAccess.writePixel(mutable, x, y, replacement()); byteArrayOf() }
                                "unsafe-write" -> { ImagePixelAccess.unsafeWritePixel(data, index, replacement()); byteArrayOf() }
                                else -> error(op)
                            }
                            result.data["pixelHex"] = value
                        } catch (error: RuntimeException) { result.values["exception"] = error.message.orEmpty() }
                        result.data["dataHex"] = packed(data)
                    }
                }
            }
            "y32" -> {
                val data = left.copyOf()
                fun replacement(): UInt = right[0].toUInt()
                fun serial(value: UInt) = bytes(intArrayOf(value.toInt()), 4)
                val original = ImageY32(w, h, data)
                val mutable = MutableImageY32(w, h, data)
                when (op) {
                    "freeze" -> {
                        val snapshot = ImageMutable.freezeImage(mutable)
                        assertNotSame(mutable.data, snapshot.data)
                        result.values["shared"] = mutable.data === snapshot.data
                        if (mode != "view") ImagePixelAccess.unsafeWritePixel(mutable.data, index, replacement())
                        result.data["imageHex"] = packed(snapshot.data); result.data["dataHex"] = packed(mutable.data)
                        result.values["width"] = snapshot.width; result.values["height"] = snapshot.height
                    }
                    "unsafe-freeze" -> {
                        val snapshot = ImageMutable.unsafeFreezeImage(mutable)
                        assertSame(mutable.data, snapshot.data)
                        result.values["shared"] = mutable.data === snapshot.data
                        result.data["dataHex"] = packed(snapshot.data)
                        result.values["width"] = snapshot.width; result.values["height"] = snapshot.height
                    }
                    "thaw", "unsafe-thaw" -> {
                        val before = packed(original.data)
                        val writable = if (op == "thaw") ImageMutable.thawImage(original) else ImageMutable.unsafeThawImage(original)
                        if (op == "thaw") assertNotSame(original.data, writable.data) else assertSame(original.data, writable.data)
                        result.values["shared"] = original.data === writable.data
                        if (mode != "view") ImagePixelAccess.unsafeWritePixel(writable.data, index, replacement())
                        result.data["sourceHex"] = if (op == "thaw") packed(original.data) else before
                        result.data["dataHex"] = packed(writable.data)
                        result.values["width"] = writable.width; result.values["height"] = writable.height
                    }
                    "create" -> {
                        val writable = ImageMutable.createMutableImage(w, h, replacement())
                        result.data["dataHex"] = packed(writable.data)
                        result.values["width"] = writable.width; result.values["height"] = writable.height
                    }
                    "new" -> {
                        val writable = ImageMutable.newMutableY32(w, h)
                        assertNotSame(data, writable.data); assertEquals(data.size, writable.data.size)
                        data.copyInto(writable.data) // Original oracle also initializes every component before observation.
                        result.data["dataHex"] = packed(writable.data)
                        result.values["width"] = writable.width; result.values["height"] = writable.height
                    }
                    else -> {
                        try {
                            val value = when (op) {
                                "pixel" -> serial(ImagePixelAccess.pixelAt(original, x, y))
                                "read" -> serial(ImagePixelAccess.readPixel(mutable, x, y))
                                "unsafe-pixel" -> serial(ImagePixelAccess.unsafePixelAtY32(data, index))
                                "unsafe-read" -> serial(ImagePixelAccess.unsafeReadPixel(mutable, index))
                                "write" -> { ImagePixelAccess.writePixel(mutable, x, y, replacement()); byteArrayOf() }
                                "unsafe-write" -> { ImagePixelAccess.unsafeWritePixel(data, index, replacement()); byteArrayOf() }
                                else -> error(op)
                            }
                            result.data["pixelHex"] = value
                        } catch (error: RuntimeException) { result.values["exception"] = error.message.orEmpty() }
                        result.data["dataHex"] = packed(data)
                    }
                }
            }
            "yf" -> {
                val data = FloatArray(left.size) { Float.fromBits(left[it]) }
                fun replacement(): Float = Float.fromBits(right[0])
                fun serial(value: Float) = bytes(intArrayOf(value.toRawBits()), 4)
                val original = ImageYF(w, h, data)
                val mutable = MutableImageYF(w, h, data)
                when (op) {
                    "freeze" -> {
                        val snapshot = ImageMutable.freezeImage(mutable)
                        assertNotSame(mutable.data, snapshot.data)
                        result.values["shared"] = mutable.data === snapshot.data
                        if (mode != "view") ImagePixelAccess.unsafeWritePixel(mutable.data, index, replacement())
                        result.data["imageHex"] = packed(snapshot.data); result.data["dataHex"] = packed(mutable.data)
                        result.values["width"] = snapshot.width; result.values["height"] = snapshot.height
                    }
                    "unsafe-freeze" -> {
                        val snapshot = ImageMutable.unsafeFreezeImage(mutable)
                        assertSame(mutable.data, snapshot.data)
                        result.values["shared"] = mutable.data === snapshot.data
                        result.data["dataHex"] = packed(snapshot.data)
                        result.values["width"] = snapshot.width; result.values["height"] = snapshot.height
                    }
                    "thaw", "unsafe-thaw" -> {
                        val before = packed(original.data)
                        val writable = if (op == "thaw") ImageMutable.thawImage(original) else ImageMutable.unsafeThawImage(original)
                        if (op == "thaw") assertNotSame(original.data, writable.data) else assertSame(original.data, writable.data)
                        result.values["shared"] = original.data === writable.data
                        if (mode != "view") ImagePixelAccess.unsafeWritePixel(writable.data, index, replacement())
                        result.data["sourceHex"] = if (op == "thaw") packed(original.data) else before
                        result.data["dataHex"] = packed(writable.data)
                        result.values["width"] = writable.width; result.values["height"] = writable.height
                    }
                    "create" -> {
                        val writable = ImageMutable.createMutableImage(w, h, replacement())
                        result.data["dataHex"] = packed(writable.data)
                        result.values["width"] = writable.width; result.values["height"] = writable.height
                    }
                    "new" -> {
                        val writable = ImageMutable.newMutableYF(w, h)
                        assertNotSame(data, writable.data); assertEquals(data.size, writable.data.size)
                        data.copyInto(writable.data) // Original oracle also initializes every component before observation.
                        result.data["dataHex"] = packed(writable.data)
                        result.values["width"] = writable.width; result.values["height"] = writable.height
                    }
                    else -> {
                        try {
                            val value = when (op) {
                                "pixel" -> serial(ImagePixelAccess.pixelAt(original, x, y))
                                "read" -> serial(ImagePixelAccess.readPixel(mutable, x, y))
                                "unsafe-pixel" -> serial(ImagePixelAccess.unsafePixelAtYF(data, index))
                                "unsafe-read" -> serial(ImagePixelAccess.unsafeReadPixel(mutable, index))
                                "write" -> { ImagePixelAccess.writePixel(mutable, x, y, replacement()); byteArrayOf() }
                                "unsafe-write" -> { ImagePixelAccess.unsafeWritePixel(data, index, replacement()); byteArrayOf() }
                                else -> error(op)
                            }
                            result.data["pixelHex"] = value
                        } catch (error: RuntimeException) { result.values["exception"] = error.message.orEmpty() }
                        result.data["dataHex"] = packed(data)
                    }
                }
            }
            "ya8" -> {
                val data = ByteArray(left.size) { left[it].toByte() }
                fun replacement(): PixelYA8 = PixelYA8(right[0].toUByte(), right[0 + 1].toUByte())
                fun serial(value: PixelYA8) = bytes(intArrayOf(value.luma.toInt(), value.alpha.toInt()), 1)
                val original = ImageYA8(w, h, data)
                val mutable = MutableImageYA8(w, h, data)
                when (op) {
                    "freeze" -> {
                        val snapshot = ImageMutable.freezeImage(mutable)
                        assertNotSame(mutable.data, snapshot.data)
                        result.values["shared"] = mutable.data === snapshot.data
                        if (mode != "view") ImagePixelAccess.unsafeWritePixel(mutable.data, index, replacement())
                        result.data["imageHex"] = packed(snapshot.data); result.data["dataHex"] = packed(mutable.data)
                        result.values["width"] = snapshot.width; result.values["height"] = snapshot.height
                    }
                    "unsafe-freeze" -> {
                        val snapshot = ImageMutable.unsafeFreezeImage(mutable)
                        assertSame(mutable.data, snapshot.data)
                        result.values["shared"] = mutable.data === snapshot.data
                        result.data["dataHex"] = packed(snapshot.data)
                        result.values["width"] = snapshot.width; result.values["height"] = snapshot.height
                    }
                    "thaw", "unsafe-thaw" -> {
                        val before = packed(original.data)
                        val writable = if (op == "thaw") ImageMutable.thawImage(original) else ImageMutable.unsafeThawImage(original)
                        if (op == "thaw") assertNotSame(original.data, writable.data) else assertSame(original.data, writable.data)
                        result.values["shared"] = original.data === writable.data
                        if (mode != "view") ImagePixelAccess.unsafeWritePixel(writable.data, index, replacement())
                        result.data["sourceHex"] = if (op == "thaw") packed(original.data) else before
                        result.data["dataHex"] = packed(writable.data)
                        result.values["width"] = writable.width; result.values["height"] = writable.height
                    }
                    "create" -> {
                        val writable = ImageMutable.createMutableImage(w, h, replacement())
                        result.data["dataHex"] = packed(writable.data)
                        result.values["width"] = writable.width; result.values["height"] = writable.height
                    }
                    "new" -> {
                        val writable = ImageMutable.newMutableYA8(w, h)
                        assertNotSame(data, writable.data); assertEquals(data.size, writable.data.size)
                        data.copyInto(writable.data) // Original oracle also initializes every component before observation.
                        result.data["dataHex"] = packed(writable.data)
                        result.values["width"] = writable.width; result.values["height"] = writable.height
                    }
                    else -> {
                        try {
                            val value = when (op) {
                                "pixel" -> serial(ImagePixelAccess.pixelAt(original, x, y))
                                "read" -> serial(ImagePixelAccess.readPixel(mutable, x, y))
                                "unsafe-pixel" -> serial(ImagePixelAccess.unsafePixelAtYA8(data, index))
                                "unsafe-read" -> serial(ImagePixelAccess.unsafeReadPixel(mutable, index))
                                "write" -> { ImagePixelAccess.writePixel(mutable, x, y, replacement()); byteArrayOf() }
                                "unsafe-write" -> { ImagePixelAccess.unsafeWritePixel(data, index, replacement()); byteArrayOf() }
                                else -> error(op)
                            }
                            result.data["pixelHex"] = value
                        } catch (error: RuntimeException) { result.values["exception"] = error.message.orEmpty() }
                        result.data["dataHex"] = packed(data)
                    }
                }
            }
            "ya16" -> {
                val data = ShortArray(left.size) { left[it].toShort() }
                fun replacement(): PixelYA16 = PixelYA16(right[0].toUShort(), right[0 + 1].toUShort())
                fun serial(value: PixelYA16) = bytes(intArrayOf(value.luma.toInt(), value.alpha.toInt()), 2)
                val original = ImageYA16(w, h, data)
                val mutable = MutableImageYA16(w, h, data)
                when (op) {
                    "freeze" -> {
                        val snapshot = ImageMutable.freezeImage(mutable)
                        assertNotSame(mutable.data, snapshot.data)
                        result.values["shared"] = mutable.data === snapshot.data
                        if (mode != "view") ImagePixelAccess.unsafeWritePixel(mutable.data, index, replacement())
                        result.data["imageHex"] = packed(snapshot.data); result.data["dataHex"] = packed(mutable.data)
                        result.values["width"] = snapshot.width; result.values["height"] = snapshot.height
                    }
                    "unsafe-freeze" -> {
                        val snapshot = ImageMutable.unsafeFreezeImage(mutable)
                        assertSame(mutable.data, snapshot.data)
                        result.values["shared"] = mutable.data === snapshot.data
                        result.data["dataHex"] = packed(snapshot.data)
                        result.values["width"] = snapshot.width; result.values["height"] = snapshot.height
                    }
                    "thaw", "unsafe-thaw" -> {
                        val before = packed(original.data)
                        val writable = if (op == "thaw") ImageMutable.thawImage(original) else ImageMutable.unsafeThawImage(original)
                        if (op == "thaw") assertNotSame(original.data, writable.data) else assertSame(original.data, writable.data)
                        result.values["shared"] = original.data === writable.data
                        if (mode != "view") ImagePixelAccess.unsafeWritePixel(writable.data, index, replacement())
                        result.data["sourceHex"] = if (op == "thaw") packed(original.data) else before
                        result.data["dataHex"] = packed(writable.data)
                        result.values["width"] = writable.width; result.values["height"] = writable.height
                    }
                    "create" -> {
                        val writable = ImageMutable.createMutableImage(w, h, replacement())
                        result.data["dataHex"] = packed(writable.data)
                        result.values["width"] = writable.width; result.values["height"] = writable.height
                    }
                    "new" -> {
                        val writable = ImageMutable.newMutableYA16(w, h)
                        assertNotSame(data, writable.data); assertEquals(data.size, writable.data.size)
                        data.copyInto(writable.data) // Original oracle also initializes every component before observation.
                        result.data["dataHex"] = packed(writable.data)
                        result.values["width"] = writable.width; result.values["height"] = writable.height
                    }
                    else -> {
                        try {
                            val value = when (op) {
                                "pixel" -> serial(ImagePixelAccess.pixelAt(original, x, y))
                                "read" -> serial(ImagePixelAccess.readPixel(mutable, x, y))
                                "unsafe-pixel" -> serial(ImagePixelAccess.unsafePixelAtYA16(data, index))
                                "unsafe-read" -> serial(ImagePixelAccess.unsafeReadPixel(mutable, index))
                                "write" -> { ImagePixelAccess.writePixel(mutable, x, y, replacement()); byteArrayOf() }
                                "unsafe-write" -> { ImagePixelAccess.unsafeWritePixel(data, index, replacement()); byteArrayOf() }
                                else -> error(op)
                            }
                            result.data["pixelHex"] = value
                        } catch (error: RuntimeException) { result.values["exception"] = error.message.orEmpty() }
                        result.data["dataHex"] = packed(data)
                    }
                }
            }
            "rgb8" -> {
                val data = ByteArray(left.size) { left[it].toByte() }
                fun replacement(): PixelRGB8 = PixelRGB8(right[0].toUByte(), right[0 + 1].toUByte(), right[0 + 2].toUByte())
                fun serial(value: PixelRGB8) = bytes(intArrayOf(value.red.toInt(), value.green.toInt(), value.blue.toInt()), 1)
                val original = ImageRGB8(w, h, data)
                val mutable = MutableImageRGB8(w, h, data)
                when (op) {
                    "freeze" -> {
                        val snapshot = ImageMutable.freezeImage(mutable)
                        assertNotSame(mutable.data, snapshot.data)
                        result.values["shared"] = mutable.data === snapshot.data
                        if (mode != "view") ImagePixelAccess.unsafeWritePixel(mutable.data, index, replacement())
                        result.data["imageHex"] = packed(snapshot.data); result.data["dataHex"] = packed(mutable.data)
                        result.values["width"] = snapshot.width; result.values["height"] = snapshot.height
                    }
                    "unsafe-freeze" -> {
                        val snapshot = ImageMutable.unsafeFreezeImage(mutable)
                        assertSame(mutable.data, snapshot.data)
                        result.values["shared"] = mutable.data === snapshot.data
                        result.data["dataHex"] = packed(snapshot.data)
                        result.values["width"] = snapshot.width; result.values["height"] = snapshot.height
                    }
                    "thaw", "unsafe-thaw" -> {
                        val before = packed(original.data)
                        val writable = if (op == "thaw") ImageMutable.thawImage(original) else ImageMutable.unsafeThawImage(original)
                        if (op == "thaw") assertNotSame(original.data, writable.data) else assertSame(original.data, writable.data)
                        result.values["shared"] = original.data === writable.data
                        if (mode != "view") ImagePixelAccess.unsafeWritePixel(writable.data, index, replacement())
                        result.data["sourceHex"] = if (op == "thaw") packed(original.data) else before
                        result.data["dataHex"] = packed(writable.data)
                        result.values["width"] = writable.width; result.values["height"] = writable.height
                    }
                    "create" -> {
                        val writable = ImageMutable.createMutableImage(w, h, replacement())
                        result.data["dataHex"] = packed(writable.data)
                        result.values["width"] = writable.width; result.values["height"] = writable.height
                    }
                    "new" -> {
                        val writable = ImageMutable.newMutableRGB8(w, h)
                        assertNotSame(data, writable.data); assertEquals(data.size, writable.data.size)
                        data.copyInto(writable.data) // Original oracle also initializes every component before observation.
                        result.data["dataHex"] = packed(writable.data)
                        result.values["width"] = writable.width; result.values["height"] = writable.height
                    }
                    else -> {
                        try {
                            val value = when (op) {
                                "pixel" -> serial(ImagePixelAccess.pixelAt(original, x, y))
                                "read" -> serial(ImagePixelAccess.readPixel(mutable, x, y))
                                "unsafe-pixel" -> serial(ImagePixelAccess.unsafePixelAtRGB8(data, index))
                                "unsafe-read" -> serial(ImagePixelAccess.unsafeReadPixel(mutable, index))
                                "write" -> { ImagePixelAccess.writePixel(mutable, x, y, replacement()); byteArrayOf() }
                                "unsafe-write" -> { ImagePixelAccess.unsafeWritePixel(data, index, replacement()); byteArrayOf() }
                                else -> error(op)
                            }
                            result.data["pixelHex"] = value
                        } catch (error: RuntimeException) { result.values["exception"] = error.message.orEmpty() }
                        result.data["dataHex"] = packed(data)
                    }
                }
            }
            "rgb16" -> {
                val data = ShortArray(left.size) { left[it].toShort() }
                fun replacement(): PixelRGB16 = PixelRGB16(right[0].toUShort(), right[0 + 1].toUShort(), right[0 + 2].toUShort())
                fun serial(value: PixelRGB16) = bytes(intArrayOf(value.red.toInt(), value.green.toInt(), value.blue.toInt()), 2)
                val original = ImageRGB16(w, h, data)
                val mutable = MutableImageRGB16(w, h, data)
                when (op) {
                    "freeze" -> {
                        val snapshot = ImageMutable.freezeImage(mutable)
                        assertNotSame(mutable.data, snapshot.data)
                        result.values["shared"] = mutable.data === snapshot.data
                        if (mode != "view") ImagePixelAccess.unsafeWritePixel(mutable.data, index, replacement())
                        result.data["imageHex"] = packed(snapshot.data); result.data["dataHex"] = packed(mutable.data)
                        result.values["width"] = snapshot.width; result.values["height"] = snapshot.height
                    }
                    "unsafe-freeze" -> {
                        val snapshot = ImageMutable.unsafeFreezeImage(mutable)
                        assertSame(mutable.data, snapshot.data)
                        result.values["shared"] = mutable.data === snapshot.data
                        result.data["dataHex"] = packed(snapshot.data)
                        result.values["width"] = snapshot.width; result.values["height"] = snapshot.height
                    }
                    "thaw", "unsafe-thaw" -> {
                        val before = packed(original.data)
                        val writable = if (op == "thaw") ImageMutable.thawImage(original) else ImageMutable.unsafeThawImage(original)
                        if (op == "thaw") assertNotSame(original.data, writable.data) else assertSame(original.data, writable.data)
                        result.values["shared"] = original.data === writable.data
                        if (mode != "view") ImagePixelAccess.unsafeWritePixel(writable.data, index, replacement())
                        result.data["sourceHex"] = if (op == "thaw") packed(original.data) else before
                        result.data["dataHex"] = packed(writable.data)
                        result.values["width"] = writable.width; result.values["height"] = writable.height
                    }
                    "create" -> {
                        val writable = ImageMutable.createMutableImage(w, h, replacement())
                        result.data["dataHex"] = packed(writable.data)
                        result.values["width"] = writable.width; result.values["height"] = writable.height
                    }
                    "new" -> {
                        val writable = ImageMutable.newMutableRGB16(w, h)
                        assertNotSame(data, writable.data); assertEquals(data.size, writable.data.size)
                        data.copyInto(writable.data) // Original oracle also initializes every component before observation.
                        result.data["dataHex"] = packed(writable.data)
                        result.values["width"] = writable.width; result.values["height"] = writable.height
                    }
                    else -> {
                        try {
                            val value = when (op) {
                                "pixel" -> serial(ImagePixelAccess.pixelAt(original, x, y))
                                "read" -> serial(ImagePixelAccess.readPixel(mutable, x, y))
                                "unsafe-pixel" -> serial(ImagePixelAccess.unsafePixelAtRGB16(data, index))
                                "unsafe-read" -> serial(ImagePixelAccess.unsafeReadPixel(mutable, index))
                                "write" -> { ImagePixelAccess.writePixel(mutable, x, y, replacement()); byteArrayOf() }
                                "unsafe-write" -> { ImagePixelAccess.unsafeWritePixel(data, index, replacement()); byteArrayOf() }
                                else -> error(op)
                            }
                            result.data["pixelHex"] = value
                        } catch (error: RuntimeException) { result.values["exception"] = error.message.orEmpty() }
                        result.data["dataHex"] = packed(data)
                    }
                }
            }
            "rgbf" -> {
                val data = FloatArray(left.size) { Float.fromBits(left[it]) }
                fun replacement(): PixelRGBF = PixelRGBF(Float.fromBits(right[0]), Float.fromBits(right[0 + 1]), Float.fromBits(right[0 + 2]))
                fun serial(value: PixelRGBF) = bytes(intArrayOf(value.red.toRawBits(), value.green.toRawBits(), value.blue.toRawBits()), 4)
                val original = ImageRGBF(w, h, data)
                val mutable = MutableImageRGBF(w, h, data)
                when (op) {
                    "freeze" -> {
                        val snapshot = ImageMutable.freezeImage(mutable)
                        assertNotSame(mutable.data, snapshot.data)
                        result.values["shared"] = mutable.data === snapshot.data
                        if (mode != "view") ImagePixelAccess.unsafeWritePixel(mutable.data, index, replacement())
                        result.data["imageHex"] = packed(snapshot.data); result.data["dataHex"] = packed(mutable.data)
                        result.values["width"] = snapshot.width; result.values["height"] = snapshot.height
                    }
                    "unsafe-freeze" -> {
                        val snapshot = ImageMutable.unsafeFreezeImage(mutable)
                        assertSame(mutable.data, snapshot.data)
                        result.values["shared"] = mutable.data === snapshot.data
                        result.data["dataHex"] = packed(snapshot.data)
                        result.values["width"] = snapshot.width; result.values["height"] = snapshot.height
                    }
                    "thaw", "unsafe-thaw" -> {
                        val before = packed(original.data)
                        val writable = if (op == "thaw") ImageMutable.thawImage(original) else ImageMutable.unsafeThawImage(original)
                        if (op == "thaw") assertNotSame(original.data, writable.data) else assertSame(original.data, writable.data)
                        result.values["shared"] = original.data === writable.data
                        if (mode != "view") ImagePixelAccess.unsafeWritePixel(writable.data, index, replacement())
                        result.data["sourceHex"] = if (op == "thaw") packed(original.data) else before
                        result.data["dataHex"] = packed(writable.data)
                        result.values["width"] = writable.width; result.values["height"] = writable.height
                    }
                    "create" -> {
                        val writable = ImageMutable.createMutableImage(w, h, replacement())
                        result.data["dataHex"] = packed(writable.data)
                        result.values["width"] = writable.width; result.values["height"] = writable.height
                    }
                    "new" -> {
                        val writable = ImageMutable.newMutableRGBF(w, h)
                        assertNotSame(data, writable.data); assertEquals(data.size, writable.data.size)
                        data.copyInto(writable.data) // Original oracle also initializes every component before observation.
                        result.data["dataHex"] = packed(writable.data)
                        result.values["width"] = writable.width; result.values["height"] = writable.height
                    }
                    else -> {
                        try {
                            val value = when (op) {
                                "pixel" -> serial(ImagePixelAccess.pixelAt(original, x, y))
                                "read" -> serial(ImagePixelAccess.readPixel(mutable, x, y))
                                "unsafe-pixel" -> serial(ImagePixelAccess.unsafePixelAtRGBF(data, index))
                                "unsafe-read" -> serial(ImagePixelAccess.unsafeReadPixel(mutable, index))
                                "write" -> { ImagePixelAccess.writePixel(mutable, x, y, replacement()); byteArrayOf() }
                                "unsafe-write" -> { ImagePixelAccess.unsafeWritePixel(data, index, replacement()); byteArrayOf() }
                                else -> error(op)
                            }
                            result.data["pixelHex"] = value
                        } catch (error: RuntimeException) { result.values["exception"] = error.message.orEmpty() }
                        result.data["dataHex"] = packed(data)
                    }
                }
            }
            "rgba8" -> {
                val data = ByteArray(left.size) { left[it].toByte() }
                fun replacement(): PixelRGBA8 = PixelRGBA8(right[0].toUByte(), right[0 + 1].toUByte(), right[0 + 2].toUByte(), right[0 + 3].toUByte())
                fun serial(value: PixelRGBA8) = bytes(intArrayOf(value.red.toInt(), value.green.toInt(), value.blue.toInt(), value.alpha.toInt()), 1)
                val original = ImageRGBA8(w, h, data)
                val mutable = MutableImageRGBA8(w, h, data)
                when (op) {
                    "freeze" -> {
                        val snapshot = ImageMutable.freezeImage(mutable)
                        assertNotSame(mutable.data, snapshot.data)
                        result.values["shared"] = mutable.data === snapshot.data
                        if (mode != "view") ImagePixelAccess.unsafeWritePixel(mutable.data, index, replacement())
                        result.data["imageHex"] = packed(snapshot.data); result.data["dataHex"] = packed(mutable.data)
                        result.values["width"] = snapshot.width; result.values["height"] = snapshot.height
                    }
                    "unsafe-freeze" -> {
                        val snapshot = ImageMutable.unsafeFreezeImage(mutable)
                        assertSame(mutable.data, snapshot.data)
                        result.values["shared"] = mutable.data === snapshot.data
                        result.data["dataHex"] = packed(snapshot.data)
                        result.values["width"] = snapshot.width; result.values["height"] = snapshot.height
                    }
                    "thaw", "unsafe-thaw" -> {
                        val before = packed(original.data)
                        val writable = if (op == "thaw") ImageMutable.thawImage(original) else ImageMutable.unsafeThawImage(original)
                        if (op == "thaw") assertNotSame(original.data, writable.data) else assertSame(original.data, writable.data)
                        result.values["shared"] = original.data === writable.data
                        if (mode != "view") ImagePixelAccess.unsafeWritePixel(writable.data, index, replacement())
                        result.data["sourceHex"] = if (op == "thaw") packed(original.data) else before
                        result.data["dataHex"] = packed(writable.data)
                        result.values["width"] = writable.width; result.values["height"] = writable.height
                    }
                    "create" -> {
                        val writable = ImageMutable.createMutableImage(w, h, replacement())
                        result.data["dataHex"] = packed(writable.data)
                        result.values["width"] = writable.width; result.values["height"] = writable.height
                    }
                    "new" -> {
                        val writable = ImageMutable.newMutableRGBA8(w, h)
                        assertNotSame(data, writable.data); assertEquals(data.size, writable.data.size)
                        data.copyInto(writable.data) // Original oracle also initializes every component before observation.
                        result.data["dataHex"] = packed(writable.data)
                        result.values["width"] = writable.width; result.values["height"] = writable.height
                    }
                    else -> {
                        try {
                            val value = when (op) {
                                "pixel" -> serial(ImagePixelAccess.pixelAt(original, x, y))
                                "read" -> serial(ImagePixelAccess.readPixel(mutable, x, y))
                                "unsafe-pixel" -> serial(ImagePixelAccess.unsafePixelAtRGBA8(data, index))
                                "unsafe-read" -> serial(ImagePixelAccess.unsafeReadPixel(mutable, index))
                                "write" -> { ImagePixelAccess.writePixel(mutable, x, y, replacement()); byteArrayOf() }
                                "unsafe-write" -> { ImagePixelAccess.unsafeWritePixel(data, index, replacement()); byteArrayOf() }
                                else -> error(op)
                            }
                            result.data["pixelHex"] = value
                        } catch (error: RuntimeException) { result.values["exception"] = error.message.orEmpty() }
                        result.data["dataHex"] = packed(data)
                    }
                }
            }
            "rgba16" -> {
                val data = ShortArray(left.size) { left[it].toShort() }
                fun replacement(): PixelRGBA16 = PixelRGBA16(right[0].toUShort(), right[0 + 1].toUShort(), right[0 + 2].toUShort(), right[0 + 3].toUShort())
                fun serial(value: PixelRGBA16) = bytes(intArrayOf(value.red.toInt(), value.green.toInt(), value.blue.toInt(), value.alpha.toInt()), 2)
                val original = ImageRGBA16(w, h, data)
                val mutable = MutableImageRGBA16(w, h, data)
                when (op) {
                    "freeze" -> {
                        val snapshot = ImageMutable.freezeImage(mutable)
                        assertNotSame(mutable.data, snapshot.data)
                        result.values["shared"] = mutable.data === snapshot.data
                        if (mode != "view") ImagePixelAccess.unsafeWritePixel(mutable.data, index, replacement())
                        result.data["imageHex"] = packed(snapshot.data); result.data["dataHex"] = packed(mutable.data)
                        result.values["width"] = snapshot.width; result.values["height"] = snapshot.height
                    }
                    "unsafe-freeze" -> {
                        val snapshot = ImageMutable.unsafeFreezeImage(mutable)
                        assertSame(mutable.data, snapshot.data)
                        result.values["shared"] = mutable.data === snapshot.data
                        result.data["dataHex"] = packed(snapshot.data)
                        result.values["width"] = snapshot.width; result.values["height"] = snapshot.height
                    }
                    "thaw", "unsafe-thaw" -> {
                        val before = packed(original.data)
                        val writable = if (op == "thaw") ImageMutable.thawImage(original) else ImageMutable.unsafeThawImage(original)
                        if (op == "thaw") assertNotSame(original.data, writable.data) else assertSame(original.data, writable.data)
                        result.values["shared"] = original.data === writable.data
                        if (mode != "view") ImagePixelAccess.unsafeWritePixel(writable.data, index, replacement())
                        result.data["sourceHex"] = if (op == "thaw") packed(original.data) else before
                        result.data["dataHex"] = packed(writable.data)
                        result.values["width"] = writable.width; result.values["height"] = writable.height
                    }
                    "create" -> {
                        val writable = ImageMutable.createMutableImage(w, h, replacement())
                        result.data["dataHex"] = packed(writable.data)
                        result.values["width"] = writable.width; result.values["height"] = writable.height
                    }
                    "new" -> {
                        val writable = ImageMutable.newMutableRGBA16(w, h)
                        assertNotSame(data, writable.data); assertEquals(data.size, writable.data.size)
                        data.copyInto(writable.data) // Original oracle also initializes every component before observation.
                        result.data["dataHex"] = packed(writable.data)
                        result.values["width"] = writable.width; result.values["height"] = writable.height
                    }
                    else -> {
                        try {
                            val value = when (op) {
                                "pixel" -> serial(ImagePixelAccess.pixelAt(original, x, y))
                                "read" -> serial(ImagePixelAccess.readPixel(mutable, x, y))
                                "unsafe-pixel" -> serial(ImagePixelAccess.unsafePixelAtRGBA16(data, index))
                                "unsafe-read" -> serial(ImagePixelAccess.unsafeReadPixel(mutable, index))
                                "write" -> { ImagePixelAccess.writePixel(mutable, x, y, replacement()); byteArrayOf() }
                                "unsafe-write" -> { ImagePixelAccess.unsafeWritePixel(data, index, replacement()); byteArrayOf() }
                                else -> error(op)
                            }
                            result.data["pixelHex"] = value
                        } catch (error: RuntimeException) { result.values["exception"] = error.message.orEmpty() }
                        result.data["dataHex"] = packed(data)
                    }
                }
            }
            "ycbcr8" -> {
                val data = ByteArray(left.size) { left[it].toByte() }
                fun replacement(): PixelYCbCr8 = PixelYCbCr8(right[0].toUByte(), right[0 + 1].toUByte(), right[0 + 2].toUByte())
                fun serial(value: PixelYCbCr8) = bytes(intArrayOf(value.luma.toInt(), value.cb.toInt(), value.cr.toInt()), 1)
                val original = ImageYCbCr8(w, h, data)
                val mutable = MutableImageYCbCr8(w, h, data)
                when (op) {
                    "freeze" -> {
                        val snapshot = ImageMutable.freezeImage(mutable)
                        assertNotSame(mutable.data, snapshot.data)
                        result.values["shared"] = mutable.data === snapshot.data
                        if (mode != "view") ImagePixelAccess.unsafeWritePixel(mutable.data, index, replacement())
                        result.data["imageHex"] = packed(snapshot.data); result.data["dataHex"] = packed(mutable.data)
                        result.values["width"] = snapshot.width; result.values["height"] = snapshot.height
                    }
                    "unsafe-freeze" -> {
                        val snapshot = ImageMutable.unsafeFreezeImage(mutable)
                        assertSame(mutable.data, snapshot.data)
                        result.values["shared"] = mutable.data === snapshot.data
                        result.data["dataHex"] = packed(snapshot.data)
                        result.values["width"] = snapshot.width; result.values["height"] = snapshot.height
                    }
                    "thaw", "unsafe-thaw" -> {
                        val before = packed(original.data)
                        val writable = if (op == "thaw") ImageMutable.thawImage(original) else ImageMutable.unsafeThawImage(original)
                        if (op == "thaw") assertNotSame(original.data, writable.data) else assertSame(original.data, writable.data)
                        result.values["shared"] = original.data === writable.data
                        if (mode != "view") ImagePixelAccess.unsafeWritePixel(writable.data, index, replacement())
                        result.data["sourceHex"] = if (op == "thaw") packed(original.data) else before
                        result.data["dataHex"] = packed(writable.data)
                        result.values["width"] = writable.width; result.values["height"] = writable.height
                    }
                    "create" -> {
                        val writable = ImageMutable.createMutableImage(w, h, replacement())
                        result.data["dataHex"] = packed(writable.data)
                        result.values["width"] = writable.width; result.values["height"] = writable.height
                    }
                    "new" -> {
                        val writable = ImageMutable.newMutableYCbCr8(w, h)
                        assertNotSame(data, writable.data); assertEquals(data.size, writable.data.size)
                        data.copyInto(writable.data) // Original oracle also initializes every component before observation.
                        result.data["dataHex"] = packed(writable.data)
                        result.values["width"] = writable.width; result.values["height"] = writable.height
                    }
                    else -> {
                        try {
                            val value = when (op) {
                                "pixel" -> serial(ImagePixelAccess.pixelAt(original, x, y))
                                "read" -> serial(ImagePixelAccess.readPixel(mutable, x, y))
                                "unsafe-pixel" -> serial(ImagePixelAccess.unsafePixelAtYCbCr8(data, index))
                                "unsafe-read" -> serial(ImagePixelAccess.unsafeReadPixel(mutable, index))
                                "write" -> { ImagePixelAccess.writePixel(mutable, x, y, replacement()); byteArrayOf() }
                                "unsafe-write" -> { ImagePixelAccess.unsafeWritePixel(data, index, replacement()); byteArrayOf() }
                                else -> error(op)
                            }
                            result.data["pixelHex"] = value
                        } catch (error: RuntimeException) { result.values["exception"] = error.message.orEmpty() }
                        result.data["dataHex"] = packed(data)
                    }
                }
            }
            "ycbcrk8" -> {
                val data = ByteArray(left.size) { left[it].toByte() }
                fun replacement(): PixelYCbCrK8 = PixelYCbCrK8(right[0].toUByte(), right[0 + 1].toUByte(), right[0 + 2].toUByte(), right[0 + 3].toUByte())
                fun serial(value: PixelYCbCrK8) = bytes(intArrayOf(value.luma.toInt(), value.cb.toInt(), value.cr.toInt(), value.black.toInt()), 1)
                val original = ImageYCbCrK8(w, h, data)
                val mutable = MutableImageYCbCrK8(w, h, data)
                when (op) {
                    "freeze" -> {
                        val snapshot = ImageMutable.freezeImage(mutable)
                        assertNotSame(mutable.data, snapshot.data)
                        result.values["shared"] = mutable.data === snapshot.data
                        if (mode != "view") ImagePixelAccess.unsafeWritePixel(mutable.data, index, replacement())
                        result.data["imageHex"] = packed(snapshot.data); result.data["dataHex"] = packed(mutable.data)
                        result.values["width"] = snapshot.width; result.values["height"] = snapshot.height
                    }
                    "unsafe-freeze" -> {
                        val snapshot = ImageMutable.unsafeFreezeImage(mutable)
                        assertSame(mutable.data, snapshot.data)
                        result.values["shared"] = mutable.data === snapshot.data
                        result.data["dataHex"] = packed(snapshot.data)
                        result.values["width"] = snapshot.width; result.values["height"] = snapshot.height
                    }
                    "thaw", "unsafe-thaw" -> {
                        val before = packed(original.data)
                        val writable = if (op == "thaw") ImageMutable.thawImage(original) else ImageMutable.unsafeThawImage(original)
                        if (op == "thaw") assertNotSame(original.data, writable.data) else assertSame(original.data, writable.data)
                        result.values["shared"] = original.data === writable.data
                        if (mode != "view") ImagePixelAccess.unsafeWritePixel(writable.data, index, replacement())
                        result.data["sourceHex"] = if (op == "thaw") packed(original.data) else before
                        result.data["dataHex"] = packed(writable.data)
                        result.values["width"] = writable.width; result.values["height"] = writable.height
                    }
                    "create" -> {
                        val writable = ImageMutable.createMutableImage(w, h, replacement())
                        result.data["dataHex"] = packed(writable.data)
                        result.values["width"] = writable.width; result.values["height"] = writable.height
                    }
                    "new" -> {
                        val writable = ImageMutable.newMutableYCbCrK8(w, h)
                        assertNotSame(data, writable.data); assertEquals(data.size, writable.data.size)
                        data.copyInto(writable.data) // Original oracle also initializes every component before observation.
                        result.data["dataHex"] = packed(writable.data)
                        result.values["width"] = writable.width; result.values["height"] = writable.height
                    }
                    else -> {
                        try {
                            val value = when (op) {
                                "pixel" -> serial(ImagePixelAccess.pixelAt(original, x, y))
                                "read" -> serial(ImagePixelAccess.readPixel(mutable, x, y))
                                "unsafe-pixel" -> serial(ImagePixelAccess.unsafePixelAtYCbCrK8(data, index))
                                "unsafe-read" -> serial(ImagePixelAccess.unsafeReadPixel(mutable, index))
                                "write" -> { ImagePixelAccess.writePixel(mutable, x, y, replacement()); byteArrayOf() }
                                "unsafe-write" -> { ImagePixelAccess.unsafeWritePixel(data, index, replacement()); byteArrayOf() }
                                else -> error(op)
                            }
                            result.data["pixelHex"] = value
                        } catch (error: RuntimeException) { result.values["exception"] = error.message.orEmpty() }
                        result.data["dataHex"] = packed(data)
                    }
                }
            }
            "cmyk8" -> {
                val data = ByteArray(left.size) { left[it].toByte() }
                fun replacement(): PixelCMYK8 = PixelCMYK8(right[0].toUByte(), right[0 + 1].toUByte(), right[0 + 2].toUByte(), right[0 + 3].toUByte())
                fun serial(value: PixelCMYK8) = bytes(intArrayOf(value.cyan.toInt(), value.magenta.toInt(), value.yellow.toInt(), value.black.toInt()), 1)
                val original = ImageCMYK8(w, h, data)
                val mutable = MutableImageCMYK8(w, h, data)
                when (op) {
                    "freeze" -> {
                        val snapshot = ImageMutable.freezeImage(mutable)
                        assertNotSame(mutable.data, snapshot.data)
                        result.values["shared"] = mutable.data === snapshot.data
                        if (mode != "view") ImagePixelAccess.unsafeWritePixel(mutable.data, index, replacement())
                        result.data["imageHex"] = packed(snapshot.data); result.data["dataHex"] = packed(mutable.data)
                        result.values["width"] = snapshot.width; result.values["height"] = snapshot.height
                    }
                    "unsafe-freeze" -> {
                        val snapshot = ImageMutable.unsafeFreezeImage(mutable)
                        assertSame(mutable.data, snapshot.data)
                        result.values["shared"] = mutable.data === snapshot.data
                        result.data["dataHex"] = packed(snapshot.data)
                        result.values["width"] = snapshot.width; result.values["height"] = snapshot.height
                    }
                    "thaw", "unsafe-thaw" -> {
                        val before = packed(original.data)
                        val writable = if (op == "thaw") ImageMutable.thawImage(original) else ImageMutable.unsafeThawImage(original)
                        if (op == "thaw") assertNotSame(original.data, writable.data) else assertSame(original.data, writable.data)
                        result.values["shared"] = original.data === writable.data
                        if (mode != "view") ImagePixelAccess.unsafeWritePixel(writable.data, index, replacement())
                        result.data["sourceHex"] = if (op == "thaw") packed(original.data) else before
                        result.data["dataHex"] = packed(writable.data)
                        result.values["width"] = writable.width; result.values["height"] = writable.height
                    }
                    "create" -> {
                        val writable = ImageMutable.createMutableImage(w, h, replacement())
                        result.data["dataHex"] = packed(writable.data)
                        result.values["width"] = writable.width; result.values["height"] = writable.height
                    }
                    "new" -> {
                        val writable = ImageMutable.newMutableCMYK8(w, h)
                        assertNotSame(data, writable.data); assertEquals(data.size, writable.data.size)
                        data.copyInto(writable.data) // Original oracle also initializes every component before observation.
                        result.data["dataHex"] = packed(writable.data)
                        result.values["width"] = writable.width; result.values["height"] = writable.height
                    }
                    else -> {
                        try {
                            val value = when (op) {
                                "pixel" -> serial(ImagePixelAccess.pixelAt(original, x, y))
                                "read" -> serial(ImagePixelAccess.readPixel(mutable, x, y))
                                "unsafe-pixel" -> serial(ImagePixelAccess.unsafePixelAtCMYK8(data, index))
                                "unsafe-read" -> serial(ImagePixelAccess.unsafeReadPixel(mutable, index))
                                "write" -> { ImagePixelAccess.writePixel(mutable, x, y, replacement()); byteArrayOf() }
                                "unsafe-write" -> { ImagePixelAccess.unsafeWritePixel(data, index, replacement()); byteArrayOf() }
                                else -> error(op)
                            }
                            result.data["pixelHex"] = value
                        } catch (error: RuntimeException) { result.values["exception"] = error.message.orEmpty() }
                        result.data["dataHex"] = packed(data)
                    }
                }
            }
            "cmyk16" -> {
                val data = ShortArray(left.size) { left[it].toShort() }
                fun replacement(): PixelCMYK16 = PixelCMYK16(right[0].toUShort(), right[0 + 1].toUShort(), right[0 + 2].toUShort(), right[0 + 3].toUShort())
                fun serial(value: PixelCMYK16) = bytes(intArrayOf(value.cyan.toInt(), value.magenta.toInt(), value.yellow.toInt(), value.black.toInt()), 2)
                val original = ImageCMYK16(w, h, data)
                val mutable = MutableImageCMYK16(w, h, data)
                when (op) {
                    "freeze" -> {
                        val snapshot = ImageMutable.freezeImage(mutable)
                        assertNotSame(mutable.data, snapshot.data)
                        result.values["shared"] = mutable.data === snapshot.data
                        if (mode != "view") ImagePixelAccess.unsafeWritePixel(mutable.data, index, replacement())
                        result.data["imageHex"] = packed(snapshot.data); result.data["dataHex"] = packed(mutable.data)
                        result.values["width"] = snapshot.width; result.values["height"] = snapshot.height
                    }
                    "unsafe-freeze" -> {
                        val snapshot = ImageMutable.unsafeFreezeImage(mutable)
                        assertSame(mutable.data, snapshot.data)
                        result.values["shared"] = mutable.data === snapshot.data
                        result.data["dataHex"] = packed(snapshot.data)
                        result.values["width"] = snapshot.width; result.values["height"] = snapshot.height
                    }
                    "thaw", "unsafe-thaw" -> {
                        val before = packed(original.data)
                        val writable = if (op == "thaw") ImageMutable.thawImage(original) else ImageMutable.unsafeThawImage(original)
                        if (op == "thaw") assertNotSame(original.data, writable.data) else assertSame(original.data, writable.data)
                        result.values["shared"] = original.data === writable.data
                        if (mode != "view") ImagePixelAccess.unsafeWritePixel(writable.data, index, replacement())
                        result.data["sourceHex"] = if (op == "thaw") packed(original.data) else before
                        result.data["dataHex"] = packed(writable.data)
                        result.values["width"] = writable.width; result.values["height"] = writable.height
                    }
                    "create" -> {
                        val writable = ImageMutable.createMutableImage(w, h, replacement())
                        result.data["dataHex"] = packed(writable.data)
                        result.values["width"] = writable.width; result.values["height"] = writable.height
                    }
                    "new" -> {
                        val writable = ImageMutable.newMutableCMYK16(w, h)
                        assertNotSame(data, writable.data); assertEquals(data.size, writable.data.size)
                        data.copyInto(writable.data) // Original oracle also initializes every component before observation.
                        result.data["dataHex"] = packed(writable.data)
                        result.values["width"] = writable.width; result.values["height"] = writable.height
                    }
                    else -> {
                        try {
                            val value = when (op) {
                                "pixel" -> serial(ImagePixelAccess.pixelAt(original, x, y))
                                "read" -> serial(ImagePixelAccess.readPixel(mutable, x, y))
                                "unsafe-pixel" -> serial(ImagePixelAccess.unsafePixelAtCMYK16(data, index))
                                "unsafe-read" -> serial(ImagePixelAccess.unsafeReadPixel(mutable, index))
                                "write" -> { ImagePixelAccess.writePixel(mutable, x, y, replacement()); byteArrayOf() }
                                "unsafe-write" -> { ImagePixelAccess.unsafeWritePixel(data, index, replacement()); byteArrayOf() }
                                else -> error(op)
                            }
                            result.data["pixelHex"] = value
                        } catch (error: RuntimeException) { result.values["exception"] = error.message.orEmpty() }
                        result.data["dataHex"] = packed(data)
                    }
                }
            }
            else -> error(type)
        }
        return result
    }

    @TestFactory fun `original Pixel methods and mutable image lifecycle`(): Stream<DynamicTest> {
        val reader = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/images/image-pixel.jsonl.gz"))).bufferedReader()
        return reader.lineSequence().drop(1).map { line ->
            val name = Json.parseToJsonElement(line).jsonObject.getValue("name").jsonPrimitive.content
            DynamicTest.dynamicTest(name) {
                val c = Json.parseToJsonElement(line).jsonObject
                fun string(key: String) = c.getValue(key).jsonPrimitive.content
                fun input(key: String) = c.getValue(key).jsonArray.map { it.jsonPrimitive.long.toInt() }.toIntArray()
                val op = string("op"); val type = string("type"); val mode = string("mode")
                val (left, right) = if (string("kind") == "values") input("left") to input("right") else generated(type, string("kind"))
                val args = c.getValue("params").jsonArray.map { it.jsonPrimitive.long }.toLongArray().copyOf(5)
                val observed = try {
                    if (op in setOf("mix", "mix-alpha", "map", "meta")) pure(op, type, mode, left, right) else access(op, type, mode, args, left, right)
                } catch (error: RuntimeException) { Observation(values = mutableMapOf("exception" to error.message.orEmpty())) }
                val expected = c.getValue("expected").jsonObject
                for (key in listOf("exception", "index", "components", "width", "height", "shared")) {
                    if (key !in expected) { assertFalse(key in observed.values, "unexpected $key"); continue }
                    if (key == "shared" && left.isEmpty()) continue // Native zero-length allocation addresses do not establish sharing.
                    val actual = observed.values[key]
                    when (key) {
                        "exception" -> assertEquals(expected.getValue(key).jsonPrimitive.content, actual)
                        "shared" -> assertEquals(expected.getValue(key).jsonPrimitive.boolean, actual)
                        else -> assertEquals(expected.getValue(key).jsonPrimitive.long, (actual as Number).toLong())
                    }
                }
                val hashes = expected.getValue("hashes").jsonObject
                assertEquals(hashes.keys, observed.data.keys)
                for ((key, expectedHash) in hashes) {
                    val data = observed.data.getValue(key)
                    assertEquals(expected.getValue("lengths").jsonObject.getValue(key).jsonPrimitive.int, data.size)
                    if (key == "hex" && "canonicalNaNSha256" in expected) {
                        val normalized = canonical(data)
                        assertEquals(expected.getValue("canonicalNaNSha256").jsonPrimitive.content, hash(normalized))
                        expected[key]?.let { assertContentEquals(canonical(it.jsonPrimitive.content.hexToByteArray()), normalized) }
                    } else {
                        assertEquals(expectedHash.jsonPrimitive.content, hash(data))
                        expected[key]?.let { assertContentEquals(it.jsonPrimitive.content.hexToByteArray(), data) }
                    }
                }
            }
        }.asStream().onClose { reader.close() }
    }
}
