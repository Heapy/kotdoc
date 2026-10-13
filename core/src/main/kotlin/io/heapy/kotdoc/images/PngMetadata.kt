// Codec.Picture.Png / Internal.Type / Internal.Metadata:
// Copyright (c) 2011 Vincent Berthoux, BSD-3-Clause; see licenses/.
// Zlib forcing semantics: Copyright (c) 2006–2016 Duncan Coutts; see licenses/zlib-LICENSE.txt.
package io.heapy.kotdoc.images

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.DataFormatException
import java.util.zip.Inflater

/**
 * JuicyPixels' PNG decoder as consumed by Pandoc's metadata-only fallback.
 * Pixel values are not exposed. True-color pixel thunks stay unevaluated;
 * palette paths force the compressed stream and the scanline traversal.
 */
object PngMetadata {
    sealed interface Result {
        data class Success(val size: ImageSize) : Result
        data class Failure(val message: String) : Result
    }

    /** Like the original pure zlib exception, this is not an Either failure. */
    class DecompressionException(message: String) : RuntimeException(message)

    /** The original uses unsafe reads here and has no portable defined outcome. */
    class TruncatedScanlineException : RuntimeException("PNG scanline exceeds decompressed input")

    fun read(bytes: ByteArray): Result = try {
        Result.Success(parse(bytes))
    } catch (error: InvalidPng) {
        Result.Failure(error.message!!)
    }

    private data class Chunk(val type: String, val data: ByteArray)
    private class InvalidPng(message: String) : RuntimeException(message)
    private fun invalid(message: String): Nothing = throw InvalidPng(message)

    private fun parse(bytes: ByteArray): ImageSize {
        val input = Input(bytes)
        if (!input.take(8).contentEquals(signature)) invalid("Invalid PNG file, signature broken")
        input.word32() // IHDR declared length and CRC are deliberately ignored.
        if (input.take(4).toString(Charsets.ISO_8859_1) != "IHDR") invalid("Invalid PNG file, wrong ihdr")
        val width = input.word32()
        val height = input.word32()
        val depth = input.word8()
        val color = input.word8()
        if (color !in listOf(0, 2, 3, 4, 6)) invalid("Invalid png color code")
        input.word8() // Compression and filter methods are unvalidated Word8s.
        input.word8()
        val interlace = input.word8()
        if (interlace !in 0..1) invalid("Invalid interlace method")
        input.word32()
        val chunks = mutableListOf<Chunk>()
        do {
            val length = input.word32()
            val type = input.take(4)
            val data = input.take(length)
            val given = input.word32()
            val crc = CRC32().apply { update(type); update(data) }.value
            if (crc != given) invalid("Invalid CRC : $crc, $given")
            val chunk = Chunk(type.toString(Charsets.ISO_8859_1), data)
            chunks.add(chunk)
        } while (chunk.type != "IEND")

        val idat = chunks.filter { it.type == "IDAT" }
        if (idat.sumOf { it.data.size.toLong() } <= 6) invalid("Invalid data size")
        val paletted = color == 3 || (color == 0 && depth in listOf(1, 2, 4))
        if (color == 3) {
            val palette = chunks.firstOrNull { it.type == "PLTE" }
            if (palette == null || palette.data.size % 3 != 0) invalid("no valid palette found")
        }
        if (paletted) {
            // pngFiltering returns immediately for empty passes, without ever
            // demanding its lazy input or even the bit-depth-specific unpacker.
            val passes = if (interlace == 0) listOf(width to height) else adam7.map { (x, y, dx, dy) ->
                passSize(width, x, dx) to passSize(height, y, dy)
            }
            val scanlines = passes.map { (w, h) -> ((w * depth + 7) / 8) to h }
                .filter { (w, h) -> w > 0 && h > 0 }
            if (scanlines.isNotEmpty()) {
                val compressed = ByteArrayOutputStream().apply { idat.forEach { write(it.data) } }.toByteArray()
                val decodedSize = inflateSize(compressed, firstOutputOnly = false)
                if (depth <= 8 && depth !in listOf(1, 2, 4, 8)) error("Impossible bit depth")
                val required = scanlines.sumOf { (w, h) -> (w + 1).toBigInteger() * h.toBigInteger() }
                if (required > decodedSize.toBigInteger()) throw TruncatedScanlineException()
                // Filter/unpack arithmetic cannot change metadata or fail for
                // in-bounds scanlines. Unknown row filters use filterNone upstream.
            }
            if (depth > 8) invalid("Invalid bit depth for paleted image")
        }

        // extractMetadatas folds every textual chunk into the map. PngZText's
        // strict field forces only the first lazy output constructor. zlib flushes
        // any produced bytes before raising a trailing error, including bad Adler.
        chunks.filter { it.type == "zTXt" }.forEach { chunk ->
            val end = chunk.data.indexOf(0)
            if (end >= 0 && end + 1 < chunk.data.size && chunk.data[end + 1] == 0.toByte()) {
                inflateSize(chunk.data.copyOfRange(end + 2, chunk.data.size), firstOutputOnly = true)
            }
        }
        var x = 72L
        var y = 72L
        chunks.firstOrNull { it.type == "pHYs" }?.data?.let { data ->
            if (data.size >= 9) {
                val phys = Input(data)
                val px = phys.word32()
                val py = phys.word32()
                if (phys.word8() == 1) {
                    x = px * 254 / 10000
                    y = py * 254 / 10000
                }
            }
        }
        return ImageSize(width.toBigInteger(), height.toBigInteger(), x.toBigInteger(), y.toBigInteger())
    }

