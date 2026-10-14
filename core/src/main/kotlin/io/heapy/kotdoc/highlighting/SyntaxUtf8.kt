// Data.Text.Lazy.Encoding/Internal.Encoding 2.1.3 and ByteString.Lazy 0.12.2.0.
// Copyright text and bytestring contributors. BSD; licenses/text-BSD-2-Clause.txt
// and ByteString-BSD-3-Clause.txt. Skylighting.Parser's BOM/CR preprocessing is BSD-3-Clause.
package io.heapy.kotdoc.highlighting

internal object SyntaxUtf8 {
    class Decoded(val chunks: List<String>, val error: SyntaxUtf8Exception?)
    /** Preserve strict chunk forcing and the lazy final incomplete-codepoint error. */
    fun decodeFile(bytes: ByteArray): Decoded {
        val dropBom = bytes.size >= 3 && bytes[0] == 0xef.toByte() && bytes[1] == 0xbb.toByte() && bytes[2] == 0xbf.toByte()
        val chunks = mutableListOf<String>(); var carry = byteArrayOf()
        // ByteString.Lazy.readFile's defaultChunkSize on the original 64-bit platforms.
        for (start in bytes.indices step 32752) {
            val from = start + if (start == 0 && dropBom) 3 else 0
            val chunk = bytes.copyOfRange(from, minOf(start + 32752, bytes.size)).filter { it != 13.toByte() }.toByteArray()
            if (chunk.isEmpty()) continue
            val previous = carry
            val input = previous + chunk; var i = 0
            while (i < input.size) {
                val lead = input[i].toInt() and 255
                if (lead < 0x80) { i++; continue }
                val length = when (lead) { in 0xc2..0xdf -> 2; in 0xe0..0xef -> 3; in 0xf0..0xf4 -> 4
                    else -> return Decoded(chunks, SyntaxUtf8Exception(lead)) }
                var incomplete = false
                for (k in 1..<length) {
                    if (i + k == input.size) { incomplete = true; break }
                    val byte = input[i + k].toInt() and 255
                    val range = when { k != 1 -> 0x80..0xbf; lead == 0xe0 -> 0xa0..0xbf; lead == 0xed -> 0x80..0x9f
                        lead == 0xf0 -> 0x90..0xbf; lead == 0xf4 -> 0x80..0x8f; else -> 0x80..0xbf }
                    if (byte !in range) return Decoded(chunks, SyntaxUtf8Exception(
                        if (i == 0 && previous.isNotEmpty()) previous.last().toInt() and 255 else lead))
                }
                if (incomplete) break
                i += length
            }
            if (i != 0) chunks.add(input.copyOfRange(0, i).toString(Charsets.UTF_8))
            carry = input.copyOfRange(i, input.size)
        }
        // skipIncomplete's strict Builder composition observes the final carried byte first.
        return Decoded(chunks, carry.lastOrNull()?.let { SyntaxUtf8Exception(it.toInt() and 255) })
    }
}
