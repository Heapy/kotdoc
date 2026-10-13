// Codec.Picture.Tiff / Tiff.Internal.Types / Tiff.Internal.Metadata:
// Copyright (c) 2011 Vincent Berthoux, JuicyPixels 3.3.9, BSD-3-Clause.
package io.heapy.kotdoc.images

import io.heapy.kotdoc.formats.HaskellString
import java.util.concurrent.CancellationException

/** JuicyPixels' TIFF metadata consumer path; pixel strips remain unevaluated. */
object TiffMetadata {
    sealed interface Result {
        data class Success(val size: ImageSize) : Result
        data class Failure(val message: String) : Result
    }

    fun read(bytes: ByteArray): Result = try {
        Result.Success(Parser(bytes).read())
    } catch (error: InvalidTiff) {
        Result.Failure(error.message!!)
    }

    private class InvalidTiff(message: String) : RuntimeException(message)
    private fun fail(message: String): Nothing = throw InvalidTiff(message)

    private sealed interface Exif {
        data object None : Exif
        data class Scalar(val name: String, val value: Long) : Exif
        data class Numbers(val name: String, val values: List<Long>) : Exif
        data class Bytes(val name: String, val value: ByteArray) : Exif
        data class Rational(val numerator: Long, val denominator: Long, val signed: Boolean = false) : Exif
        data object SubIfd : Exif
    }

    private data class Entry(val tag: Int, val type: Int, val count: Long, val offset: Long, var exif: Exif = Exif.None)

    private class Parser(private val bytes: ByteArray) {
        private var position = 0
        private var little = true

        fun read(): ImageSize {
            little = when (word(2)) {
                0x4949L -> true
                0x4d4dL -> false
                else -> fail("Invalid endian tag value")
            }
            if (word(2) != 42L) fail("Invalid TIFF magic number")
            val offset = word(4)
            skip(offset - position)
            val entries = directory().sortedBy { it.offset }.map(::clean)
            fetch(entries)

            fun first(tag: Int): Entry? = entries.firstOrNull { it.tag == tag }
            fun required(tag: Int, message: String): Entry = first(tag) ?: fail(message)
            fun data(tag: Int, message: String): Long = required(tag, message).offset
            fun defaultData(tag: Int, value: Long): Long = first(tag)?.offset ?: value
            fun extended(tag: Int, missing: String, malformed: String): List<Long> = numbers(required(tag, missing).exif, malformed)
            fun defaultExtended(tag: Int, name: String, default: List<Long>): List<Long> {
                val exif = first(tag)?.exif ?: Exif.None
                if (exif == Exif.None) return default
                return numbers(exif, "Can't parse tag $name ${show(exif)}")
            }

            // Get's applicative actions are sequenced in precisely this order.
            data(256, "Can't find width")
            val height = data(257, "Can't find height")
            val color = data(262, "Can't find color space")
            if (color !in listOf(0L, 1L, 2L, 3L, 4L, 5L, 6L, 8L)) fail("Unrecognized color space $color")
            data(277, "Can't find sample per pixel")
            var rows = data(278, "Can't find row per strip")
            val planar = defaultData(284, 1)
            if (planar !in 0L..2L) fail("Unknown planar constant ($planar)")
            val format = defaultExtended(339, "TagSampleFormat", listOf(1))
            format.forEach { if (it !in 1L..4L) fail("Undefined data format ($it)") }
            val bits = extended(258, "Can't find bit per sample", "Can't find bit depth")
            val compression = data(259, "Can't find Compression")
            if (compression !in listOf(0L, 1L, 2L, 5L, 6L, 32773L)) fail("Unknown compression scheme $compression")
            extended(279, "Can't find byte counts", "Can't find bit per sample")
            extended(273, "Strip offsets missing", "Can't find strip offsets")
            val palette = (first(320)?.exif as? Exif.Numbers)?.name == "ExifShorts"
            defaultExtended(530, "TagYCbCrSubsampling", listOf(2, 2))
            val predictor = defaultData(317, 1)
            if (predictor !in 1L..2L) fail("Unknown predictor ($predictor)")

            // The original tail-recurses forever when both these values are 0.
            // Preserve divergence, while allowing the calling thread to cancel.
            while (rows == 0L) {
                if (Thread.currentThread().isInterrupted) throw CancellationException("TIFF metadata decoding interrupted")
                rows = height
            }
            validateColor(color, bits, format, planar, palette)

            // Metadata's right-biased union differs from first-tag validation.
            val widthMeta = entries.last { it.tag == 256 }.offset
            val heightMeta = entries.last { it.tag == 257 }.offset
            val unit = first(296)?.takeIf { it.type == 3 }?.offset
            fun dpi(tag: Int): Long {
                if (unit != 2L && unit != 3L) return 72
                val rational = first(tag)?.exif as? Exif.Rational ?: return 72
                if (rational.signed) return 72
                // Zero denominators throw in the original; do not turn them
                // into a missing resolution or a format-parser failure.
                val value = rational.numerator / rational.denominator
                return if (unit == 3L) value * 254 / 100 else value
            }
            return ImageSize(widthMeta.toBigInteger(), heightMeta.toBigInteger(), dpi(282).toBigInteger(), dpi(283).toBigInteger())
        }