    private fun passSize(size: Long, start: Int, stride: Int): Long =
        if (size <= start) 0 else (size - start + stride - 1) / stride

    private fun inflateSize(bytes: ByteArray, firstOutputOnly: Boolean): Long {
        val inflater = Inflater()
        return try {
            // A one-byte output buffer observes the existence of the first lazy
            // chunk before a later stream error. It does not demand its tail.
            val buffer = ByteArray(if (firstOutputOnly) 1 else 8192)
            var outputSize = 0L
            inflater.setInput(bytes)
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count > 0) {
                    if (firstOutputOnly) return count.toLong()
                    outputSize += count
                } else if (!inflater.finished()) {
                    if (inflater.needsDictionary()) throw DecompressionException("compressed data stream requires custom dictionary")
                    if (inflater.needsInput()) throw DecompressionException("premature end of compressed data stream")
                    throw DecompressionException("compressed data stream made no progress")
                }
            }
            outputSize
        } catch (error: DataFormatException) {
            // Inflater may write output and discover the bad checksum within the
            // same native call. zlib's lazy stream exposes that output first.
            if (firstOutputOnly && inflater.bytesWritten > 0) inflater.bytesWritten
            else throw DecompressionException("compressed data stream format error (${error.message})")
        } finally {
            inflater.end()
        }
    }

    private class Input(private val bytes: ByteArray) {
        private var position = 0
        fun word8(): Int {
            if (position == bytes.size) invalid("not enough bytes")
            return bytes[position++].toInt() and 255
        }
        fun word32(): Long = (word8().toLong() shl 24) or (word8().toLong() shl 16) or
            (word8().toLong() shl 8) or word8().toLong()
        fun take(count: Int): ByteArray = take(count.toLong())
        fun take(count: Long): ByteArray {
            if (count > bytes.size - position) invalid("not enough bytes")
            return bytes.copyOfRange(position, position + count.toInt()).also { position += count.toInt() }
        }
    }

    private val signature = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
    private val adam7 = listOf(
        listOf(0, 0, 8, 8), listOf(4, 0, 8, 8), listOf(0, 4, 4, 8),
        listOf(2, 0, 4, 4), listOf(0, 2, 2, 4), listOf(1, 0, 2, 2), listOf(0, 1, 1, 2),
    )
}
