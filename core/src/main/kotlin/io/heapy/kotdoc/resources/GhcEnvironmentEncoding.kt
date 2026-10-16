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
import io.heapy.kotdoc.formats.HaskellString
import java.io.IOException
import io.heapy.kotdoc.errors.GhcPosixErrno
import io.heapy.kotdoc.errors.GhcIOException
import io.heapy.kotdoc.errors.GhcIoError
import io.heapy.kotdoc.errors.IoErrorType
import io.heapy.kotdoc.layout.CharacterStringChars as Chars

/** A filesystem decoder with GHC's RoundtripFailure recovery, not replacement.
 * Stateful iconv descriptors are created afresh for each C string. */
internal class GhcEnvironmentEncoding(
    private val name: String,
    ignoreFailures: Boolean = false,
    private val failureMode: GhcCodingFailureMode = if (ignoreFailures) GhcCodingFailureMode.Ignore else GhcCodingFailureMode.Roundtrip,
    private val uncheckedLatin1: Boolean = false,
    allowNativeSuffix: Boolean = false,
) {
    private val normalized = buildString { name.codePoints().forEach { if (it != 45) appendCodePoint(RegexCharacters.toUpper(it)) } }
    private val builtin = when {
        normalized in listOf("UTF8", "UTF16", "UTF16LE", "UTF16BE", "UTF32", "UTF32LE", "UTF32BE") -> normalized
        name in asciiNames -> "ASCII"
        name in latin1Names -> "LATIN1"
        else -> null
    }
    init {
        if (!allowNativeSuffix && name.contains("//")) unknownEncoding()
        if (builtin == null) {
            // The Windows code-page backend is a separate upstream module.
            // POSIX iconv must never be loaded on Windows.
            if (Platform.isWindows()) unknownEncoding()
            val supported = try {
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
            iconv(encoded).contentEquals(intArrayOf(97))
            } catch (_: IOException) { false } // charIsRepresentable catches every IOException.
            if (!supported) unknownEncoding()
        }
    }

    private fun unknownEncoding(): Nothing = throw GhcIOException(GhcIoError(IoErrorType.NoSuchThing, Chars.fromString("mkTextEncoding"), Chars.fromString("unknown encoding:$name${failureMode.suffix}")))

    internal fun canonicalName(fallback: String): String = when (builtin) {
        "ASCII" -> "ASCII"
        "LATIN1" -> "ISO-8859-1"
        else -> fallback
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
            if (point < 0) { recoverDecode(lead)?.let(::appendCodePoint); consumed = 1 }
            else appendCodePoint(point)
            at += consumed
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
                                if (result == -1L && error !in listOf(7, 22, if (Platform.isMac()) 92 else 84)) throw GhcPosixErrno.exception("iconvRecoder", error)
                                if (remaining > 0 && (result != -1L || error != 7 && written < capacity)) {
                                    val b = bytes[at].toInt() and 255
                                    recoverDecode(b)?.let(::appendCodePoint); at++
                                }
                            } }
                        }
                    }.toIntArray()
                }
            }
        } finally { closeIconv(descriptor) }
    }

    /** GHC withEncodedCString/tryFillBuffer using IgnoreCodingFailure. Each
     * growth restarts the input while retaining the encoder state, including
     * an already emitted BOM or iconv shift state. The returned bytes exclude
     * the final C terminator, but the caller's reservation affects retries. */
    fun encodeIgnoringErrors(points: IntArray, nullTerminate: Boolean = true): ByteArray =
        encodeCString(points, nullTerminate, GhcCodingFailureMode.Ignore)

    fun encodeCString(original: IntArray, nullTerminate: Boolean = true, mode: GhcCodingFailureMode = failureMode): ByteArray {
        // recoverEncode TRANSLIT mutates the original C Char buffer. Keep those
        // mutations across retries, but never modify the caller's Kotlin array.
        val points = original.copyOf()
        val descriptor = if (builtin == null) openIconv("UTF-32LE", name) else null
        var doneBom = false
        try {
            if (points.isEmpty()) return byteArrayOf() // tryFillBuffer checks empty input before OutputUnderflow.
            var capacity = points.size.toLong() + 1
            while (true) {
                var retry = false
                val result = Memory(capacity).use { output ->
                    var at = 0; var used = 0L
                    if (builtin == "UTF16" || builtin == "UTF32") {
                        val bom = if (builtin == "UTF16") byteArrayOf(-2, -1) else byteArrayOf(0, 0, -2, -1)
                        if (!doneBom) {
                            if (capacity < bom.size) retry = true else {
                                output.write(0, bom, 0, bom.size); used = bom.size.toLong(); doneBom = true
                            }
                        }
                    }
                    if (descriptor == null) {
                        val unit = when { builtin!!.startsWith("UTF16") -> 2; builtin.startsWith("UTF32") -> 4; else -> 1 }
                        val little = builtin.endsWith("LE")
                        fun put(value: Int) { output.setByte(used++, value.toByte()) }
                        fun word16(n: Int) { if (little) {put(n); put(n ushr 8)} else {put(n ushr 8); put(n)} }
                        while (!retry && at < points.size) {
                            if (capacity - used < unit) { retry = true; break }
                            val cp = points[at]
                            if (!uncheckedLatin1 && (cp in 0xd800..0xdfff || builtin == "ASCII" && cp !in 0..127 || builtin == "LATIN1" && cp !in 0..255)) {
                                when (mode) {
                                    GhcCodingFailureMode.Ignore -> at++
                                    GhcCodingFailureMode.Transliterate -> if (cp == 63) at++ else points[at] = 63
                                    GhcCodingFailureMode.Roundtrip -> if (cp in 0xdc80..0xdcff) { put(cp); at++ } else encodingError(cp)
                                    GhcCodingFailureMode.Error -> encodingError(cp)
                                }
                                continue
                            }
                            val size = when {
                                builtin == "UTF8" -> if (cp < 128) 1 else if (cp < 2048) 2 else if (cp < 65536) 3 else 4
                                unit == 2 -> if (cp < 65536) 2 else 4
                                else -> unit
                            }
                            if (capacity - used < size) { retry = true; break }
                            when {
                                builtin == "UTF8" -> when (size) {
                                    1 -> put(cp)
                                    2 -> {put(0xc0 or (cp ushr 6)); put(0x80 or (cp and 63))}
                                    3 -> {put(0xe0 or (cp ushr 12)); put(0x80 or ((cp ushr 6) and 63)); put(0x80 or (cp and 63))}
                                    else -> {put(0xf0 or (cp ushr 18)); put(0x80 or ((cp ushr 12) and 63)); put(0x80 or ((cp ushr 6) and 63)); put(0x80 or (cp and 63))}
                                }
                                unit == 2 -> if (cp < 65536) word16(cp) else {word16(0xd800 + ((cp - 65536) ushr 10)); word16(0xdc00 + ((cp - 65536) and 1023))}
                                unit == 4 -> for (i in 0..3) put(cp ushr (if (little) 8 * i else 8 * (3 - i)))
                                else -> put(cp)
                            }
                            at++
                        }
                    } else {
                        Memory(maxOf(1, points.size).toLong() * 4).use { input ->
                            for (i in points.indices) for (j in 0..3) input.setByte(i.toLong() * 4 + j, (points[i] ushr (8*j)).toByte())
                            while (!retry && at < points.size) {
                                Memory(Native.POINTER_SIZE.toLong()).use { inLeft -> Memory(Native.POINTER_SIZE.toLong()).use { outLeft ->
                                    inLeft.setNativeLong(0, com.sun.jna.NativeLong((points.size - at).toLong() * 4))
                                    outLeft.setNativeLong(0, com.sun.jna.NativeLong(capacity - used))
                                    val incoming = PointerByReference(input.share(at.toLong() * 4))
                                    val outgoing = PointerByReference(output.share(used))
                                    val r = iconvFunction.invokeLong(arrayOf(descriptor, incoming, inLeft, outgoing, outLeft))
                                    val error = Native.getLastError(); val remaining = inLeft.getNativeLong(0).toLong(); val free = outLeft.getNativeLong(0).toLong()
                                    at = points.size - (remaining shr 2).toInt(); used = capacity - free
                                    if (r == -1L && error !in listOf(7, 22, if (Platform.isMac()) 92 else 84)) throw GhcPosixErrno.exception("iconvRecoder", error)
                                    if (at < points.size) {
                                        if (r == -1L && (error == 7 || error == (if (Platform.isMac()) 92 else 84) && free == 0L)) retry = true
                                        else when (mode) {
                                            GhcCodingFailureMode.Ignore -> at++
                                            GhcCodingFailureMode.Transliterate -> if (points[at] == 63) at++ else {
                                                points[at] = 63
                                                input.setInt(at.toLong() * 4, 63) // host is little endian on supported ABIs.
                                            }
                                            GhcCodingFailureMode.Roundtrip -> if (points[at] in 0xdc80..0xdcff) {
                                                output.setByte(used++, points[at].toByte()); at++
                                            } else encodingError(points[at])
                                            GhcCodingFailureMode.Error -> encodingError(points[at])
                                        }
                                    }
                                } }
                            }
                        }
                    }
                    if (retry || nullTerminate && used == capacity) null else output.getByteArray(0, used.toInt())
                }
                if (result != null) return result
                capacity = Math.multiplyExact(capacity, 2)
            }
        } finally { if (descriptor != null) closeIconv(descriptor) }
    }

    private fun recoverDecode(byte: Int): Int? = when (failureMode) {
        GhcCodingFailureMode.Ignore -> null
        GhcCodingFailureMode.Transliterate -> 0xfffd
        GhcCodingFailureMode.Roundtrip -> if (byte < 128) byte else 0xdc00 + byte
        GhcCodingFailureMode.Error -> throw GhcIOException(GhcIoError(IoErrorType.InvalidArgument,
            Chars.fromString("recoverDecode"), Chars.fromString("cannot decode byte sequence starting from $byte")))
    }

    private fun encodingError(point: Int): Nothing {
        val shown = when (point) {
            39 -> "'\\''"
            34 -> "'\"'"
            else -> "'" + HaskellString.quoteCodePoints(intArrayOf(point)).let { it.substring(1, it.length - 1) } + "'"
        }
        throw GhcIOException(GhcIoError(IoErrorType.InvalidArgument, Chars.fromString("recoverEncode"),
            Chars.fromString("cannot encode character $shown")))
    }

    companion object {
        private val asciiNames = listOf("ANSI_X3.4-1968", "iso-ir-6", "ANSI_X3.4-1986", "ISO_646.irv:1991", "US-ASCII", "us", "IBM367", "cp367", "csASCII", "ASCII", "ISO646-US")
        private val latin1Names = listOf("ISO_8859-1:1987", "iso-ir-100", "ISO_8859-1", "ISO-8859-1", "latin1", "l1", "IBM819", "CP819", "csISOLatin1")
        private val library by lazy { NativeLibrary.getInstance(if (Platform.isMac()) "iconv" else Platform.C_LIBRARY_NAME) }
        private fun function(name: String) = try { library.getFunction(name) } catch (_: UnsatisfiedLinkError) { library.getFunction("lib$name") }
        private val iconvFunction by lazy { function("iconv") }
        private fun openIconv(from: String, to: String): Pointer {
            val p = function("iconv_open").invokePointer(arrayOf(to, from))
            if (Pointer.nativeValue(p) == -1L) throw GhcPosixErrno.exception("mkTextEncoding", Native.getLastError())
            return p
        }
        private fun closeIconv(p: Pointer) {
            if (function("iconv_close").invokeInt(arrayOf(p)) == -1) throw GhcPosixErrno.exception("Iconv.close", Native.getLastError())
        }
    }
}
