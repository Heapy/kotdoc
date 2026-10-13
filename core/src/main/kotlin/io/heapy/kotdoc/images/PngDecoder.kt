// Codec.Picture.Png / Internal.Type / Internal.Metadata:
// Copyright (c) 2011 Vincent Berthoux, BSD-3-Clause; see licenses/.
// Zlib errors: Copyright (c) 2006–2016 Duncan Coutts; see licenses/zlib-LICENSE.txt.
package io.heapy.kotdoc.images

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.DataFormatException
import java.util.zip.Inflater
import kotlin.math.abs

/** PNG decoding without platform image codecs. The result preserves upstream demand order. */
internal object PngDecoder {
    class DecompressionException(message: String) : RuntimeException("Codec.Compression.Zlib: $message")
    sealed interface Result<out T> {
        class Success<T>(val value: T) : Result<T>
        data class Failure(val message: String) : Result<Nothing>
    }
    class Decoded(val image: PalettedRaster, val metadata: Lazy<ImageMetadatas>)
    class DecodedImage(val image: Lazy<DynamicRaster>, val metadata: Lazy<ImageMetadatas>)

    fun decodeWithPaletteAndMetadata(bytes: ByteArray): Result<Decoded> = try {
        val raw = parse(bytes)
        val compressed = raw.chunks.filter { it.type == "IDAT" }
        if (compressed.sumOf { it.data.size.toLong() } <= 6) invalid("Invalid data size")
        val h = raw.header
        val data = lazy { inflate(ByteArrayOutputStream().apply { compressed.forEach { write(it.data) } }.toByteArray()) }
        val pixels = lazy { deinterlace(h, data) }
        val image = if (h.color == 3 || h.color == 0 && h.depth in listOf(1, 2, 4)) {
            val palette = if (h.color == 0) {
                val max = (1 shl h.depth) - 1
                ByteArray((max + 1) * 3) { ((it / 3) * (255 / max)).toByte() }
            } else raw.chunks.firstOrNull { it.type == "PLTE" }?.data
                ?.takeIf { it.size % 3 == 0 } ?: invalid("no valid palette found")
            // palette8 inspects deinterlacer's Either before returning Right.
            val indices = pixels.value as? ByteArray ?: invalid("Invalid bit depth for paleted image")
            val indexed = lazyOf(RasterImage(h.width, h.height, RasterCodecs.Y8, RasterVector(RasterStorages.Bytes, indices)))
            val transparency = raw.chunks.filter { it.type == "tRNS" }
            if (transparency.size == 1) {
                PalettedRaster.RGBA8(indexed, lazy {
                    val alpha = transparency.single().data
                    val rgba = ByteArray(palette.size / 3 * 4) { i ->
                        if (i % 4 == 3) alpha.getOrElse(i / 4) { 255.toByte() }
                        else palette[i / 4 * 3 + i % 4]
                    }
                    RasterPalette((palette.size / 3).toLong(), RasterCodecs.RGBA8, RasterVector(RasterStorages.Bytes, rgba))
                })
            } else PalettedRaster.RGB8(indexed, lazyOf(RasterPalette((palette.size / 3).toLong(), RasterCodecs.RGB8, RasterVector(RasterStorages.Bytes, palette))))
        } else PalettedRaster.TrueColorImage { dynamic(h, pixels.value) }
        Result.Success(Decoded(image, lazy { metadata(raw) }))
    } catch (error: InvalidPng) {
        Result.Failure(error.message!!)
    }

    fun decodeWithMetadata(bytes: ByteArray): Result<DecodedImage> = when (val out = decodeWithPaletteAndMetadata(bytes)) {
        is Result.Failure -> out
        is Result.Success -> Result.Success(DecodedImage(lazy { out.value.image.palettedToTrueColor() }, out.value.metadata))
    }
    fun decode(bytes: ByteArray): Result<Lazy<DynamicRaster>> = when (val out = decodeWithMetadata(bytes)) {
        is Result.Failure -> out
        is Result.Success -> Result.Success(out.value.image)
    }

