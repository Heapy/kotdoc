// Port of Text.Pandoc.URI and URI helpers used by PandocMonad/MediaBag.
// Copyright (C) 2006-2024 John MacFarlane. GPL-2.0-or-later; see licenses/.
package io.heapy.kotdoc.resources

import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale
import io.heapy.kotdoc.uri.PandocURI
import io.heapy.kotdoc.parsing.attoparsec.AttoResult
import io.heapy.kotdoc.parsing.attoparsec.TextParser

object ResourceUri {
    /** Text.Pandoc.SelfContained.makeDataURI, including UTF8.toString normalization. */
    fun makeDataUri(mime: String, contents: ByteArray): String {
        if (mime.startsWith("text/")) {
            val start = if (contents.size >= 3 && contents[0] == 0xef.toByte() &&
                contents[1] == 0xbb.toByte() && contents[2] == 0xbf.toByte()) 3 else 0
            val normalized = contents.copyOfRange(start, contents.size).filter { it != 13.toByte() }.toByteArray()
            val text = Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(normalized)).toString()
            val type = if (';' in mime) mime else "$mime;charset=utf-8"
            return "data:$type,${urlEncode(text)}"
        }
        val bytes = if (mime.endsWith("+xml")) contents.filter { it != 13.toByte() }.toByteArray() else contents
        return "data:$mime;base64,${java.util.Base64.getEncoder().encodeToString(bytes)}"
    }
    fun isUri(value: String): Boolean = PandocURI.isURI(value)

    internal fun parse(value: String): URI? = try { URI(value) } catch (_: URISyntaxException) { null }
    internal fun escaped(value: String): String = escape(value.replace('\\', '/')) {
        it.isAsciiLetterOrDigit() || it in "-._~:/?#[]@!$&'()*+,;=%"
    }
    fun urlEncode(value: String): String = PandocURI.urlEncode(value)

    private fun Char.isAsciiLetterOrDigit() = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'
    private fun escape(value: String, allowed: (Char) -> Boolean): String = buildString {
        var i = 0
        while (i < value.length) {
            val cp = value.codePointAt(i)
            val chars = String(Character.toChars(cp))
            if (cp < 128 && allowed(cp.toChar())) append(chars)
            else for (byte in chars.toByteArray(Charsets.UTF_8)) append('%').append((byte.toInt() and 255).toString(16).uppercase(Locale.ROOT).padStart(2, '0'))
            i += Character.charCount(cp)
        }
    }

    fun unescape(value: String): String = buildString {
        var index = 0
        while (index < value.length) {
            if (value[index] != '%' || index + 2 >= value.length || value.substring(index + 1, index + 3).toIntOrNull(16) == null) {
                append(value[index++]); continue
            }
            val bytes = ByteArrayOutputStream()
            while (index + 2 < value.length && value[index] == '%') {
                val n = value.substring(index + 1, index + 3).toIntOrNull(16) ?: break
                bytes.write(n); index += 3
            }
            append(bytes.toByteArray().toString(Charsets.UTF_8))
        }
    }

    data class Data(val contents: ByteArray, val mime: String)

    /** downloadOrRead requires the original parser followed by endOfInput. */
    fun base64Data(value: String): Data? {
        val result = PandocURI.pBase64DataURI.before(TextParser.endOfInput()).parseComplete(value)
        if (result !is AttoResult.Done) return null
        return Data(result.value.contents.value(), result.value.mime.value())
    }

    internal fun extractData(path: String): Data {
        val spec = unescape(path.substringBefore(',')).replace(" ", "").split(';')
        val base64 = spec.size > 1 && spec.last() == "base64"
        val mime = (if (base64) spec.dropLast(1) else spec).joinToString(";")
        val bytes = unescapeBytes(path.substringAfter(',', ""))
        return Data(if (base64) decodeBase64Lenient(bytes) else bytes, mime)
    }

    private fun unescapeBytes(value: String): ByteArray {
        val result = ByteArrayOutputStream()
        var i = 0
        while (i < value.length) {
            val n = if (value[i] == '%' && i + 2 < value.length) value.substring(i + 1, i + 3).toIntOrNull(16) else null
            if (n == null) result.write(value[i++].code and 255) else { result.write(n); i += 3 }
        }
        return result.toByteArray()
    }

    /** base64-bytestring 1.2.1.0 strict decoder: padding is skipped while
     * looking for either of the first two sextets, and terminates later ones. */
    internal fun decodeBase64Lenient(bytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        var position = 0
        fun next(skipPadding: Boolean): Int {
            while (position < bytes.size) {
                when (val c = bytes[position++].toInt() and 255) {
                    in 65..90 -> return c - 65
                    in 97..122 -> return c - 97 + 26
                    in 48..57 -> return c - 48 + 52
                    43 -> return 62
                    47 -> return 63
                    61 -> if (!skipPadding) return 64
                }
            }
            return 64
        }
        while (true) {
            val a = next(true)
            val b = next(true)
            if (a == 64 || b == 64) break
            val c = next(false)
            val d = next(false)
            output.write((a shl 2) or (b ushr 4))
            if (c == 64) break
            output.write(((b shl 4) or (c ushr 2)) and 255)
            if (d == 64) break
            output.write(((c shl 6) or d) and 255)
        }
        return output.toByteArray()
    }
}
