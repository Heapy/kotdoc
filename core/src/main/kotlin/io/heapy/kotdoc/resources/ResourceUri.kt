// Port of Text.Pandoc.URI and URI helpers used by PandocMonad/MediaBag.
// Copyright (C) 2006-2024 John MacFarlane. GPL-2.0-or-later; see licenses/.
package io.heapy.kotdoc.resources

import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

object ResourceUri {
    private val schemes = checkNotNull(javaClass.getResource("/io/heapy/kotdoc/uri-schemes.txt")).readText().lines().toSet()
    private val restrictedName = Regex("[A-Za-z0-9][A-Za-z0-9!#$&^_.+-]*")

    fun isUri(value: String): Boolean = base64Data(value) != null ||
        (parse(escape(value) { it.code < 128 })?.scheme?.lowercase(Locale.ROOT) in schemes)

    internal fun parse(value: String): URI? = try { URI(value) } catch (_: URISyntaxException) { null }
    internal fun escaped(value: String): String = escape(value.replace('\\', '/')) {
        it.isAsciiLetterOrDigit() || it in "-._~:/?#[]@!$&'()*+,;=%"
    }
    fun urlEncode(value: String): String = escape(value) { it.isAsciiLetterOrDigit() || it in "-._~" }

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

    /** pBase64DataURI, including percent octets, case-insensitive scheme and MIME parameters. */
    fun base64Data(value: String): Data? {
        if (!value.startsWith("data:", ignoreCase = true)) return null
        val delimiter = value.indexOf(";base64,", 5)
        if (delimiter < 0) return null
        val parts = value.substring(5, delimiter).split(';')
        val type = parts[0].split('/')
        if (type.size != 2 || type.any { !restrictedName.matches(it) }) return null
        val mime = StringBuilder(parts[0])
        for (param in parts.drop(1)) {
            val stripped = param.trimStart { it.isWhitespace() || it == '\u00a0' }
            val key = stripped.substringBefore('=')
            if ('=' !in stripped || !restrictedName.matches(key)) return null
            mime.append(';').append(stripped)
        }
        val encoded = value.substring(delimiter + 8)
        if (!Regex("(?:[A-Za-z0-9/+ \\t\\r\\n]|%[A-Fa-f0-9]{2})*=*").matches(encoded)) return null
        return Data(decodeBase64Lenient(unescapeBytes(encoded)), mime.toString())
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

    internal fun decodeBase64Lenient(bytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (byte in bytes) {
            // The strict ByteString decoder stops at padding; the FB2 reader
            // uses the distinct lazy ByteString decoder, which skips padding.
            val c = byte.toInt() and 255
            if (c == 61) break
            val digit = when (c) {
                in 65..90 -> c - 65; in 97..122 -> c - 97 + 26; in 48..57 -> c - 48 + 52
                43 -> 62; 47 -> 63; else -> continue
            }
            buffer = (buffer shl 6) or digit
            bits += 6
            if (bits >= 8) { bits -= 8; output.write((buffer ushr bits) and 255) }
        }
        return output.toByteArray()
    }
}