        private fun clean(entry: Entry): Entry =
            if (!little && entry.type == 3 && entry.count == 1L) entry.copy(offset = entry.offset ushr 16) else entry

        private fun directory(): List<Entry> {
            val count = word(2).toInt()
            val entries = List(count) {
                val tag = word(2).toInt()
                val type = word(2).toInt()
                if (type !in 1..12) fail("Invalid TIF directory type")
                Entry(tag, type, word(4), word(4))
            }
            word(4) // The next-directory pointer is consumed, never followed.
            return entries
        }

        /** Shared forward-only cursor, also used by nested Exif directories. */
        private fun fetch(entries: List<Entry>) {
            data class Frame(val entries: List<Entry>, var index: Int = 0)
            val stack = ArrayDeque<Frame>()
            stack.addLast(Frame(entries))
            while (stack.isNotEmpty()) {
                val frame = stack.last()
                if (frame.index == frame.entries.size) { stack.removeLast(); continue }
                val e = frame.entries[frame.index++]
                if (e.tag == 34665 && e.type == 4 && e.count == 1L) {
                    if (align(e)) {
                        val nested = directory().sortedBy { it.offset }.map(::clean).sortedBy { it.offset }
                        e.exif = Exif.SubIfd
                        stack.addLast(Frame(nested))
                    }
                    continue
                }
                fun immediate(): ByteArray = ByteArray(e.count.toInt()) { i ->
                    (e.offset ushr (if (little) i * 8 else (3 - i) * 8)).toByte()
                }
                e.exif = when {
                    e.type in listOf(2, 7) -> {
                        val name = if (e.type == 2) "ExifString" else "ExifUndefined"
                        if (e.count <= 4) Exif.Bytes(name, immediate())
                        else if (align(e)) Exif.Bytes(name, take(e.count)) else Exif.None
                    }
                    e.type == 3 && e.count == 2L -> {
                        val low = e.offset and 65535
                        val high = e.offset ushr 16
                        Exif.Numbers("ExifShorts", if (little) listOf(low, high) else listOf(high, low))
                    }
                    e.type in listOf(5, 10) && e.count == 1L -> {
                        if (align(e)) {
                            // Upstream explicitly uses little endian here even
                            // when the TIFF header says big endian.
                            val a = word(4, true)
                            val b = word(4, true)
                            if (e.type == 10) Exif.Rational(a.toInt().toLong(), b.toInt().toLong(), true)
                            else Exif.Rational(a, b)
                        } else Exif.None
                    }
                    e.type == 3 && e.count == 1L -> Exif.Scalar("ExifShort", e.offset and 65535)
                    e.type == 4 && e.count == 1L -> Exif.Scalar("ExifLong", e.offset)
                    (e.type == 3 && e.count > 2) || (e.type == 4 && e.count > 1) -> {
                        if (align(e)) {
                            val width = if (e.type == 3) 2 else 4
                            // Check before allocating, preserving the same
                            // truncated-input error without an oversized vector.
                            requireBytes(e.count * width)
                            Exif.Numbers(if (e.type == 3) "ExifShorts" else "ExifLongs", List(e.count.toInt()) { word(width) })
                        } else Exif.None
                    }
                    else -> Exif.None
                }
            }
        }

