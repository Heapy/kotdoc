// yaml 0.11.11.2 Data.Yaml.Internal; © 2008 Michael Snoyman, BSD-2-Clause.
// Text.Libyaml types, libyaml 0.1.4; complete original notices in licenses/.
package io.heapy.kotdoc.yaml

import io.heapy.kotdoc.json.PrettyValue
import io.heapy.kotdoc.json.ScientificNumber
import io.heapy.kotdoc.layout.CharacterString
import io.heapy.kotdoc.layout.CharacterStringChars
import io.heapy.kotdoc.layout.TextChars
import io.heapy.kotdoc.parsing.Utf8Text
import java.math.BigInteger
import java.util.Collections

enum class YamlStyle { Any, Plain, SingleQuoted, DoubleQuoted, Literal, Folded, PlainNoTag }

sealed interface YamlTag {
    data object StrTag : YamlTag
    data object FloatTag : YamlTag
    data object NullTag : YamlTag
    data object BoolTag : YamlTag
    data object SetTag : YamlTag
    data object IntTag : YamlTag
    data object SeqTag : YamlTag
    data object MapTag : YamlTag
    data class UriTag(val value: CharacterString) : YamlTag
    data object NoTag : YamlTag
}

/** Owned scalar bytes; anchors and URI tags retain raw source String characters. */
class YamlScalarEvent(
    contents: ByteArray,
    val tag: YamlTag,
    val style: YamlStyle,
    val anchor: CharacterString?,
) {
    private val bytes = contents.copyOf()
    val contents: ByteArray get() = bytes.copyOf()
}

sealed interface YamlScientificResult {
    data class Success(val value: ScientificNumber) : YamlScientificResult
    data class Failure(val error: String) : YamlScientificResult
}

/** Original YAML scalar schema, numeric parser and encoder string-style selection. */
object YamlScalars {
    val specialStrings: Set<String> = Collections.unmodifiableSet(setOf(
        "y", "Y", "yes", "Yes", "YES", "n", "N", "no", "No", "NO", "true", "True", "TRUE",
        "false", "False", "FALSE", "on", "On", "ON", "off", "Off", "OFF", "null", "Null", "NULL", "~", "*",
    ))
    private val decimal = Regex("[+-]?[0-9]+(?:\\.[0-9]*)?(?:[eE][+-]?[0-9]+)?")
    private val hexadecimal = Regex("0x[0-9a-fA-F]+")
    private val octal = Regex("0o[0-7]+")

    /** Attoparsec's choice/backtracking and signed Int64 exponent accumulation. */
    fun textToScientific(input: String): YamlScientificResult {
        val text = TextChars.fromString(input)
        if (hexadecimal.matches(text)) return YamlScientificResult.Success(ScientificNumber(BigInteger(text.substring(2), 16), 0))
        if (octal.matches(text)) return YamlScientificResult.Success(ScientificNumber(BigInteger(text.substring(2), 8), 0))
        if (!decimal.matches(text)) {
            val start = if (text.startsWith('+') || text.startsWith('-')) 1 else 0
            val error = if (start == text.length) "not enough input"
                else if (text[start] !in '0'..'9') "Failed reading: takeWhile1" else "endOfInput"
            return YamlScientificResult.Failure(error)
        }
        val split = text.indexOfFirst { it == 'e' || it == 'E' }
        val mantissa = if (split < 0) text else text.substring(0, split)
        val dot = mantissa.indexOf('.')
        val fractional = if (dot < 0) 0 else mantissa.length - dot - 1
        val coefficient = BigInteger(mantissa.replace(".", ""))
        // Source decimal :: Int wraps on every step, equivalent to its low 64 bits.
        val exponent = if (split < 0) 0L else BigInteger(text.substring(split + 1)).toLong()
        return YamlScientificResult.Success(ScientificNumber(coefficient, exponent - fractional))
    }
    fun isNumeric(text: String): Boolean = textToScientific(text) is YamlScientificResult.Success
    fun isSpecialString(text: String): Boolean = TextChars.fromString(text) in specialStrings || isNumeric(text)
    fun defaultStringStyle(input: String): Pair<YamlTag, YamlStyle> {
        val text = TextChars.fromString(input)
        return YamlTag.NoTag to when {
            '\n' in text -> YamlStyle.Literal
            isSpecialString(text) -> YamlStyle.SingleQuoted
            else -> YamlStyle.PlainNoTag
        }
    }
    fun stringScalar(
        input: String,
        anchor: String? = null,
        stringStyle: (String) -> Pair<YamlTag, YamlStyle> = ::defaultStringStyle,
    ): YamlScalarEvent {
        val text = TextChars.fromString(input)
        val rawAnchor = anchor?.let { CharacterStringChars.fromString(TextChars.fromString(it)) }
        if (text.isEmpty()) return YamlScalarEvent(byteArrayOf(), YamlTag.NoTag, YamlStyle.SingleQuoted, rawAnchor)
        val (tag, style) = stringStyle(text)
        return YamlScalarEvent(text.toByteArray(Charsets.UTF_8), tag, style, rawAnchor)
    }
    fun resolve(event: YamlScalarEvent): PrettyValue {
        val text = Utf8Text.decodeLenient(event.contents)
        if (event.style == YamlStyle.SingleQuoted || event.style == YamlStyle.DoubleQuoted ||
            event.tag == YamlTag.StrTag || event.style == YamlStyle.Folded) return PrettyValue.Text(text)
        if (text in setOf("null", "Null", "NULL", "~", "")) return PrettyValue.Null
        if (text in setOf("y", "Y", "yes", "Yes", "YES", "on", "On", "ON", "true", "True", "TRUE")) return PrettyValue.Bool(true)
        if (text in setOf("n", "N", "no", "No", "NO", "off", "Off", "OFF", "false", "False", "FALSE")) return PrettyValue.Bool(false)
        return when (val result = textToScientific(text)) {
            is YamlScientificResult.Success -> PrettyValue.Number(result.value)
            is YamlScientificResult.Failure -> PrettyValue.Text(text)
        }
    }
}
