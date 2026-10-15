// Selected GHC 9.12.2 IO.Encoding/UTF8/UTF16/UTF32/Latin1/Failure/Iconv behavior.
// Full University of Glasgow/base notices: licenses/GHC-base-BSD-3-Clause.txt.
package io.heapy.kotdoc.resources

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import io.heapy.kotdoc.parsing.RegexCharacters
import java.io.IOException

/** A filesystem decoder with GHC's RoundtripFailure recovery, not replacement.
 * Stateful iconv descriptors are created afresh for each C string. */
internal class GhcEnvironmentEncoding(private val name: String) {
    private val normalized = buildString { name.codePoints().forEach { if (it != 45) appendCodePoint(RegexCharacters.toUpper(it)) } }
    private val builtin = when {
        normalized in listOf("UTF8", "UTF16", "UTF16LE", "UTF16BE", "UTF32", "UTF32LE", "UTF32BE") -> normalized
        name in asciiNames -> "ASCII"
        name in latin1Names -> "LATIN1"
        else -> null
    }
    init {
        if (name.contains("//")) throw IOException("mkTextEncoding: $name//ROUNDTRIP")
        if (builtin == null) {
            // Original mkIconvEncoding validates an encode/C-string/decode
            // roundtrip for 'a', not just whether iconv_open accepts the name.
            val descriptor = openIconv("UTF-32LE", name)
            val encoded = try {
                Memory(4).use { input ->
                    input.clear(); input.setByte(0, 97)
                    var capacity = 2L // withCString starts at cCharSize * (length "a" + 1).
                    var answer: ByteArray? = null
                    while (answer == null) {
                        Memory(capacity).use { output ->
                            Memory(Native.POINTER_SIZE.toLong()).use { inLeft -> Memory(Native.POINTER_SIZE.toLong()).use { outLeft ->
                                inLeft.setNativeLong(0, com.sun.jna.NativeLong(4))
                                outLeft.setNativeLong(0, com.sun.jna.NativeLong(capacity))
                                val r = iconvFunction.invokeLong(arrayOf(descriptor, PointerByReference(input), inLeft, PointerByReference(output), outLeft))
                                val error = Native.getLastError()
                                val remaining = inLeft.getNativeLong(0).toLong()
                                val free = outLeft.getNativeLong(0).toLong()
                                if (r == -1L && error !in listOf(7, 22, if (Platform.isMac()) 92 else 84)) throw IOException("mkTextEncoding: $name")
                                if (remaining == 0L && free > 0) {
                                    val data = output.getByteArray(0, (capacity - free).toInt())
                                    answer = data.copyOf(data.indexOf(0).let { if (it < 0) data.size else it })
                                } else if (remaining == 0L || r == -1L && (error == 7 || error == (if (Platform.isMac()) 92 else 84) && free == 0L)) {
                                    // The original iconv encoder's setState is a no-op;
                                    // retries retain its state while restarting input.
                                    capacity *= 2
                                } else throw IOException("mkTextEncoding: $name")
                            } }
                        }
                    }
                    checkNotNull(answer)
                }
            } finally { closeIconv(descriptor) }
            if (!iconv(encoded).contentEquals(intArrayOf(97))) throw IOException("mkTextEncoding: $name")
        }
    }

    /** Raw 32-bit GHC Char slots: iconv can produce values beyond Unicode. */
    fun decode(bytes: ByteArray): IntArray = if (builtin == null) iconv(bytes) else buildList<Int> {
        fun appendCodePoint(value: Int) { add(value) }
        fun b(i: Int) = bytes[i].toInt() and 255
        var at = 0
        var little = builtin.endsWith("LE")
        if (builtin == "UTF16" && bytes.size >= 2) {
            if (b(0) == 255 && b(1) == 254) { little = true; at = 2 }
            else if (b(0) == 254 && b(1) == 255) at = 2
        }
        if (builtin == "UTF32" && bytes.size >= 4) {
            if (b(0) == 255 && b(1) == 254 && b(2) == 0 && b(3) == 0) { little = true; at = 4 }
            else if (b(0) == 0 && b(1) == 0 && b(2) == 254 && b(3) == 255) at = 4
        }
        fun word16(i: Int) = if (little) b(i) + (b(i + 1) shl 8) else (b(i) shl 8) + b(i + 1)
        while (at < bytes.size) {
            val lead = b(at)
            var point = -1
            var consumed = 1
            when (builtin) {
                "ASCII" -> if (lead < 128) point = lead
                "LATIN1" -> point = lead
                "UTF8" -> {
                    if (lead < 128) point = lead else {
                        val n = when (lead) { in 0xc2..0xdf -> 2; in 0xe0..0xef -> 3; in 0xf0..0xf4 -> 4; else -> 0 }
                        var valid = n > 0 && bytes.size - at >= n
                        var cp = lead and when (n) { 2 -> 31; 3 -> 15; else -> 7 }
                        if (valid) for (i in 1..<n) {
                            val c = b(at + i)
                            if (c !in 0x80..0xbf || i == 1 && when (lead) {
                                0xe0 -> c < 0xa0; 0xed -> c > 0x9f; 0xf0 -> c < 0x90; 0xf4 -> c > 0x8f; else -> false
                            }) valid = false
                            cp = (cp shl 6) or (c and 63)
                        }
                        if (valid) { point = cp; consumed = n }
                    }
                }
                "UTF16", "UTF16LE", "UTF16BE" -> if (bytes.size - at >= 2) {
                    val first = word16(at)
                    if (first !in 0xd800..0xdfff) { point = first; consumed = 2 }
                    else if (bytes.size - at >= 4) {
                        val second = word16(at + 2)
                        if (first in 0xd800..0xdbff && second in 0xdc00..0xdfff) {
                            point = 0x10000 + ((first - 0xd800) shl 10) + second - 0xdc00; consumed = 4
                        }
                    }
                }
                "UTF32", "UTF32LE", "UTF32BE" -> if (bytes.size - at >= 4) {
                    var cp = 0L
                    for (i in 0..3) cp = cp or (b(at + i).toLong() shl (if (little) i * 8 else (3 - i) * 8))
                    if (cp in 0..0x10ffff && cp !in 0xd800..0xdfff) { point = cp.toInt(); consumed = 4 }
                }
            }
            if (point < 0) { point = if (lead < 128) lead else 0xdc00 + lead; consumed = 1 }
            appendCodePoint(point); at += consumed
        }
    }.toIntArray()

