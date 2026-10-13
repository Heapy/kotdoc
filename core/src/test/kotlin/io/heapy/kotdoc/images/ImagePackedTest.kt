package io.heapy.kotdoc.images

import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.security.MessageDigest
import java.util.stream.Stream
import java.util.zip.GZIPInputStream
import kotlin.streams.asStream
import kotlin.test.*

class ImagePackedTest {
    private data class Observation(val data: MutableMap<String, ByteArray> = mutableMapOf(), var exception: String? = null)
    private fun word(value: ULong, bytes: Int) = ByteArray(bytes) { (value shr (it * 8)).toByte() }
    private fun packed(data: ByteArray) = data.copyOf()
    private fun packed(data: ShortArray) = ByteArray(data.size * 2) { (data[it / 2].toInt() ushr ((it % 2) * 8)).toByte() }
    private fun packed(data: IntArray) = ByteArray(data.size * 4) { (data[it / 4] ushr ((it % 4) * 8)).toByte() }
    private fun packed(data: FloatArray) = ByteArray(data.size * 4) { (data[it / 4].toRawBits() ushr ((it % 4) * 8)).toByte() }
    private fun dimensions(type: String): Triple<Int, Int, Int> = when (type) {
        "y8" -> Triple(1, 8, 8)
        "y16" -> Triple(1, 16, 16)
        "y32" -> Triple(1, 32, 32)
        "yf" -> Triple(1, 32, 32)
        "ya8" -> Triple(2, 8, 16)
        "ya16" -> Triple(2, 16, 32)
        "rgba8" -> Triple(4, 8, 32)
        "rgba16" -> Triple(4, 16, 64)
        "cmyk8" -> Triple(4, 8, 32)
        "cmyk16" -> Triple(4, 16, 64)
        else -> error(type)
    }
    private fun generated(type: String, kind: String): List<ULong> {
        val (components, bits, packedBits) = dimensions(type)
        if (kind == "packed-domain") return (0..<(1 shl packedBits)).map { it.toULong() }
        val limit = 1 shl bits
        return (0..<limit).flatMap { i -> (0..<components).map { c -> ((i * (2 * c + 1) + c * 257) % limit).toULong() } }
    }
    private fun pure(op: String, type: String, values: List<ULong>): Observation {
        val (n, bits, packedBits) = dimensions(type)
        val count = if (op == "pack") values.size / n else values.size
        val words = ByteArray(count * packedBits / 8); val pixels = ByteArray(count * n * bits / 8)
        for (i in 0..<count) {
            val src = i * n
            when (type) {
                "y8" -> {
                    val pixel: UByte
                    val value: UByte
                    if (op == "pack") {
                        val input: UByte = values[src].toUByte()
                        value = ImagePacked.packPixel(input)
                        pixel = ImagePacked.unpackPixelY8(value)
                    } else {
                        pixel = ImagePacked.unpackPixelY8(values[i].toUByte())
                        value = ImagePacked.packPixel(pixel)
                    }
                    word(value.toULong(), packedBits / 8).copyInto(words, i * packedBits / 8)
                    val fields = listOf(pixel.toULong())
                    fields.forEachIndexed { c, field -> word(field, bits / 8).copyInto(pixels, (i * n + c) * bits / 8) }
                }
                "y16" -> {
                    val pixel: UShort
                    val value: UShort
                    if (op == "pack") {
                        val input: UShort = values[src].toUShort()
                        value = ImagePacked.packPixel(input)
                        pixel = ImagePacked.unpackPixelY16(value)
                    } else {
                        pixel = ImagePacked.unpackPixelY16(values[i].toUShort())
                        value = ImagePacked.packPixel(pixel)
                    }
                    word(value.toULong(), packedBits / 8).copyInto(words, i * packedBits / 8)
                    val fields = listOf(pixel.toULong())
                    fields.forEachIndexed { c, field -> word(field, bits / 8).copyInto(pixels, (i * n + c) * bits / 8) }
                }
                "y32" -> {
                    val pixel: UInt
                    val value: UInt
                    if (op == "pack") {
                        val input: UInt = values[src].toUInt()
                        value = ImagePacked.packPixel(input)
                        pixel = ImagePacked.unpackPixelY32(value)
                    } else {
                        pixel = ImagePacked.unpackPixelY32(values[i].toUInt())
                        value = ImagePacked.packPixel(pixel)
                    }
                    word(value.toULong(), packedBits / 8).copyInto(words, i * packedBits / 8)
                    val fields = listOf(pixel.toULong())
                    fields.forEachIndexed { c, field -> word(field, bits / 8).copyInto(pixels, (i * n + c) * bits / 8) }
                }
                "yf" -> {
                    val pixel: Float
                    val value: Float
                    if (op == "pack") {
                        val input: Float = Float.fromBits(values[src].toInt())
                        value = ImagePacked.packPixel(input)
                        pixel = ImagePacked.unpackPixelYF(value)
                    } else {
                        pixel = ImagePacked.unpackPixelYF(Float.fromBits(values[i].toInt()))
                        value = ImagePacked.packPixel(pixel)
                    }
                    word(value.toRawBits().toUInt().toULong(), packedBits / 8).copyInto(words, i * packedBits / 8)
                    val fields = listOf(pixel.toRawBits().toUInt().toULong())
                    fields.forEachIndexed { c, field -> word(field, bits / 8).copyInto(pixels, (i * n + c) * bits / 8) }
                }
                "ya8" -> {
                    val pixel: PixelYA8
                    val value: UShort
                    if (op == "pack") {
                        val input: PixelYA8 = PixelYA8(values[src].toUByte(), values[src + 1].toUByte())
                        value = ImagePacked.packPixel(input)
                        pixel = ImagePacked.unpackPixelYA8(value)
                    } else {
                        pixel = ImagePacked.unpackPixelYA8(values[i].toUShort())
                        value = ImagePacked.packPixel(pixel)
                    }
                    word(value.toULong(), packedBits / 8).copyInto(words, i * packedBits / 8)
                    val fields = listOf(pixel.luma.toULong(), pixel.alpha.toULong())
                    fields.forEachIndexed { c, field -> word(field, bits / 8).copyInto(pixels, (i * n + c) * bits / 8) }
                }
                "ya16" -> {
                    val pixel: PixelYA16
                    val value: UInt
                    if (op == "pack") {
                        val input: PixelYA16 = PixelYA16(values[src].toUShort(), values[src + 1].toUShort())
                        value = ImagePacked.packPixel(input)
                        pixel = ImagePacked.unpackPixelYA16(value)
                    } else {
                        pixel = ImagePacked.unpackPixelYA16(values[i].toUInt())
                        value = ImagePacked.packPixel(pixel)
                    }
                    word(value.toULong(), packedBits / 8).copyInto(words, i * packedBits / 8)
                    val fields = listOf(pixel.luma.toULong(), pixel.alpha.toULong())
                    fields.forEachIndexed { c, field -> word(field, bits / 8).copyInto(pixels, (i * n + c) * bits / 8) }
                }
                "rgba8" -> {
                    val pixel: PixelRGBA8
                    val value: UInt
                    if (op == "pack") {
                        val input: PixelRGBA8 = PixelRGBA8(values[src].toUByte(), values[src + 1].toUByte(), values[src + 2].toUByte(), values[src + 3].toUByte())
                        value = ImagePacked.packPixel(input)
                        pixel = ImagePacked.unpackPixelRGBA8(value)
                    } else {
                        pixel = ImagePacked.unpackPixelRGBA8(values[i].toUInt())
                        value = ImagePacked.packPixel(pixel)
                    }
                    word(value.toULong(), packedBits / 8).copyInto(words, i * packedBits / 8)
                    val fields = listOf(pixel.red.toULong(), pixel.green.toULong(), pixel.blue.toULong(), pixel.alpha.toULong())
                    fields.forEachIndexed { c, field -> word(field, bits / 8).copyInto(pixels, (i * n + c) * bits / 8) }
                }
                "rgba16" -> {
                    val pixel: PixelRGBA16
                    val value: ULong
                    if (op == "pack") {
                        val input: PixelRGBA16 = PixelRGBA16(values[src].toUShort(), values[src + 1].toUShort(), values[src + 2].toUShort(), values[src + 3].toUShort())
                        value = ImagePacked.packPixel(input)
                        pixel = ImagePacked.unpackPixelRGBA16(value)
                    } else {
                        pixel = ImagePacked.unpackPixelRGBA16(values[i])
                        value = ImagePacked.packPixel(pixel)
                    }
                    word(value, packedBits / 8).copyInto(words, i * packedBits / 8)
                    val fields = listOf(pixel.red.toULong(), pixel.green.toULong(), pixel.blue.toULong(), pixel.alpha.toULong())
                    fields.forEachIndexed { c, field -> word(field, bits / 8).copyInto(pixels, (i * n + c) * bits / 8) }
                }
                "cmyk8" -> {
                    val pixel: PixelCMYK8
                    val value: UInt
                    if (op == "pack") {
                        val input: PixelCMYK8 = PixelCMYK8(values[src].toUByte(), values[src + 1].toUByte(), values[src + 2].toUByte(), values[src + 3].toUByte())
                        value = ImagePacked.packPixel(input)
                        pixel = ImagePacked.unpackPixelCMYK8(value)
                    } else {
                        pixel = ImagePacked.unpackPixelCMYK8(values[i].toUInt())
                        value = ImagePacked.packPixel(pixel)
                    }
                    word(value.toULong(), packedBits / 8).copyInto(words, i * packedBits / 8)
                    val fields = listOf(pixel.cyan.toULong(), pixel.magenta.toULong(), pixel.yellow.toULong(), pixel.black.toULong())
                    fields.forEachIndexed { c, field -> word(field, bits / 8).copyInto(pixels, (i * n + c) * bits / 8) }
                }
                "cmyk16" -> {
                    val pixel: PixelCMYK16
                    val value: ULong
                    if (op == "pack") {
                        val input: PixelCMYK16 = PixelCMYK16(values[src].toUShort(), values[src + 1].toUShort(), values[src + 2].toUShort(), values[src + 3].toUShort())
                        value = ImagePacked.packPixel(input)
                        pixel = ImagePacked.unpackPixelCMYK16(value)
                    } else {
                        pixel = ImagePacked.unpackPixelCMYK16(values[i])
                        value = ImagePacked.packPixel(pixel)
                    }
                    word(value, packedBits / 8).copyInto(words, i * packedBits / 8)
                    val fields = listOf(pixel.cyan.toULong(), pixel.magenta.toULong(), pixel.yellow.toULong(), pixel.black.toULong())
                    fields.forEachIndexed { c, field -> word(field, bits / 8).copyInto(pixels, (i * n + c) * bits / 8) }
                }
                else -> error(type)
            }
        }
        return Observation(mutableMapOf("packedHex" to words, "pixelHex" to pixels))
    }
    private fun memory(op: String, type: String, params: LongArray, storage: List<ULong>, values: List<ULong>): Observation {
        val out = Observation()
        val view = ImageBufferView(params[2], params[3])
        val index = params[4]; val count = params[5]
        when (type) {
            "y8" -> {
                val data = ByteArray(storage.size) { storage[it].toByte() }
                val image = MutableImageY8(params[0].toInt(), params[1].toInt(), data)
                fun pixel(): UByte = values[0].toUByte()
                val complete = view.offset == 0L && view.length == data.size.toLong()
                try {
                    val result = when (op) {
                        "read" -> {
                            val p = if (complete) ImagePacked.readPackedPixelAt(image, index) else ImagePacked.readPackedPixelAt(image, index, view)
                            val fields = listOf(p.toULong())
                            val result = ByteArray(fields.size * 1)
                            fields.forEachIndexed { c, field -> word(field, 1).copyInto(result, c * 1) }
                            result
                        }
                        "write" -> { if (complete) ImagePacked.writePackedPixelAt(image, index, pixel()) else ImagePacked.writePackedPixelAt(image, index, pixel(), view); byteArrayOf() }
                        "fill" -> { if (complete) ImagePacked.fillImageWith(image, pixel()) else ImagePacked.fillImageWith(image, pixel(), view); byteArrayOf() }
                        "between" -> { if (complete) ImagePacked.unsafeWritePixelBetweenAt(image, pixel(), index, count) else ImagePacked.unsafeWritePixelBetweenAt(image, pixel(), index, count, view); byteArrayOf() }
                        else -> error(op)
                    }
                    out.data["pixelHex"] = result
                } catch (error: RuntimeException) { out.exception = error.message.orEmpty() }
                out.data["dataHex"] = packed(data)
            }
            "y16" -> {
                val data = ShortArray(storage.size) { storage[it].toShort() }
                val image = MutableImageY16(params[0].toInt(), params[1].toInt(), data)
                fun pixel(): UShort = values[0].toUShort()
                val complete = view.offset == 0L && view.length == data.size.toLong()
                try {
                    val result = when (op) {
                        "read" -> {
                            val p = if (complete) ImagePacked.readPackedPixelAt(image, index) else ImagePacked.readPackedPixelAt(image, index, view)
                            val fields = listOf(p.toULong())
                            val result = ByteArray(fields.size * 2)
                            fields.forEachIndexed { c, field -> word(field, 2).copyInto(result, c * 2) }
                            result
                        }
                        "write" -> { if (complete) ImagePacked.writePackedPixelAt(image, index, pixel()) else ImagePacked.writePackedPixelAt(image, index, pixel(), view); byteArrayOf() }
                        "fill" -> { if (complete) ImagePacked.fillImageWith(image, pixel()) else ImagePacked.fillImageWith(image, pixel(), view); byteArrayOf() }
                        "between" -> { if (complete) ImagePacked.unsafeWritePixelBetweenAt(image, pixel(), index, count) else ImagePacked.unsafeWritePixelBetweenAt(image, pixel(), index, count, view); byteArrayOf() }
                        else -> error(op)
                    }
                    out.data["pixelHex"] = result
                } catch (error: RuntimeException) { out.exception = error.message.orEmpty() }
                out.data["dataHex"] = packed(data)
            }
            "y32" -> {
                val data = IntArray(storage.size) { storage[it].toInt() }
                val image = MutableImageY32(params[0].toInt(), params[1].toInt(), data)
                fun pixel(): UInt = values[0].toUInt()
                val complete = view.offset == 0L && view.length == data.size.toLong()
                try {
                    val result = when (op) {
                        "read" -> {
                            val p = if (complete) ImagePacked.readPackedPixelAt(image, index) else ImagePacked.readPackedPixelAt(image, index, view)
                            val fields = listOf(p.toULong())
                            val result = ByteArray(fields.size * 4)
                            fields.forEachIndexed { c, field -> word(field, 4).copyInto(result, c * 4) }
                            result
                        }
                        "write" -> { if (complete) ImagePacked.writePackedPixelAt(image, index, pixel()) else ImagePacked.writePackedPixelAt(image, index, pixel(), view); byteArrayOf() }
                        "fill" -> { if (complete) ImagePacked.fillImageWith(image, pixel()) else ImagePacked.fillImageWith(image, pixel(), view); byteArrayOf() }
                        "between" -> { if (complete) ImagePacked.unsafeWritePixelBetweenAt(image, pixel(), index, count) else ImagePacked.unsafeWritePixelBetweenAt(image, pixel(), index, count, view); byteArrayOf() }
                        else -> error(op)
                    }
                    out.data["pixelHex"] = result
                } catch (error: RuntimeException) { out.exception = error.message.orEmpty() }
                out.data["dataHex"] = packed(data)
            }
            "yf" -> {
                val data = FloatArray(storage.size) { Float.fromBits(storage[it].toInt()) }
                val image = MutableImageYF(params[0].toInt(), params[1].toInt(), data)
                fun pixel(): Float = Float.fromBits(values[0].toInt())
                val complete = view.offset == 0L && view.length == data.size.toLong()
                try {
                    val result = when (op) {
                        "read" -> {
                            val p = if (complete) ImagePacked.readPackedPixelAt(image, index) else ImagePacked.readPackedPixelAt(image, index, view)
                            val fields = listOf(p.toRawBits().toUInt().toULong())
                            val result = ByteArray(fields.size * 4)
                            fields.forEachIndexed { c, field -> word(field, 4).copyInto(result, c * 4) }
                            result
                        }
                        "write" -> { if (complete) ImagePacked.writePackedPixelAt(image, index, pixel()) else ImagePacked.writePackedPixelAt(image, index, pixel(), view); byteArrayOf() }
                        "fill" -> { if (complete) ImagePacked.fillImageWith(image, pixel()) else ImagePacked.fillImageWith(image, pixel(), view); byteArrayOf() }
                        "between" -> { if (complete) ImagePacked.unsafeWritePixelBetweenAt(image, pixel(), index, count) else ImagePacked.unsafeWritePixelBetweenAt(image, pixel(), index, count, view); byteArrayOf() }
                        else -> error(op)
                    }
                    out.data["pixelHex"] = result
                } catch (error: RuntimeException) { out.exception = error.message.orEmpty() }
                out.data["dataHex"] = packed(data)
            }
            "ya8" -> {
                val data = ByteArray(storage.size) { storage[it].toByte() }
                val image = MutableImageYA8(params[0].toInt(), params[1].toInt(), data)
                fun pixel(): PixelYA8 = PixelYA8(values[0].toUByte(), values[0 + 1].toUByte())
                val complete = view.offset == 0L && view.length == data.size.toLong()
                try {
                    val result = when (op) {
                        "read" -> {
                            val p = if (complete) ImagePacked.readPackedPixelAt(image, index) else ImagePacked.readPackedPixelAt(image, index, view)
                            val fields = listOf(p.luma.toULong(), p.alpha.toULong())
                            val result = ByteArray(fields.size * 1)
                            fields.forEachIndexed { c, field -> word(field, 1).copyInto(result, c * 1) }
                            result
                        }
                        "write" -> { if (complete) ImagePacked.writePackedPixelAt(image, index, pixel()) else ImagePacked.writePackedPixelAt(image, index, pixel(), view); byteArrayOf() }
                        "fill" -> { if (complete) ImagePacked.fillImageWith(image, pixel()) else ImagePacked.fillImageWith(image, pixel(), view); byteArrayOf() }
                        "between" -> { if (complete) ImagePacked.unsafeWritePixelBetweenAt(image, pixel(), index, count) else ImagePacked.unsafeWritePixelBetweenAt(image, pixel(), index, count, view); byteArrayOf() }
                        else -> error(op)
                    }
                    out.data["pixelHex"] = result
                } catch (error: RuntimeException) { out.exception = error.message.orEmpty() }
                out.data["dataHex"] = packed(data)
            }
            "ya16" -> {
                val data = ShortArray(storage.size) { storage[it].toShort() }
                val image = MutableImageYA16(params[0].toInt(), params[1].toInt(), data)
                fun pixel(): PixelYA16 = PixelYA16(values[0].toUShort(), values[0 + 1].toUShort())
                val complete = view.offset == 0L && view.length == data.size.toLong()
                try {
                    val result = when (op) {
                        "read" -> {
                            val p = if (complete) ImagePacked.readPackedPixelAt(image, index) else ImagePacked.readPackedPixelAt(image, index, view)
                            val fields = listOf(p.luma.toULong(), p.alpha.toULong())
                            val result = ByteArray(fields.size * 2)
                            fields.forEachIndexed { c, field -> word(field, 2).copyInto(result, c * 2) }
                            result
                        }
                        "write" -> { if (complete) ImagePacked.writePackedPixelAt(image, index, pixel()) else ImagePacked.writePackedPixelAt(image, index, pixel(), view); byteArrayOf() }
                        "fill" -> { if (complete) ImagePacked.fillImageWith(image, pixel()) else ImagePacked.fillImageWith(image, pixel(), view); byteArrayOf() }
                        "between" -> { if (complete) ImagePacked.unsafeWritePixelBetweenAt(image, pixel(), index, count) else ImagePacked.unsafeWritePixelBetweenAt(image, pixel(), index, count, view); byteArrayOf() }
                        else -> error(op)
                    }
                    out.data["pixelHex"] = result
                } catch (error: RuntimeException) { out.exception = error.message.orEmpty() }
                out.data["dataHex"] = packed(data)
            }
            "rgba8" -> {
                val data = ByteArray(storage.size) { storage[it].toByte() }
                val image = MutableImageRGBA8(params[0].toInt(), params[1].toInt(), data)
                fun pixel(): PixelRGBA8 = PixelRGBA8(values[0].toUByte(), values[0 + 1].toUByte(), values[0 + 2].toUByte(), values[0 + 3].toUByte())
                val complete = view.offset == 0L && view.length == data.size.toLong()
                try {
                    val result = when (op) {
                        "read" -> {
                            val p = if (complete) ImagePacked.readPackedPixelAt(image, index) else ImagePacked.readPackedPixelAt(image, index, view)
                            val fields = listOf(p.red.toULong(), p.green.toULong(), p.blue.toULong(), p.alpha.toULong())
                            val result = ByteArray(fields.size * 1)
                            fields.forEachIndexed { c, field -> word(field, 1).copyInto(result, c * 1) }
                            result
                        }
                        "write" -> { if (complete) ImagePacked.writePackedPixelAt(image, index, pixel()) else ImagePacked.writePackedPixelAt(image, index, pixel(), view); byteArrayOf() }
                        "fill" -> { if (complete) ImagePacked.fillImageWith(image, pixel()) else ImagePacked.fillImageWith(image, pixel(), view); byteArrayOf() }
                        "between" -> { if (complete) ImagePacked.unsafeWritePixelBetweenAt(image, pixel(), index, count) else ImagePacked.unsafeWritePixelBetweenAt(image, pixel(), index, count, view); byteArrayOf() }
                        else -> error(op)
                    }
                    out.data["pixelHex"] = result
                } catch (error: RuntimeException) { out.exception = error.message.orEmpty() }
                out.data["dataHex"] = packed(data)
            }
            "rgba16" -> {
                val data = ShortArray(storage.size) { storage[it].toShort() }
                val image = MutableImageRGBA16(params[0].toInt(), params[1].toInt(), data)
                fun pixel(): PixelRGBA16 = PixelRGBA16(values[0].toUShort(), values[0 + 1].toUShort(), values[0 + 2].toUShort(), values[0 + 3].toUShort())
                val complete = view.offset == 0L && view.length == data.size.toLong()
                try {
                    val result = when (op) {
                        "read" -> {
                            val p = if (complete) ImagePacked.readPackedPixelAt(image, index) else ImagePacked.readPackedPixelAt(image, index, view)
                            val fields = listOf(p.red.toULong(), p.green.toULong(), p.blue.toULong(), p.alpha.toULong())
                            val result = ByteArray(fields.size * 2)
                            fields.forEachIndexed { c, field -> word(field, 2).copyInto(result, c * 2) }
                            result
                        }
                        "write" -> { if (complete) ImagePacked.writePackedPixelAt(image, index, pixel()) else ImagePacked.writePackedPixelAt(image, index, pixel(), view); byteArrayOf() }
                        "fill" -> { if (complete) ImagePacked.fillImageWith(image, pixel()) else ImagePacked.fillImageWith(image, pixel(), view); byteArrayOf() }
                        "between" -> { if (complete) ImagePacked.unsafeWritePixelBetweenAt(image, pixel(), index, count) else ImagePacked.unsafeWritePixelBetweenAt(image, pixel(), index, count, view); byteArrayOf() }
                        else -> error(op)
                    }
                    out.data["pixelHex"] = result
                } catch (error: RuntimeException) { out.exception = error.message.orEmpty() }
                out.data["dataHex"] = packed(data)
            }
            "cmyk8" -> {
                val data = ByteArray(storage.size) { storage[it].toByte() }
                val image = MutableImageCMYK8(params[0].toInt(), params[1].toInt(), data)
                fun pixel(): PixelCMYK8 = PixelCMYK8(values[0].toUByte(), values[0 + 1].toUByte(), values[0 + 2].toUByte(), values[0 + 3].toUByte())
                val complete = view.offset == 0L && view.length == data.size.toLong()
                try {
                    val result = when (op) {
                        "read" -> {
                            val p = if (complete) ImagePacked.readPackedPixelAt(image, index) else ImagePacked.readPackedPixelAt(image, index, view)
                            val fields = listOf(p.cyan.toULong(), p.magenta.toULong(), p.yellow.toULong(), p.black.toULong())
                            val result = ByteArray(fields.size * 1)
                            fields.forEachIndexed { c, field -> word(field, 1).copyInto(result, c * 1) }
                            result
                        }
                        "write" -> { if (complete) ImagePacked.writePackedPixelAt(image, index, pixel()) else ImagePacked.writePackedPixelAt(image, index, pixel(), view); byteArrayOf() }
                        "fill" -> { if (complete) ImagePacked.fillImageWith(image, pixel()) else ImagePacked.fillImageWith(image, pixel(), view); byteArrayOf() }
                        "between" -> { if (complete) ImagePacked.unsafeWritePixelBetweenAt(image, pixel(), index, count) else ImagePacked.unsafeWritePixelBetweenAt(image, pixel(), index, count, view); byteArrayOf() }
                        else -> error(op)
                    }
                    out.data["pixelHex"] = result
                } catch (error: RuntimeException) { out.exception = error.message.orEmpty() }
                out.data["dataHex"] = packed(data)
            }
            "cmyk16" -> {
                val data = ShortArray(storage.size) { storage[it].toShort() }
                val image = MutableImageCMYK16(params[0].toInt(), params[1].toInt(), data)
                fun pixel(): PixelCMYK16 = PixelCMYK16(values[0].toUShort(), values[0 + 1].toUShort(), values[0 + 2].toUShort(), values[0 + 3].toUShort())
                val complete = view.offset == 0L && view.length == data.size.toLong()
                try {
                    val result = when (op) {
                        "read" -> {
                            val p = if (complete) ImagePacked.readPackedPixelAt(image, index) else ImagePacked.readPackedPixelAt(image, index, view)
                            val fields = listOf(p.cyan.toULong(), p.magenta.toULong(), p.yellow.toULong(), p.black.toULong())
                            val result = ByteArray(fields.size * 2)
                            fields.forEachIndexed { c, field -> word(field, 2).copyInto(result, c * 2) }
                            result
                        }
                        "write" -> { if (complete) ImagePacked.writePackedPixelAt(image, index, pixel()) else ImagePacked.writePackedPixelAt(image, index, pixel(), view); byteArrayOf() }
                        "fill" -> { if (complete) ImagePacked.fillImageWith(image, pixel()) else ImagePacked.fillImageWith(image, pixel(), view); byteArrayOf() }
                        "between" -> { if (complete) ImagePacked.unsafeWritePixelBetweenAt(image, pixel(), index, count) else ImagePacked.unsafeWritePixelBetweenAt(image, pixel(), index, count, view); byteArrayOf() }
                        else -> error(op)
                    }
                    out.data["pixelHex"] = result
                } catch (error: RuntimeException) { out.exception = error.message.orEmpty() }
                out.data["dataHex"] = packed(data)
            }
            else -> error(type)
        }
        return out
    }

