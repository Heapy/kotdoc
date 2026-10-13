// Text.Pandoc.ImageSize: Copyright (C) 2011–2024 John MacFarlane.
// Translated from Pandoc 3.12.1, GPL-2.0-or-later.
package io.heapy.kotdoc.images

import java.math.BigInteger

/** Pandoc's integer dimensions and resolution, with arbitrary-precision fields. */
data class ImageSize(
    val pxX: BigInteger = 300.toBigInteger(),
    val pxY: BigInteger = 200.toBigInteger(),
    val dpiX: BigInteger = 72.toBigInteger(),
    val dpiY: BigInteger = 72.toBigInteger(),
) {
    fun sizeInPixels(): Pair<BigInteger, BigInteger> = pxX to pxY
    fun sizeInPoints(): Pair<Double, Double> =
        (pxX.toDouble() * 72 / dpiX.toDouble()) to (pxY.toDouble() * 72 / dpiY.toDouble())

    /** Applied by the upstream imageSize dispatcher, after a parser succeeds. */
    fun checkDpi(): ImageSize = copy(
        dpiX = if (dpiX.signum() <= 0) 72.toBigInteger() else dpiX,
        dpiY = if (dpiY.signum() <= 0) 72.toBigInteger() else dpiY,
    )
}

enum class ImageType { Png, Gif, Jpeg, Svg, Pdf, Eps, Emf, Tiff, Webp, Avif }

/**
 * Original header scanners. A null PNG/JPEG result requests the upstream full
 * decoder fallback; it does not mean the complete imageSize operation failed.
 * These functions do not validate pixels, CRCs, or the entire file container.
 */
object ImageHeaders {
    fun imageType(bytes: ByteArray): ImageType? {
        val b = Input(bytes)
        return when {
            b.has(0, "\u0089PNG") -> ImageType.Png
            b.has(0, "GIF8") -> ImageType.Gif
            b.has(0, "II*\u0000") || b.has(0, "MM\u0000*") -> ImageType.Tiff
            b.has(0, "\u00ff\u00d8\u00ff\u00db") ||
                (b.has(0, "\u00ff\u00d8\u00ff") && bytes.size >= 4 && (bytes[3].toInt() and 255) in 0xe0..0xef) -> ImageType.Jpeg
            b.has(0, "%PDF") -> ImageType.Pdf
            b.has(0, "<svg") -> ImageType.Svg
            b.has(0, "<?xm") && bytes.indices.any { bytes[it] == 60.toByte() && (b.has(it + 1, "svg") || b.has(it + 1, "SVG")) } -> ImageType.Svg
            b.has(0, "%!PS") && b.has(bytes.indexOf(32.toByte()).let { if (it < 0) bytes.size else it + 1 }, "EPSF") -> ImageType.Eps
            b.has(0, "\u0001\u0000\u0000\u0000") && b.has(40, " EMF") -> ImageType.Emf
            b.has(0, "\u00ef\u00bb\u00bf<") -> imageType(bytes.copyOfRange(3, bytes.size))
            b.has(0, "RIFF") && b.has(8, "WEBP") -> ImageType.Webp
            b.has(4, "ftyp") && (b.has(8, "avif") || b.has(8, "avis")) -> ImageType.Avif
            else -> null
        }
    }

    fun pngSize(bytes: ByteArray): ImageSize? = parse(bytes) {
        skip(8)
        val length = unsigned(4)
        requireInput(ascii(4) == "IHDR" && length >= 13)
        val w = unsigned(4)
        val h = unsigned(4)
        skip(length - 8 + 4)
        var dpi = 72L to 72L
        while (!empty) {
            val len = unsigned(4)
            when (ascii(4)) {
                "pHYs" -> {
                    val x = unsigned(4)
                    val y = unsigned(4)
                    if (unsigned(1) == 1L) dpi = (x * 254 / 10000) to (y * 254 / 10000)
                    break
                }
                "IDAT", "IEND" -> break
                else -> skip(len + 4)
            }
        }
        size(w, h, dpi.first, dpi.second)
    }

