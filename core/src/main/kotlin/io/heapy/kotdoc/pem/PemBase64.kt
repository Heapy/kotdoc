// base64 1.0 standard strict ByteString path, Emily Pillmore 2019-2023.
// Full notice: licenses/base64-BSD-3-Clause.txt.
package io.heapy.kotdoc.pem

import java.io.ByteArrayOutputStream

/** Standard padded Base64 dependency of crypton-pem, including error priority. */
object PemBase64 {
    sealed interface DecodeResult
    class Decoded(val bytes: ByteArray) : DecodeResult
    data class Invalid(val message: String) : DecodeResult
    private const val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private val table = IntArray(256) { if (it == 61) 99 else alphabet.indexOf(it.toChar()).let { n -> if (n < 0) 255 else n } }
    fun encode(bytes: ByteArray): ByteArray = ByteArrayOutputStream().apply {
        var i = 0
        while (i < bytes.size) {
            val a = bytes[i++].toInt() and 255; val b = if (i < bytes.size) bytes[i++].toInt() and 255 else -1
            val c = if (i < bytes.size) bytes[i++].toInt() and 255 else -1
            write(alphabet[a shr 2].code); write(alphabet[((a and 3) shl 4) or (if (b < 0) 0 else b shr 4)].code)
            write(if (b < 0) 61 else alphabet[((b and 15) shl 2) or (if (c < 0) 0 else c shr 6)].code)
            write(if (c < 0) 61 else alphabet[c and 63].code)
        }
    }.toByteArray()
    fun decode(bytes: ByteArray): DecodeResult {
        if (bytes.isEmpty()) return Decoded(byteArrayOf())
        when (bytes.size % 4) { 1 -> return Invalid("Base64-encoded bytestring has invalid size"); 2, 3 -> return Invalid("Base64-encoded bytestring requires padding") }
        val out = ByteArrayOutputStream()
        fun error(kind: String, index: Int) = Invalid("$kind$index")
        var i = 0
        while (i < bytes.size) {
            val values = IntArray(4) { table[bytes[i + it].toInt() and 255] }
            val (a, b, c, d) = values; val last = i + 4 == bytes.size
            if (last) {
                if (a == 99) return error("invalid padding near offset: ", i)
                if (b == 99) return error("invalid padding near offset: ", i + 1)
                if (c == 99 && d != 99) return error("invalid base64 encoding near offset: ", i + 3)
            } else {
                val pad = values.indexOf(99)
                if (pad >= 0) return error("invalid padding near offset: ", i + pad)
            }
            val invalid = values.indexOf(255)
            if (invalid >= 0) return error("invalid base64 encoding near offset: ", i + invalid)
            val word = (a shl 18) or (b shl 12) or (c shl 6) or d
            out.write(word shr 16)
            if (last && c == 99 && d == 99) {
                if (b and 15 != 0) return error("non-canonical encoding detected at offset: ", i + 1)
                return Decoded(out.toByteArray())
            }
            if (last && d == 99) {
                if (c and 3 != 0) return error("non-canonical encoding detected at offset: ", i + 2)
                out.write(word shr 8); return Decoded(out.toByteArray())
            }
            out.write(word shr 8); out.write(word); i += 4
        }
        return Decoded(out.toByteArray())
    }
    fun isValid(bytes: ByteArray): Boolean = bytes.indices.all { i -> val b = bytes[i].toInt() and 255; if (b == 61) i >= bytes.size - 2 else table[b] < 64 }
    fun isBase64(bytes: ByteArray) = isValid(bytes) && decode(bytes) is Decoded
}
