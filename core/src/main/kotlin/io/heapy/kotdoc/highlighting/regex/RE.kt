// Skylighting.Regex 0.15, Copyright (c) 2016-2024 John MacFarlane.
// BSD-3-Clause; licenses/Skylighting-core-BSD-3-Clause.txt.
package io.heapy.kotdoc.highlighting.regex

import io.heapy.kotdoc.formats.HaskellString
import io.heapy.kotdoc.highlighting.HighlightJsonException
import kotlinx.serialization.json.*
import java.util.Base64

/** Original RE smart constructor: metadata never forces the cached compilation. */
class RE(bytes: ByteArray, val caseSensitive: Boolean, val minimal: Boolean) : Comparable<RE> {
    private val content = bytes.copyOf()
    val bytes: ByteArray get() = content.copyOf()
    private val cached: CompiledRE by lazy {
        when (val result = KdeCompiler.compile(caseSensitive, minimal, content)) {
            is CompiledKdeRegex.Failure -> CompiledRE.Failure(result.error)
            is CompiledKdeRegex.Success -> CompiledRE.Success(result.regex)
        }
    }
    fun compile(): CompiledRE = cached
    override fun compareTo(other: RE): Int {
        for (i in 0..<minOf(content.size, other.content.size)) {
            val cmp = (content[i].toInt() and 255).compareTo(other.content[i].toInt() and 255)
            if (cmp != 0) return cmp
        }
        return content.size.compareTo(other.content.size).takeIf { it != 0 }
            ?: caseSensitive.compareTo(other.caseSensitive).takeIf { it != 0 }
            ?: minimal.compareTo(other.minimal)
    }
    override fun equals(other: Any?) = other is RE && compareTo(other) == 0
    override fun hashCode() = 31 * (31 * content.contentHashCode() + caseSensitive.hashCode()) + minimal.hashCode()
    fun haskellShow(precedence: Int = 0): String {
        val text = HaskellString.quote(buildString { content.forEach { append((it.toInt() and 255).toChar()) } })
        val value = "RE {reString = $text, reCaseSensitive = ${if (caseSensitive) "True" else "False"}, reMinimal = ${if (minimal) "True" else "False"}}"
        return if (precedence > 10) "($value)" else value
    }
    override fun toString() = haskellShow()
    fun toJson(): JsonObject = buildJsonObject {
        put("reString", Base64.getEncoder().encodeToString(content))
        put("reCaseSensitive", caseSensitive)
        put("reMinimal", minimal)
    }
    companion object {
        fun fromJson(value: JsonElement): RE {
            fun kind(v: JsonElement) = when (v) {
                JsonNull -> "Null"; is JsonObject -> "Object"; is JsonArray -> "Array"
                is JsonPrimitive -> if (v.isString) "String" else if (v.booleanOrNull != null) "Boolean" else "Number"
            }
            fun fail(reason: String): Nothing = throw HighlightJsonException(reason)
            val obj = value as? JsonObject ?: fail("parsing RE failed, expected Object, but encountered ${kind(value)}")
            fun required(key: String) = obj[key] ?: fail("key \"$key\" not found")
            fun boolean(key: String, v: JsonElement): Boolean =
                if (v is JsonPrimitive && !v.isString && v.booleanOrNull != null) v.boolean
                else throw HighlightJsonException("expected Bool, but encountered ${kind(v)}", listOf(key))
            val str = required("reString")
            if (str !is JsonPrimitive || !str.isString) throw HighlightJsonException("parsing Text failed, expected String, but encountered ${kind(str)}", listOf("reString"))
            val bytes = StrictBase64.decode(str.content)
            val sensitive = boolean("reCaseSensitive", required("reCaseSensitive"))
            val minimal = obj["reMinimal"]?.takeUnless { it == JsonNull }?.let { boolean("reMinimal", it) } ?: false
            return RE(bytes, sensitive, minimal)
        }
        fun readHaskell(text: String): RE? = RERead.read(text)
    }
}

sealed interface CompiledRE {
    data class Failure(val error: String) : CompiledRE
    class Success(val regex: KdeRegex) : CompiledRE {
        val groups: Map<Long, KdeRegex> by lazy { KdeMatcher.extractCapturingGroups(regex) }
    }
}

// Data.ByteString.Base64.Internal 1.2.1.0, Copyright (c) 2010 Bryan O'Sullivan.
// BSD-3-Clause; licenses/Base64-bytestring-BSD-3-Clause.txt.
/** base64-bytestring's strict decoder, including padding-bit validation. */
internal object StrictBase64 {
    fun decode(text: String): ByteArray {
        val bytes = text.toByteArray(Charsets.UTF_8)
        fun fail(reason: String): Nothing = throw HighlightJsonException(reason)
        if (bytes.size % 4 == 1) fail("Base64-encoded bytestring has invalid size")
        if (bytes.size % 4 != 0) fail("Base64-encoded bytestring is unpadded or has invalid padding")
        val output = java.io.ByteArrayOutputStream()
        fun digit(i: Int): Int = when (val c = bytes[i].toInt() and 255) {
            in 65..90 -> c - 65; in 97..122 -> c - 97 + 26; in 48..57 -> c - 48 + 52
            43 -> 62; 47 -> 63
            61 -> 99
            else -> 255
        }
        for (i in bytes.indices step 4) {
            val a = digit(i); val b = digit(i + 1); val c = digit(i + 2); val d = digit(i + 3)
            val digits = intArrayOf(a, b, c, d)
            // Upstream checks padding before invalid characters, including when
            // an earlier byte is invalid. Final-chunk incoherence reports d.
            val padding = (if (i + 4 == bytes.size) 0..1 else 0..3).firstOrNull { digits[it] == 99 }
            if (padding != null) fail("invalid padding at offset: ${i + padding}")
            if (i + 4 == bytes.size && c == 99 && d != 99) fail("invalid character at offset: ${i + 3}")
            val invalid = digits.indices.firstOrNull { digits[it] == 255 }
            if (invalid != null) fail("invalid character at offset: ${i + invalid}")
            if (c == 99) {
                if (b and 15 != 0) fail("non-canonical encoding detected at offset: ${i + 1}")
                output.write(a shl 2 or (b shr 4))
            } else {
                if (d == 99) {
                    if (c and 3 != 0) fail("non-canonical encoding detected at offset: ${i + 2}")
                    output.write(a shl 2 or (b shr 4)); output.write(b shl 4 or (c shr 2))
                } else {
                    output.write(a shl 2 or (b shr 4)); output.write(b shl 4 or (c shr 2)); output.write(c shl 6 or d)
                }
            }
        }
        return output.toByteArray()
    }
}