    fun jpegSize(bytes: ByteArray): ImageSize? = parse(bytes) {
        skip(2)
        var jfif: Pair<Long, Long>? = null
        var exif: Pair<Long, Long>? = null
        while (true) {
            requireInput(unsigned(1) == 255L)
            var marker: Int
            do { marker = unsigned(1).toInt() } while (marker == 255)
            when {
                marker in 0xc0..0xcf && marker !in listOf(0xc4, 0xc8, 0xcc) -> {
                    skip(3)
                    val h = unsigned(2)
                    val w = unsigned(2)
                    val dpi = exif ?: jfif ?: (72L to 72L)
                    return@parse size(w, h, dpi.first, dpi.second)
                }
                marker == 0xd9 || marker == 0xda -> fail()
                marker in 0xd0..0xd7 || marker == 1 -> Unit
                else -> {
                    val length = unsigned(2)
                    requireInput(length >= 2)
                    val body = take(length - 2)
                    when (marker) {
                        0xe0 -> jfif = jfifDensity(body) ?: jfif
                        0xe1 -> exif = exifDensity(body) ?: exif
                    }
                }
            }
        }
        @Suppress("UNREACHABLE_CODE")
        error("unreachable")
    }

    private fun jfifDensity(bytes: ByteArray): Pair<Long, Long>? = parse(bytes) {
        requireInput(has(0, "JFIF\u0000"))
        skip(7)
        val units = unsigned(1)
        val x = unsigned(2)
        val y = unsigned(2)
        when (units) {
            1L -> x to y
            2L -> (x * 254 / 100) to (y * 254 / 100)
            else -> fail()
        }
    }

    private fun exifDensity(bytes: ByteArray): Pair<Long, Long>? = parse(bytes) {
        requireInput(has(0, "Exif\u0000\u0000"))
        skip(6)
        val tiff = Input(take((bytes.size - 6).toLong()))
        val little = when (tiff.ascii(2)) { "II" -> true; "MM" -> false; else -> fail() }
        fun at(offset: Long, count: Int) = tiff.at(offset, count, little)
        val ifd = at(4, 4)
        val count = at(ifd, 2).toInt()
        // Read every tag before looking up any requested value. The upstream
        // parser rejects a truncated later entry even if earlier tags suffice.
        val entries = List(count) { i ->
            val off = ifd + 2 + 12L * i
            at(off, 2).toInt() to off
        }
        fun entry(tag: Int): Long = entries.firstOrNull { it.first == tag }?.second ?: fail()
        val unit = at(entry(0x0128) + 8, 2)
        requireInput(unit == 2L || unit == 3L)
        fun resolution(tag: Int): Long {
            val offset = at(entry(tag) + 8, 4)
            val numerator = at(offset, 4)
            val denominator = at(offset + 4, 4)
            requireInput(denominator != 0L)
            val value = numerator / denominator
            return if (unit == 3L) value * 254 / 100 else value
        }
        resolution(0x011a) to resolution(0x011b)
    }

    fun emfSize(bytes: ByteArray): ImageSize? = parse(bytes) {
        skip(24)
        val left = unsigned(4, true).toInt().toBigInteger()
        val top = unsigned(4, true).toInt().toBigInteger()
        val right = unsigned(4, true).toInt().toBigInteger()
        val bottom = unsigned(4, true).toInt().toBigInteger()
        skip(32)
        val dx = unsigned(4, true).toBigInteger()
        val dy = unsigned(4, true).toBigInteger()
        val mx = unsigned(4, true).toBigInteger()
        val my = unsigned(4, true).toBigInteger()
        requireInput(mx.signum() != 0 && my.signum() != 0)
        ImageSize(dx * (right - left) / (mx * 100.toBigInteger()),
            dy * (bottom - top) / (my * 100.toBigInteger()),
            dx * 254.toBigInteger() / (mx * 10.toBigInteger()),
            dy * 254.toBigInteger() / (my * 10.toBigInteger()))
    }

    fun webpSize(bytes: ByteArray, dpi: BigInteger = 96.toBigInteger()): ImageSize? = parse(bytes) {
        requireInput(ascii(4) == "RIFF")
        skip(4)
        requireInput(ascii(4) == "WEBP")
        val (w, h) = when (ascii(4)) {
            "VP8 " -> {
                skip(4)
                requireInput(unsigned(1) and 1L == 0L)
                skip(2)
                requireInput(unsigned(1) == 0x9dL && unsigned(1) == 1L && unsigned(1) == 0x2aL)
                (unsigned(2, true) and 0x3fff) to (unsigned(2, true) and 0x3fff)
            }
            "VP8L" -> {
                skip(4)
                requireInput(unsigned(1) == 0x2fL)
                val word = unsigned(4, true)
                (1 + (word and 0x3fff)) to (1 + ((word ushr 14) and 0x3fff))
            }
            "VP8X" -> { skip(8); (1 + unsigned(3, true)) to (1 + unsigned(3, true)) }
            else -> fail()
        }
        ImageSize(w.toBigInteger(), h.toBigInteger(), dpi, dpi)
    }

