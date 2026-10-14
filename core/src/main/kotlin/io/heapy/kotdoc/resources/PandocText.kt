// Text.Pandoc.Class.PandocMonad.toTextM/findDecodingError.
// Copyright (C) 2006-2026 John MacFarlane. GPL-2.0-or-later.
package io.heapy.kotdoc.resources

class PandocUtf8DecodingError(val path: String, val offset: Int, val byte: Int) : ResourceException(
    "UTF-8 decoding error in '$path' at byte offset $offset (${byte.toString(16).padStart(2, ' ')}).\n" +
        "The input must be a UTF-8 encoded text.",
)

object PandocText {
    /** Remove one leading UTF-8 BOM and every CR byte, then decode strictly. */
    fun toText(path: String, bytes: ByteArray): String {
        val start = if (bytes.size >= 3 && bytes[0] == 0xef.toByte() &&
            bytes[1] == 0xbb.toByte() && bytes[2] == 0xbf.toByte()) 3 else 0
        val filtered = if (start == 0 && 13.toByte() !in bytes) bytes else
            bytes.copyOfRange(start, bytes.size).filter { it != 13.toByte() }.toByteArray()
        val error = findDecodingError(filtered)
        if (error != null) {
            // The original rescans the unstripped file only after decoding fails.
            val (offset, byte) = findDecodingError(bytes) ?: (0 to error.second)
            throw PandocUtf8DecodingError(path, offset, byte)
        }
        return filtered.toString(Charsets.UTF_8)
    }

    /** RFC 3629; bad continuations report their byte, truncation the lead byte. */
    fun findDecodingError(bytes: ByteArray): Pair<Int, Int>? {
        var i = 0
        while (i < bytes.size) {
            val w = bytes[i].toInt() and 255
            if (w < 0x80) { i++; continue }
            if (w < 0xc2 || w > 0xf4) return i to w
            val ranges = when (w) {
                0xe0 -> listOf(0xa0..0xbf, 0x80..0xbf)
                0xed -> listOf(0x80..0x9f, 0x80..0xbf)
                0xf0 -> listOf(0x90..0xbf, 0x80..0xbf, 0x80..0xbf)
                0xf4 -> listOf(0x80..0x8f, 0x80..0xbf, 0x80..0xbf)
                in 0xc2..0xdf -> listOf(0x80..0xbf)
                in 0xe1..0xef -> listOf(0x80..0xbf, 0x80..0xbf)
                else -> listOf(0x80..0xbf, 0x80..0xbf, 0x80..0xbf)
            }
            for ((n, range) in ranges.withIndex()) {
                val j = i + n + 1
                if (j == bytes.size) return i to w
                val byte = bytes[j].toInt() and 255
                if (byte !in range) return j to byte
            }
            i += ranges.size + 1
        }
        return null
    }
}
