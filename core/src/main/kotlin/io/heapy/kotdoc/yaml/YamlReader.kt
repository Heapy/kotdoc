// libyaml 0.2.5 reader.c; © 2006–2016 Kirill Simonov, 2017–2020 Ingy döt Net.
// MIT; complete original notice: licenses/Libyaml-MIT.txt.
package io.heapy.kotdoc.yaml

enum class YamlEncoding { Any, Utf8, Utf16LE, Utf16BE }

/** Empty bytes mean EOF; null means the original read-handler input error. */
fun interface YamlReadHandler { fun read(maximum: Int): ByteArray? }

data class YamlReaderFailure(val problem: String, val offset: ULong, val value: Int)

/** The original buffered reader state, separate from scanner marks/token state. */
class YamlReader(
    private val handler: YamlReadHandler,
    encoding: YamlEncoding = YamlEncoding.Any,
    initialOffset: ULong = 0u,
) {
    var encoding: YamlEncoding = encoding
        private set
    var offset: ULong = initialOffset
        private set
    var eof: Boolean = false
        private set
    var unread: Int = 0
        private set
    var failure: YamlReaderFailure? = null
        private set
    private var raw = ByteArray(0)
    private var rawPointer = 0
    private var rawLast = 0
    private var buffer = ByteArray(0)
    private var bufferPointer = 0
    private var bufferLast = 0

    fun bufferedBytes(): ByteArray = buffer.copyOfRange(bufferPointer, bufferLast)
    fun remainingRawBytes(): ByteArray = raw.copyOfRange(rawPointer, rawLast)
    /** Scanner lookahead without copying the populated buffer. */
    internal fun peekByte(relativeOffset: Int = 0): Int {
        require(relativeOffset >= 0 && relativeOffset < bufferLast - bufferPointer)
        return buffer[bufferPointer + relativeOffset].toInt() and 255
    }

    private fun error(problem: String, offset: ULong, value: Int): Boolean {
        failure = YamlReaderFailure(problem, offset, value)
        return false
    }
    private fun updateRaw(): Boolean {
        if (rawPointer == 0 && rawLast == 16_384) return true
        if (eof) return true
        if (rawPointer > 0 && rawPointer < rawLast) raw.copyInto(raw, 0, rawPointer, rawLast)
        rawLast -= rawPointer
        rawPointer = 0
        val bytes = handler.read(16_384 - rawLast) ?: return error("input error", offset, -1)
        require(bytes.size <= 16_384 - rawLast) { "read handler exceeded requested size" }
        if (rawLast + bytes.size > raw.size) raw = raw.copyOf(rawLast + bytes.size)
        bytes.copyInto(raw, rawLast)
        rawLast += bytes.size
        if (bytes.isEmpty()) eof = true
        return true
    }
    private fun determineEncoding(): Boolean {
        while (!eof && rawLast - rawPointer < 3) if (!updateRaw()) return false
        fun byte(i: Int) = raw[rawPointer + i].toInt() and 255
        val left = rawLast - rawPointer
        val skip: Int
        when {
            left >= 2 && byte(0) == 255 && byte(1) == 254 -> { encoding = YamlEncoding.Utf16LE; skip = 2 }
            left >= 2 && byte(0) == 254 && byte(1) == 255 -> { encoding = YamlEncoding.Utf16BE; skip = 2 }
            left >= 3 && byte(0) == 239 && byte(1) == 187 && byte(2) == 191 -> { encoding = YamlEncoding.Utf8; skip = 3 }
            else -> { encoding = YamlEncoding.Utf8; skip = 0 }
        }
        rawPointer += skip
        offset += skip.toULong()
        return true
    }

    /** As in the scanner, length is a small lookahead character count. */
    fun ensure(length: Int): Boolean {
        require(length >= 0)
        if (eof && rawPointer == rawLast) return true
        if (unread >= length) return true
        if (encoding == YamlEncoding.Any && !determineEncoding()) return false
        if (bufferPointer > 0 && bufferPointer < bufferLast) {
            buffer.copyInto(buffer, 0, bufferPointer, bufferLast)
            bufferLast -= bufferPointer
            bufferPointer = 0
        } else if (bufferPointer == bufferLast) { bufferPointer = 0; bufferLast = 0 }
        var first = true
        while (unread < length) {
            if (!first || rawPointer == rawLast) if (!updateRaw()) return false
            first = false
            while (rawPointer != rawLast) {
                fun byte(k: Int): Int = raw[rawPointer + k].toInt() and 255
                val left = rawLast - rawPointer
                var width: Int
                var value: Int
                when (encoding) {
                    YamlEncoding.Utf8 -> {
                        val lead = byte(0)
                        width = when {
                            lead and 128 == 0 -> 1
                            lead and 224 == 192 -> 2
                            lead and 240 == 224 -> 3
                            lead and 248 == 240 -> 4
                            else -> 0
                        }
                        if (width == 0) return error("invalid leading UTF-8 octet", offset, lead)
                        if (width > left) {
                            if (eof) return error("incomplete UTF-8 octet sequence", offset, -1)
                            break
                        }
                        value = lead and when (width) { 1 -> 127; 2 -> 31; 3 -> 15; else -> 7 }
                        for (k in 1 until width) {
                            val octet = byte(k)
                            if (octet and 192 != 128) return error("invalid trailing UTF-8 octet", offset + k.toULong(), octet)
                            value = (value shl 6) + (octet and 63)
                        }
                        if (!(width == 1 || width == 2 && value >= 128 || width == 3 && value >= 2048 || width == 4 && value >= 65536))
                            return error("invalid length of a UTF-8 sequence", offset, -1)
                        if (value in 0xd800..0xdfff || value > 0x10ffff) return error("invalid Unicode character", offset, value)
                    }
                    YamlEncoding.Utf16LE, YamlEncoding.Utf16BE -> {
                        if (left < 2) {
                            if (eof) return error("incomplete UTF-16 character", offset, -1)
                            break
                        }
                        val low = if (encoding == YamlEncoding.Utf16LE) 0 else 1
                        val high = 1 - low
                        value = byte(low) + (byte(high) shl 8)
                        if (value and 0xfc00 == 0xdc00) return error("unexpected low surrogate area", offset, value)
                        if (value and 0xfc00 == 0xd800) {
                            width = 4
                            if (left < 4) {
                                if (eof) return error("incomplete UTF-16 surrogate pair", offset, -1)
                                break
                            }
                            val second = byte(low + 2) + (byte(high + 2) shl 8)
                            if (second and 0xfc00 != 0xdc00) return error("expected low surrogate area", offset + 2u, second)
                            value = 0x10000 + ((value and 0x3ff) shl 10) + (second and 0x3ff)
                        } else width = 2
                    }
                    YamlEncoding.Any -> kotlin.error("encoding not determined")
                }
                if (!(value == 9 || value == 10 || value == 13 || value in 0x20..0x7e || value == 0x85 ||
                            value in 0xa0..0xd7ff || value in 0xe000..0xfffd || value in 0x10000..0x10ffff))
                    return error("control characters are not allowed", offset, value)
                rawPointer += width
                offset += width.toULong()
                when {
                    value <= 127 -> append(value)
                    value <= 2047 -> { append(192 + (value shr 6)); append(128 + (value and 63)) }
                    value <= 65535 -> { append(224 + (value shr 12)); append(128 + ((value shr 6) and 63)); append(128 + (value and 63)) }
                    else -> { append(240 + (value shr 18)); append(128 + ((value shr 12) and 63)); append(128 + ((value shr 6) and 63)); append(128 + (value and 63)) }
                }
                unread++
            }
            if (eof) { append(0); unread++; return true }
        }
        if (offset >= Long.MAX_VALUE.toULong()) return error("input is too long", offset, -1)
        return true
    }
    private fun append(value: Int) {
        // Preserve the original logical capacities while allocating only populated
        // storage. This avoids two large empty arrays per tiny scalar reader.
        check(bufferLast < 49_152) { "reader lookahead exceeds the source buffer capacity" }
        if (bufferLast == buffer.size) buffer = buffer.copyOf(minOf(49_152, maxOf(8, buffer.size * 2)))
        buffer[bufferLast++] = value.toByte()
    }

    /** Move the decoded pointer by complete characters; marks belong to the scanner. */
    fun consume(count: Int): Int {
        require(count >= 0)
        val taken = minOf(count, unread)
        repeat(taken) {
            val lead = buffer[bufferPointer].toInt() and 255
            bufferPointer += when { lead < 128 -> 1; lead < 224 -> 2; lead < 240 -> 3; else -> 4 }
        }
        unread -= taken
        return taken
    }
}