    fun avifSize(bytes: ByteArray, dpi: BigInteger = 96.toBigInteger()): ImageSize? = parse(bytes) {
        val ftyp = boxHeader()
        requireInput(ftyp.type == "ftyp" && ftyp.contentSize >= 8)
        val brand = ascii(4)
        requireInput(brand == "avif" || brand == "avis")
        skip(ftyp.contentSize - 4)
        // The original recursive search tail-calls into a container and, once
        // its range is exhausted, resumes unbounded scanning at that position.
        // It does not restore enclosing ranges. Preserve that behavior here.
        var remaining: Long? = null
        while (!empty) {
            if (remaining != null && remaining < 8) remaining = null
            val box = boxHeader()
            if (remaining != null) requireInput(box.contentSize + box.headerSize <= remaining)
            if (box.type in containers) {
                if (box.type == "meta") skip(4)
                remaining = box.contentSize - if (box.type == "meta") 4 else 0
                continue
            }
            val start = position
            val dimensions: Pair<Long, Long>? = when (box.type) {
                "ispe" -> { skip(4); unsigned(4) to unsigned(4) }
                "tkhd" -> {
                    val version = unsigned(1)
                    skip(3)
                    skip(if (version == 1L) 84 else 72)
                    (unsigned(4) ushr 16) to (unsigned(4) ushr 16)
                }
                "av01" -> av01Dimensions()
                "stsd" -> {
                    skip(8)
                    var codec: String
                    do {
                        val length = unsigned(4)
                        codec = ascii(4)
                        if (codec != "av01" && length > 8) skip(length - 8)
                    } while (codec != "av01")
                    av01Dimensions()
                }
                else -> null
            }
            if (dimensions != null) return@parse ImageSize(dimensions.first.toBigInteger(), dimensions.second.toBigInteger(), dpi, dpi)
            val unconsumed = box.contentSize - (position - start)
            if (unconsumed > 0) skip(unconsumed)
            remaining = remaining?.minus(box.contentSize + box.headerSize)
        }
        fail()
    }

    private val containers = setOf("moov", "trak", "mdia", "minf", "stbl", "meta", "dinf", "ipco", "iprp")
    private data class Box(val type: String, val contentSize: Long, val headerSize: Int)
    private fun Input.boxHeader(): Box {
        val length = unsigned(4)
        val type = ascii(4)
        return when (length) {
            0L -> Box(type, (bytes.size - position).toLong(), 8)
            1L -> {
                // Valid largesize is <= 0x7fffffff: checking the high word first
                // avoids interpreting the unsigned 64-bit value as signed.
                val high = unsigned(4)
                val low = unsigned(4)
                requireInput(high == 0L && low in 16..0x7fffffff)
                Box(type, low - 16, 16)
            }
            else -> { requireInput(length >= 8); Box(type, length - 8, 8) }
        }
    }
    private fun Input.av01Dimensions(): Pair<Long, Long> { skip(24); return unsigned(2) to unsigned(2) }
    private fun size(w: Long, h: Long, dx: Long, dy: Long) = ImageSize(w.toBigInteger(), h.toBigInteger(), dx.toBigInteger(), dy.toBigInteger())

    private class InvalidHeader : RuntimeException(null, null, false, false)
    private fun fail(): Nothing = throw InvalidHeader()
    private fun requireInput(condition: Boolean) { if (!condition) fail() }
    private inline fun <T> parse(bytes: ByteArray, parser: Input.() -> T): T? =
        try { Input(bytes).parser() } catch (_: InvalidHeader) { null }

    private class Input(val bytes: ByteArray) {
        var position = 0
        val empty: Boolean get() = position == bytes.size
        fun has(offset: Int, text: String): Boolean = offset >= 0 && offset.toLong() + text.length <= bytes.size &&
            text.indices.all { (bytes[offset + it].toInt() and 255) == text[it].code }
        fun skip(count: Long) { requireInput(count >= 0 && count <= bytes.size - position); position += count.toInt() }
        fun skip(count: Int) = skip(count.toLong())
        fun take(count: Long): ByteArray { val start = position; skip(count); return bytes.copyOfRange(start, position) }
        fun ascii(count: Int): String = take(count.toLong()).toString(Charsets.ISO_8859_1)
        fun unsigned(count: Int, little: Boolean = false): Long {
            val start = position
            skip(count)
            var result = 0L
            for (i in 0..<count) {
                val index = if (little) start + count - i - 1 else start + i
                result = (result shl 8) or (bytes[index].toLong() and 255)
            }
            return result
        }
        fun at(offset: Long, count: Int, little: Boolean): Long {
            requireInput(offset >= 0 && offset <= bytes.size)
            position = offset.toInt()
            return unsigned(count, little)
        }
    }
}