    private data class Header(val width: Long, val height: Long, val depth: Int, val color: Int, val interlace: Int) {
        val components get() = when (color) { 2 -> 3; 4 -> 2; 6 -> 4; else -> 1 }
    }
    private data class Chunk(val type: String, val data: ByteArray)
    private data class Raw(val header: Header, val chunks: List<Chunk>)
    private class InvalidPng(message: String) : RuntimeException(message)
    private fun invalid(message: String): Nothing = throw InvalidPng(message)
    private class Input(private val bytes: ByteArray) {
        private var position = 0
        fun byte(): Int {
            if (position == bytes.size) invalid("not enough bytes")
            return bytes[position++].toInt() and 255
        }
        fun word(): Long = (byte().toLong() shl 24) or (byte().toLong() shl 16) or (byte().toLong() shl 8) or byte().toLong()
        fun take(count: Long): ByteArray {
            if (count > bytes.size - position) invalid("not enough bytes")
            return bytes.copyOfRange(position, position + count.toInt()).also { position += count.toInt() }
        }
    }
    private fun parse(bytes: ByteArray): Raw {
        val input = Input(bytes)
        if (!input.take(8).contentEquals(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))) invalid("Invalid PNG file, signature broken")
        input.word() // The upstream IHDR parser ignores its declared length and CRC.
        if (input.take(4).toString(Charsets.ISO_8859_1) != "IHDR") invalid("Invalid PNG file, wrong ihdr")
        val w = input.word(); val h = input.word(); val depth = input.byte(); val color = input.byte()
        if (color !in listOf(0, 2, 3, 4, 6)) invalid("Invalid png color code")
        input.byte(); input.byte() // Compression and filter methods are unvalidated.
        val interlace = input.byte()
        if (interlace !in 0..1) invalid("Invalid interlace method")
        input.word()
        val chunks = mutableListOf<Chunk>()
        do {
            val size = input.word(); val type = input.take(4); val data = input.take(size); val given = input.word()
            val computed = CRC32().apply { update(type); update(data) }.value
            if (computed != given) invalid("Invalid CRC : $computed, $given")
            val chunk = Chunk(type.toString(Charsets.ISO_8859_1), data)
            chunks.add(chunk)
        } while (chunk.type != "IEND")
        return Raw(Header(w, h, depth, color, interlace), chunks)
    }

    private fun inflate(bytes: ByteArray): ByteArray {
        val inflater = Inflater()
        try {
            inflater.setInput(bytes)
            val out = ByteArrayOutputStream(); val buffer = ByteArray(8192)
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count > 0) out.write(buffer, 0, count)
                else if (!inflater.finished()) {
                    if (inflater.needsDictionary()) throw DecompressionException("compressed data stream requires custom dictionary")
                    if (inflater.needsInput()) throw DecompressionException("premature end of compressed data stream")
                    throw DecompressionException("compressed data stream made no progress")
                }
            }
            return out.toByteArray()
        } catch (error: DataFormatException) {
            throw DecompressionException("compressed data stream format error (${error.message})")
        } finally { inflater.end() }
    }

    private val adam7 = listOf(
        listOf(0, 0, 8, 8), listOf(4, 0, 8, 8), listOf(0, 4, 4, 8),
        listOf(2, 0, 4, 4), listOf(0, 2, 2, 4), listOf(1, 0, 2, 2), listOf(0, 1, 1, 2),
    )
    private fun passSize(size: Long, start: Int, stride: Int) = if (size <= start) 0 else (size - start + stride - 1) / stride
    private fun deinterlace(h: Header, data: Lazy<ByteArray>): Any {
        val size = RasterVector.allocationLength(h.width * h.height * h.components)
        val bytes = if (h.depth <= 8) ByteArray(size) else null
        val shorts = if (h.depth > 8) ShortArray(size) else null
        val bpp = if (h.depth < 8) 1 else h.components * (h.depth / 8)
        var cursor = 0
        for ((x, y, dx, dy) in if (h.interlace == 0) listOf(listOf(0, 0, 1, 1)) else adam7) {
            val w = passSize(h.width, x, dx); val height = passSize(h.height, y, dy)
            val byteWidth = (h.depth * w * h.components + 7) / 8
            if (byteWidth <= 0 || height <= 0) continue
            val input = data.value // Strict concatenation forces the entire zlib stream.
            var previous = ByteArray(Math.toIntExact(byteWidth + bpp))
            var current = ByteArray(previous.size)
            for (row in 0L..<height) {
                if (cursor.toLong() + byteWidth + 1 > input.size) throw PngMetadata.TruncatedScanlineException()
                val filter = input[cursor++].toInt() and 255
                for (i in bpp..<current.size) {
                    val a = current[i - bpp].toInt() and 255
                    val b = previous[i].toInt() and 255
                    val c = previous[i - bpp].toInt() and 255
                    val prediction = when (filter) {
                        1 -> a; 2 -> b; 3 -> (a + b) / 2
                        4 -> {
                            val p = a + b - c; val pa = abs(p - a); val pb = abs(p - b); val pc = abs(p - c)
                            if (pa <= pb && pa <= pc) a else if (pb <= pc) b else c
                        }
                        else -> 0
                    }
                    current[i] = (input[cursor++].toInt() + prediction).toByte()
                }
                val top = (y + row * dy) * h.width
                when (h.depth) {
                    1, 2, 4 -> {
                        val perByte = 8 / h.depth
                        for (column in 0L..<w) {
                            // For the remainder upstream reads the very last byte.
                            val source = if (column / perByte == w / perByte) current.lastIndex else bpp + (column / perByte).toInt()
                            val value = (current[source].toInt() ushr (8 - h.depth * ((column % perByte).toInt() + 1))) and ((1 shl h.depth) - 1)
                            bytes!![Math.toIntExact(top + x + column * dx)] = value.toByte()
                        }
                    }
                    8 -> {
                        val last = minOf(h.width - 1, (current.lastIndex - bpp).toLong() / h.components)
                        for (column in 0L..last) for (channel in 0..<h.components) {
                            bytes!![Math.toIntExact((top + x + column * dx) * h.components + channel)] = current[bpp + Math.toIntExact(column * h.components + channel)]
                        }
                    }
                    in 9..255 -> {
                        val last = minOf(h.width - 1, (current.lastIndex - bpp).toLong() / (h.components * 2))
                        for (column in 0L..last) for (channel in 0..<h.components) {
                            val at = bpp + Math.toIntExact((column * h.components + channel) * 2)
                            val value = ((current[at].toInt() and 255) shl 8) or (current[at + 1].toInt() and 255)
                            shorts!![Math.toIntExact((top + x + column * dx) * h.components + channel)] = value.toShort()
                        }
                    }
                    else -> error("Impossible bit depth")
                }
                val swap = previous; previous = current; current = swap
            }
        }
        return bytes ?: shorts!!
    }

    private fun dynamic(h: Header, data: Any): DynamicRaster {
        fun <P : Any> byteImage(codec: RasterCodec<P, UByte, ByteArray>) = lazyOf(RasterImage(h.width, h.height, codec, RasterVector(RasterStorages.Bytes, data as ByteArray)))
        fun <P : Any> shortImage(codec: RasterCodec<P, UShort, ShortArray>) = lazyOf(RasterImage(h.width, h.height, codec, RasterVector(RasterStorages.Shorts, data as ShortArray)))
        return if (h.depth <= 8) when (h.color) {
            0 -> DynamicRaster.Y8(byteImage(RasterCodecs.Y8))
            2 -> DynamicRaster.RGB8(byteImage(RasterCodecs.RGB8))
            4 -> DynamicRaster.YA8(byteImage(RasterCodecs.YA8))
            else -> DynamicRaster.RGBA8(byteImage(RasterCodecs.RGBA8))
        } else when (h.color) {
            0 -> DynamicRaster.Y16(shortImage(RasterCodecs.Y16))
            2 -> DynamicRaster.RGB16(shortImage(RasterCodecs.RGB16))
            4 -> DynamicRaster.YA16(shortImage(RasterCodecs.YA16))
            else -> DynamicRaster.RGBA16(shortImage(RasterCodecs.RGBA16))
        }
    }

    // Metadata values are lazy upstream. Keep them pending until duplicate keys
    // have been removed, so an overridden zTXt tail is never decompressed.
    private class Pending(val key: ImageMetadataKey<*>, val insert: (ImageMetadatas) -> ImageMetadatas)
    private fun <T> pending(key: ImageMetadataKey<T>, value: () -> T) = Pending(key) { it.insertLazy(key, value) }
    private fun merge(left: List<Pending>, right: List<Pending>) = right.fold(left) { acc, item -> listOf(item) + acc.filterNot { it.key == item.key } }
    private fun text(key: String, value: () -> String): Pending = when (key) {
        "Title" -> pending(ImageMetadataKey.Title, value)
        "Author" -> pending(ImageMetadataKey.Author, value)
        "Description" -> pending(ImageMetadataKey.Description, value)
        "Copyright" -> pending(ImageMetadataKey.Copyright, value)
        "Software" -> pending(ImageMetadataKey.Software, value)
        "Disclaimer" -> pending(ImageMetadataKey.Disclaimer, value)
        "Warning" -> pending(ImageMetadataKey.Warning, value)
        "Source" -> pending(ImageMetadataKey.Source, value)
        "Comment" -> pending(ImageMetadataKey.Comment, value)
        else -> pending(ImageMetadataKey.Unknown(key)) { ImageMetadataValue.StringValue(value()) }
    }
    private fun metadata(raw: Raw): ImageMetadatas {
        val dpis = raw.chunks.firstOrNull { it.type == "pHYs" }?.data?.takeIf { it.size >= 9 }?.let {
            val i = Input(it); val x = i.word(); val y = i.word(); val meter = i.byte() == 1
            listOf(pending(ImageMetadataKey.DpiX) { if (meter) ImageMetadatas.dotsPerMeterToInch(x.toULong()) else 72uL },
                pending(ImageMetadataKey.DpiY) { if (meter) ImageMetadatas.dotsPerMeterToInch(y.toULong()) else 72uL })
        } ?: emptyList()
        val gamma = raw.chunks.firstOrNull { it.type == "gAMA" }?.data?.takeIf { it.size >= 4 }?.let {
            listOf(pending(ImageMetadataKey.Gamma) { Input(it).word() / 100000.0 })
        } ?: emptyList()
        fun texts(compressed: Boolean): List<Pending> {
            val entries = raw.chunks.filter { it.type == if (compressed) "zTXt" else "tEXt" }.asReversed().mapNotNull { chunk ->
                val end = chunk.data.indexOf(0)
                if (end < 0 || compressed && (end + 1 >= chunk.data.size || chunk.data[end + 1] != 0.toByte())) return@mapNotNull null
                val content = chunk.data.copyOfRange(end + if (compressed) 2 else 1, chunk.data.size)
                if (compressed) try {
                    PngMetadata.inflateSize(content, firstOutputOnly = true)
                } catch (error: PngMetadata.DecompressionException) { throw DecompressionException(error.message!!) }
                text(chunk.data.copyOfRange(0, end).toString(Charsets.ISO_8859_1)) {
                    (if (compressed) inflate(content) else content).toString(Charsets.ISO_8859_1)
                }
            }
            return entries.fold(emptyList()) { tail, entry -> merge(listOf(entry), tail) }
        }
        val pending = merge(dpis, merge(gamma, merge(texts(false), texts(true))))
        val extracted = pending.asReversed().fold(ImageMetadatas.empty) { acc, item -> item.insert(acc) }
        return ImageMetadatas.basic(ImageSourceFormat.SourcePng, raw.header.width, raw.header.height) + extracted
    }
}
