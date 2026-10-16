// GHC 9.12.2 GHC.Internal.IO.Encoding and Encoding.Failure.
// University of Glasgow; full notice: licenses/GHC-base-BSD-3-Clause.txt.
package io.heapy.kotdoc.resources

import io.heapy.kotdoc.errors.GhcIOException
import io.heapy.kotdoc.errors.GhcIoError
import io.heapy.kotdoc.errors.IoErrorType
import io.heapy.kotdoc.parsing.RegexCharacters
import java.io.IOException
import io.heapy.kotdoc.layout.CharacterStringChars as Chars

enum class GhcCodingFailureMode(val suffix: String) {
    Error(""), Ignore("//IGNORE"), Transliterate("//TRANSLIT"), Roundtrip("//ROUNDTRIP"),
}

/** Configurable GHC C-string codecs. Each call opens fresh encoder/decoder
 * state; the suffix configures recovery and is not part of textEncodingName. */
class GhcTextEncoding private constructor(
    val textEncodingName: String,
    val failureMode: GhcCodingFailureMode,
    internal val codec: GhcEnvironmentEncoding,
) {
    fun decode(bytes: ByteArray): IntArray = codec.decode(bytes)
    /** Excludes the C terminator; its reservation still affects GHC retries. */
    fun encode(points: IntArray, nullTerminate: Boolean = true): ByteArray = codec.encodeCString(points, nullTerminate)

    fun charIsRepresentable(point: Int): Boolean = try {
        val bytes = encode(intArrayOf(point))
        val nul = bytes.indexOf(0)
        decode(if (nul < 0) bytes else bytes.copyOf(nul)).contentEquals(intArrayOf(point))
    } catch (_: IOException) { false }

    companion object {
        fun mkTextEncoding(name: String): GhcTextEncoding {
            val encoding = name.substringBefore('/')
            val suffix = name.substring(encoding.length)
            val mode = GhcCodingFailureMode.entries.firstOrNull { it.suffix == suffix }
                ?: throw GhcIOException(GhcIoError(IoErrorType.NoSuchThing, Chars.fromString("mkTextEncoding"),
                    Chars.fromString("unknown encoding:$name")))
            val normalized = buildString { encoding.codePoints().forEach { if (it != 45) appendCodePoint(RegexCharacters.toUpper(it)) } }
            val canonical = when (normalized) {
                "UTF8" -> "UTF-8"
                "UTF16" -> "UTF-16"
                "UTF16LE" -> "UTF16-LE" // Exact upstream textEncodingName spelling.
                "UTF16BE" -> "UTF-16BE"
                "UTF32" -> "UTF-32"
                "UTF32LE" -> "UTF-32LE"
                "UTF32BE" -> "UTF-32BE"
                else -> encoding
            }
            val codec = GhcEnvironmentEncoding(encoding, failureMode = mode)
            return GhcTextEncoding(codec.canonicalName(canonical), mode, codec)
        }

        /** Unlike checked ISO-8859-1, char8 truncates every Char modulo 256. */
        val char8 = GhcTextEncoding("ISO-8859-1", GhcCodingFailureMode.Error,
            GhcEnvironmentEncoding("ISO-8859-1", failureMode = GhcCodingFailureMode.Error, uncheckedLatin1 = true))
    }
}
