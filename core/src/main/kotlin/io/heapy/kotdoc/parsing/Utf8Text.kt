// text-2.1.3 Data.Text.Internal.Encoding.decodeUtf8With1/2 and skipIncomplete.
// BSD-2-Clause; licenses/text-BSD-2-Clause.txt.
package io.heapy.kotdoc.parsing

object Utf8Text {
    /** text's lenient handler replaces each invalid byte, including incomplete prefixes. */
    fun decodeLenient(bytes: ByteArray): String = buildString {
        var at = 0
        while (at < bytes.size) {
            val lead = bytes[at].toInt() and 255
            if (lead < 128) { appendCodePoint(lead); at++; continue }
            val n = when (lead) { in 0xc2..0xdf -> 2; in 0xe0..0xef -> 3; in 0xf0..0xf4 -> 4; else -> 0 }
            var point = lead and (if (n == 2) 31 else if (n == 3) 15 else 7)
            var valid = n != 0 && n <= bytes.size - at
            if (valid) for (i in 1..<n) {
                val byte = bytes[at + i].toInt() and 255
                if (byte !in 0x80..0xbf || i == 1 && when (lead) {
                    0xe0 -> byte < 0xa0; 0xed -> byte > 0x9f; 0xf0 -> byte < 0x90; 0xf4 -> byte > 0x8f; else -> false
                }) valid = false
                point = (point shl 6) or (byte and 63)
            }
            if (valid) { appendCodePoint(point); at += n } else { appendCodePoint(0xfffd); at++ }
        }
    }
}
