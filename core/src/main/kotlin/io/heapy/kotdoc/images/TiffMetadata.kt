// Codec.Picture.Tiff / Tiff.Internal.Types / Tiff.Internal.Metadata:
// Copyright (c) 2011 Vincent Berthoux, JuicyPixels 3.3.9, BSD-3-Clause.
package io.heapy.kotdoc.images

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
    } catch (error: JpegBinaryException) {
        Result.Failure(error.message!!)
    }

    private class InvalidTiff(message: String) : RuntimeException(message)
    private fun fail(message: String): Nothing = throw InvalidTiff(message)

    private class Parser(private val bytes: ByteArray) {
        fun read(): ImageSize {
            val entries = TiffBinary.document(JpegInput(bytes)).directories.single()

            fun first(tag: Int): ImageFileDirectory? = entries.firstOrNull { it.identifier.code == tag }
            fun required(tag: Int, message: String): ImageFileDirectory = first(tag) ?: fail(message)
            fun data(tag: Int, message: String): Long = required(tag, message).offset
            fun defaultData(tag: Int, value: Long): Long = first(tag)?.offset ?: value
            fun extended(tag: Int, missing: String, malformed: String): List<Long> = numbers(required(tag, missing).extended, malformed)
            fun defaultExtended(tag: Int, name: String, default: List<Long>): List<Long> {
                val exif = first(tag)?.extended ?: ExifData.None
                if (exif == ExifData.None) return default
                return numbers(exif, "Can't parse tag $name $exif")
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
            val palette = first(320)?.extended is ExifData.Shorts
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

            val metadata = ImageExifMetadata.extractTiff(entries)
            fun size(key: ImageMetadataKey<ULong>) = checkNotNull(metadata.lookup(key)).toString().toBigInteger()
            fun dpi(key: ImageMetadataKey<ULong>) = (metadata.lookup(key) ?: 72uL).toString().toBigInteger()
            return ImageSize(size(ImageMetadataKey.Width), size(ImageMetadataKey.Height), dpi(ImageMetadataKey.DpiX), dpi(ImageMetadataKey.DpiY))
        }
    }

    private fun numbers(exif: ExifData, message: String): List<Long> = when (exif) {
        is ExifData.LongValue -> listOf(exif.value)
        is ExifData.ShortValue -> listOf(exif.value.toLong())
        is ExifData.Longs -> exif.values
        is ExifData.Shorts -> exif.values.map(Int::toLong)
        else -> fail(message)
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