    private fun iconv(bytes: ByteArray): IntArray {
        val suffix = name.indexOf('/').let { if (it < 0) "" else name.substring(it) }
        val descriptor = openIconv(name.substringBefore('/'), "UTF-32LE$suffix")
        try {
            return Memory(maxOf(1, bytes.size).toLong()).use { input ->
                if (bytes.isNotEmpty()) input.write(0, bytes, 0, bytes.size)
                val capacity = maxOf(1, bytes.size).toLong() * 4
                Memory(capacity).use { output ->
                    var at = 0
                    buildList<Int> {
                        fun appendCodePoint(value: Int) { add(value) }
                        while (at < bytes.size) {
                            val incoming = PointerByReference(input.share(at.toLong()))
                            val outgoing = PointerByReference(output)
                            Memory(Native.POINTER_SIZE.toLong()).use { inLeft -> Memory(Native.POINTER_SIZE.toLong()).use { outLeft ->
                                inLeft.setNativeLong(0, com.sun.jna.NativeLong((bytes.size - at).toLong()))
                                outLeft.setNativeLong(0, com.sun.jna.NativeLong(capacity))
                                val result = iconvFunction.invokeLong(arrayOf(descriptor, incoming, inLeft, outgoing, outLeft))
                                val error = Native.getLastError()
                                val remaining = inLeft.getNativeLong(0).toLong()
                                val written = capacity - outLeft.getNativeLong(0).toLong()
                                var offset = 0L
                                while (offset < written) {
                                    var cp = 0
                                    for (i in 0..3) cp = cp or ((output.getByte(offset + i).toInt() and 255) shl (i * 8))
                                    appendCodePoint(cp); offset += 4
                                }
                                at = bytes.size - remaining.toInt()
                                if (result == -1L && error !in listOf(7, 22, if (Platform.isMac()) 92 else 84)) throw IOException("iconvRecoder: errno $error")
                                if (remaining > 0 && (result != -1L || error != 7 && written < capacity)) {
                                    val b = bytes[at].toInt() and 255
                                    appendCodePoint(if (b < 128) b else 0xdc00 + b); at++
                                }
                            } }
                        }
                    }.toIntArray()
                }
            }
        } finally { closeIconv(descriptor) }
    }

    companion object {
        private val asciiNames = listOf("ANSI_X3.4-1968", "iso-ir-6", "ANSI_X3.4-1986", "ISO_646.irv:1991", "US-ASCII", "us", "IBM367", "cp367", "csASCII", "ASCII", "ISO646-US")
        private val latin1Names = listOf("ISO_8859-1:1987", "iso-ir-100", "ISO_8859-1", "ISO-8859-1", "latin1", "l1", "IBM819", "CP819", "csISOLatin1")
        private val library by lazy { NativeLibrary.getInstance(if (Platform.isMac()) "iconv" else Platform.C_LIBRARY_NAME) }
        private fun function(name: String) = try { library.getFunction(name) } catch (_: UnsatisfiedLinkError) { library.getFunction("lib$name") }
        private val iconvFunction by lazy { function("iconv") }
        private fun openIconv(from: String, to: String): Pointer {
            val p = function("iconv_open").invokePointer(arrayOf(to, from))
            if (Pointer.nativeValue(p) == -1L) throw IOException("mkTextEncoding: $from")
            return p
        }
        private fun closeIconv(p: Pointer) {
            if (function("iconv_close").invokeInt(arrayOf(p)) == -1) throw IOException("Iconv.close: errno ${Native.getLastError()}")
        }
    }
}