        private fun align(entry: Entry): Boolean {
            if (entry.offset >= bytes.size || position > entry.offset) return false
            skip(entry.offset - position)
            return true
        }

        private fun word(count: Int, littleEndian: Boolean = little): Long {
            requireBytes(count.toLong())
            var value = 0L
            repeat(count) { i ->
                val shift = if (littleEndian) i * 8 else (count - i - 1) * 8
                value = value or ((bytes[position++].toLong() and 255) shl shift)
            }
            return value
        }

        private fun take(count: Long): ByteArray {
            requireBytes(count)
            return bytes.copyOfRange(position, position + count.toInt()).also { position += count.toInt() }
        }

        private fun skip(count: Long) {
            if (count <= 0) return // Data.Binary.Get.skip treats negative counts as zero.
            requireBytes(count)
            position += count.toInt()
        }

        private fun requireBytes(count: Long) {
            if (count > bytes.size - position) fail("not enough bytes")
        }
    }

    private fun numbers(exif: Exif, message: String): List<Long> = when (exif) {
        is Exif.Scalar -> listOf(exif.value)
        is Exif.Numbers -> exif.values
        else -> fail(message)
    }

    private fun show(exif: Exif): String = when (exif) {
        Exif.None -> "ExifNone"
        is Exif.Scalar -> "${exif.name} ${exif.value}"
        is Exif.Numbers -> "${exif.name} ${exif.values.joinToString(",", "[", "]")}"
        is Exif.Bytes -> "${exif.name} ${HaskellString.quote(exif.value.toString(Charsets.ISO_8859_1))}"
        is Exif.Rational -> {
            fun arg(value: Long) = if (value < 0) "($value)" else "$value"
            "${if (exif.signed) "ExifSignedRational" else "ExifRational"} ${arg(exif.numerator)} ${arg(exif.denominator)}"
        }
        Exif.SubIfd -> error("Exif subdirectories are not scalar/default TIFF tags")
    }

    /** The Either-producing guards of unpack are strict; their image values are not. */
    private fun validateColor(color: Long, bits: List<Long>, format: List<Long>, planar: Long, palette: Boolean) {
        val unsigned = format.all { it == 1L }
        fun repeated(n: Int, depths: List<Long>): Boolean = bits.size == n && bits.distinct().singleOrNull() in depths
        val supported = when (color) {
            3L -> palette && bits.size == 1 && bits[0] in listOf(2L, 4L, 8L) && format == listOf(1L)
            5L -> repeated(4, listOf(8, 16)) && unsigned
            0L, 1L -> {
                if (bits == listOf(64L)) fail("Failure to unpack TIFF file, 64-bit samples unsupported.")
                val mono = (bits.size == 1 && bits[0] in listOf(2L, 4L, 8L, 12L, 16L, 32L) && unsigned) ||
                    (bits == listOf(32L) && format.all { it == 3L }) ||
                    (repeated(2, listOf(2, 4, 8, 12, 16)) && unsigned) ||
                    (bits == listOf(8L, 8L, 8L) && unsigned)
                if (mono && color == 0L && (bits == listOf(32L) || bits.size == 3)) {
                    fail("Unsupported color type used with colorspace MonochromeWhite0")
                }
                mono
            }
            6L -> planar != 2L && bits == listOf(8L, 8L, 8L) && unsigned
            2L -> unsigned && (repeated(3, listOf(2, 4, 8, 16)) || repeated(4, listOf(8, 16)))
            else -> false
        }
        if (!supported) fail("Failure to unpack TIFF file")
    }
}