    @TestFactory fun `all original packed pixel instances and mutable views`(): Stream<DynamicTest> {
        val reader = GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/images/image-packed.jsonl.gz"))).bufferedReader()
        return reader.lineSequence().drop(1).map { line ->
            val name = Json.parseToJsonElement(line).jsonObject.getValue("name").jsonPrimitive.content
            DynamicTest.dynamicTest(name) {
                val c = Json.parseToJsonElement(line).jsonObject
                fun string(key: String) = c.getValue(key).jsonPrimitive.content
                fun values(key: String) = c.getValue(key).jsonArray.map { it.jsonPrimitive.content.toULong() }
                val op = string("op"); val type = string("type")
                val input = if (string("kind") == "values") values("values") else generated(type, string("kind"))
                val params = c.getValue("params").jsonArray.map { it.jsonPrimitive.long }.toLongArray().copyOf(6)
                val result = if (op == "pack" || op == "unpack") pure(op, type, input) else memory(op, type, params, values("storage"), input)
                val expected = c.getValue("expected").jsonObject
                assertEquals(expected["exception"]?.jsonPrimitive?.content, result.exception)
                val hashes = expected.getValue("hashes").jsonObject
                assertEquals(hashes.keys, result.data.keys)
                for ((key, wanted) in hashes) {
                    val data = result.data.getValue(key)
                    assertEquals(expected.getValue("lengths").jsonObject.getValue(key).jsonPrimitive.int, data.size)
                    assertEquals(wanted.jsonPrimitive.content, MessageDigest.getInstance("SHA-256").digest(data).toHexString())
                    expected[key]?.let { assertContentEquals(it.jsonPrimitive.content.hexToByteArray(), data) }
                }
            }
        }.asStream().onClose { reader.close() }
    }
}
