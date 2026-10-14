// Data.ByteString.UTF8 1.0.2, Copyright (c) Iavor S. Diatchki 2009.
// BSD-3-Clause; licenses/Utf8-string-BSD-3-Clause.txt.
package io.heapy.kotdoc.parsing

object Utf8String {
    data class Decoded(val point: Int, val bytes: Int)
    fun decode(bytes: ByteArray, offset: Int = 0): Decoded? {
        if (offset >= bytes.size) return null
        val lead = bytes[offset].toInt() and 255
        if (lead < 0x80) return Decoded(lead, 1)
        if (lead < 0xc0 || lead >= 0xf8) return Decoded(0xfffd, 1)
        val size = if (lead < 0xe0) 2 else if (lead < 0xf0) 3 else 4
        var point = lead and (if (size == 2) 31 else if (size == 3) 15 else 7)
        var used = 1
        while (used < size) {
            val next = bytes.getOrNull(offset + used)?.toInt()?.and(255)
            if (next == null || next and 0xc0 != 0x80) return Decoded(0xfffd, used)
            point = (point shl 6) or (next and 63); used++
        }
        val valid = when (size) {
            2 -> point >= 0x80
            3 -> point in 0x800..<0xd800 || point in 0xe000..<0xfffe
            else -> point in 0x10000..<0x110000
        }
        return Decoded(if (valid) point else 0xfffd, if (!valid && size == 2) 1 else size)
    }
    fun toString(bytes: ByteArray): String = buildString {
        var at = 0
        while (at < bytes.size) { val d = checkNotNull(decode(bytes, at)); appendCodePoint(d.point); at += d.bytes }
    }
}
